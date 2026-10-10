package com.pethome.rag;

import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.config.KnowledgeProperties;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.rag.dto.RagAskRequest;
import com.pethome.rag.dto.RagAskResponse;
import com.pethome.rag.dto.RagChatMessage;
import com.pethome.rag.dto.RagPetInfo;
import com.pethome.rag.dto.RagReindexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * RAG 客户端工具类测试（MockRestServiceServer，不依赖真实 RAG 服务）
 *
 * <p>覆盖：请求报文与请求头、正常解析、失败重试、重试耗尽、4xx 不重试、超时重试、重建索引。</p>
 *
 * @author pet-home
 */
class KnowledgeClientTest {

    private static final String BASE_URL = "http://localhost:8000";
    private static final String ASK_URL = BASE_URL + "/api/knowledge/ask";
    private static final String REINDEX_URL = BASE_URL + "/api/knowledge/reindex";
    private static final String API_KEY = "it-rag-api-key";

    private static final String ANSWER_JSON = "{\"answer\":\"建议每天给猫咪梳毛一次。\","
            + "\"references\":[{\"docId\":1,\"title\":\"猫咪护理\",\"content\":\"每周梳毛2-3次\"}]}";

    private MockRestServiceServer server;
    private KnowledgeClient client;
    private KnowledgeProperties properties;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        properties = new KnowledgeProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey(API_KEY);
        // 问答与重建索引共用同一个 RestTemplate 实例，便于统一 mock
        client = new KnowledgeClient(restTemplate, restTemplate, properties);
    }

    // ==================== 问答 ====================

    @Test
    void askShouldSendApiKeyAndParseResponse() {
        server.expect(requestTo(ASK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(properties.getApiKeyHeader(), API_KEY))
                .andExpect(jsonPath("$.question").value("猫咪掉毛怎么办"))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.enabledDocIds[0]").value(1))
                .andExpect(jsonPath("$.petInfo.breed").value("英短"))
                .andExpect(jsonPath("$.conversationHistory[0].role").value("user"))
                .andRespond(withSuccess(ANSWER_JSON, MediaType.APPLICATION_JSON));

        RagAskResponse response = client.ask(buildRequest());

        assertThat(response.getAnswer()).isEqualTo("建议每天给猫咪梳毛一次。");
        assertThat(response.getReferences()).hasSize(1);
        assertThat(response.getReferences().get(0).getDocId()).isEqualTo(1L);
        assertThat(response.getReferences().get(0).getTitle()).isEqualTo("猫咪护理");
        server.verify();
    }

    @Test
    void askShouldRetryOnceAndSucceedWhenServerError() {
        server.expect(requestTo(ASK_URL)).andRespond(withServerError());
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess(ANSWER_JSON, MediaType.APPLICATION_JSON));

        RagAskResponse response = client.ask(buildRequest());

        assertThat(response.getAnswer()).isNotBlank();
        // verify() 校验两次请求都按预期发出（即确实重试了一次）
        server.verify();
    }

    @Test
    void askShouldRetryOnTimeoutAndSucceed() {
        server.expect(requestTo(ASK_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess(ANSWER_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.ask(buildRequest()).getAnswer()).isNotBlank();
        server.verify();
    }

    @Test
    void askShouldThrowKnowledgeServiceExceptionWhenAllAttemptsFail() {
        server.expect(requestTo(ASK_URL)).andRespond(withServerError());
        server.expect(requestTo(ASK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.ask(buildRequest()))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessage(KnowledgeServiceException.MESSAGE);
        server.verify();
    }

    @Test
    void askShouldThrowWhenResponseBodyIsEmpty() {
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask(buildRequest()))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessage(KnowledgeServiceException.MESSAGE);
        server.verify();
    }

    @Test
    void askShouldNotRetryOnClientError() {
        // 401 属于 API Key 配置问题，重试无意义：只允许一次请求，若发生第二次会因缺少期望而失败
        server.expect(requestTo(ASK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"非法的 API Key\"}"));

        assertThatThrownBy(() -> client.ask(buildRequest()))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessage(KnowledgeServiceException.MESSAGE);
        server.verify();
    }

    @Test
    void askShouldRespectMaxRetriesConfig() {
        properties.setMaxRetries(0);
        server.expect(requestTo(ASK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.ask(buildRequest()))
                .isInstanceOf(KnowledgeServiceException.class);
        server.verify();
    }

    // ==================== 重建索引 ====================

    @Test
    void rebuildIndexShouldPostDocuments() {
        server.expect(requestTo(REINDEX_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(properties.getApiKeyHeader(), API_KEY))
                .andExpect(jsonPath("$.documents[0].id").value(11))
                .andExpect(jsonPath("$.documents[0].title").value("猫咪护理"))
                .andExpect(jsonPath("$.documents[0].content").value("每周梳毛2-3次。"))
                .andRespond(withSuccess("{\"documentCount\":1,\"chunkCount\":3}", MediaType.APPLICATION_JSON));

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(11L);
        doc.setTitle("猫咪护理");
        doc.setCategory("护理");
        doc.setContent("每周梳毛2-3次。");

        RagReindexResponse response = client.rebuildIndex(List.of(doc));

        assertThat(response.getDocumentCount()).isEqualTo(1);
        assertThat(response.getChunkCount()).isEqualTo(3);
        server.verify();
    }

    @Test
    void rebuildIndexShouldSendEmptyArrayWhenNoDocuments() {
        server.expect(requestTo(REINDEX_URL))
                .andExpect(jsonPath("$.documents").isArray())
                .andRespond(withSuccess("{\"documentCount\":0,\"chunkCount\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.rebuildIndex(null).getDocumentCount()).isZero();
        server.verify();
    }

    // ==================== 辅助方法 ====================

    private RagAskRequest buildRequest() {
        RagChatMessage message = new RagChatMessage();
        message.setRole("user");
        message.setContent("猫咪掉毛怎么办");

        RagPetInfo petInfo = new RagPetInfo();
        petInfo.setBreed("英短");
        petInfo.setAge(2);

        RagAskRequest request = new RagAskRequest();
        request.setQuestion("猫咪掉毛怎么办");
        request.setUserId(7L);
        request.setEnabledDocIds(List.of(1L, 2L));
        request.setConversationHistory(List.of(message));
        request.setPetInfo(petInfo);
        return request;
    }

}
