package com.pethome.rag;

import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.config.KnowledgeProperties;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.rag.dto.RagAskRequest;
import com.pethome.rag.dto.RagAskResponse;
import com.pethome.rag.dto.RagReindexDocument;
import com.pethome.rag.dto.RagReindexRequest;
import com.pethome.rag.dto.RagReindexResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 知识库（RAG）服务客户端
 *
 * <p>基于 {@link RestTemplate} 封装对 rag-service 的 HTTP 调用，统一处理：</p>
 * <ul>
 *     <li>请求头携带 X-API-Key</li>
 *     <li>超时（由 {@code KnowledgeClientConfig} 中两个 RestTemplate 分别配置）</li>
 *     <li>失败重试（简单循环实现，默认重试 1 次）</li>
 *     <li>最终失败统一抛 {@link KnowledgeServiceException}（bizCode = 600）</li>
 * </ul>
 *
 * @author pet-home
 */
@Slf4j
@Component
public class KnowledgeClient {

    /** 问答接口路径 */
    private static final String ASK_PATH = "/api/knowledge/ask";

    /** 重建索引接口路径 */
    private static final String REINDEX_PATH = "/api/knowledge/reindex";

    /** 日志中响应体最长截断长度 */
    private static final int LOG_BODY_MAX_LENGTH = 500;

    private final RestTemplate askRestTemplate;
    private final RestTemplate reindexRestTemplate;
    private final KnowledgeProperties properties;

    public KnowledgeClient(@Qualifier("knowledgeRestTemplate") RestTemplate askRestTemplate,
                           @Qualifier("knowledgeReindexRestTemplate") RestTemplate reindexRestTemplate,
                           KnowledgeProperties properties) {
        this.askRestTemplate = askRestTemplate;
        this.reindexRestTemplate = reindexRestTemplate;
        this.properties = properties;
    }

    /**
     * 调用 RAG 问答接口 POST /api/knowledge/ask
     *
     * @param request 问答请求（问题、历史、宠物信息、可检索文档 id）
     * @return 回答与参考来源
     * @throws KnowledgeServiceException 调用失败/超时/重试后仍失败
     */
    public RagAskResponse ask(RagAskRequest request) {
        return execute("知识库问答", ASK_PATH, request, RagAskResponse.class, askRestTemplate);
    }

    /**
     * 调用 RAG 重建索引接口 POST /api/knowledge/reindex
     *
     * @param docs 全量启用文档（RAG 侧会清空后重建）
     * @return 入库文档数与片段数
     * @throws KnowledgeServiceException 调用失败/超时/重试后仍失败
     */
    public RagReindexResponse rebuildIndex(List<KnowledgeDoc> docs) {
        RagReindexRequest request = new RagReindexRequest();
        if (docs != null && !docs.isEmpty()) {
            request.setDocuments(docs.stream().map(this::toReindexDocument).toList());
        }
        return execute("重建知识库索引", REINDEX_PATH, request, RagReindexResponse.class, reindexRestTemplate);
    }

    private RagReindexDocument toReindexDocument(KnowledgeDoc doc) {
        RagReindexDocument document = new RagReindexDocument();
        document.setId(doc.getId());
        document.setTitle(doc.getTitle());
        document.setCategory(doc.getCategory());
        document.setContent(doc.getContent());
        return document;
    }

    /**
     * 统一执行请求
     *
     * <p>重试条件：连接失败、读写超时、5xx、响应体解析失败。
     * 4xx（如 API Key 错误、参数不合法）属于配置/契约问题，重试无意义，直接失败。</p>
     */
    private <T> T execute(String action, String path, Object body, Class<T> responseType, RestTemplate restTemplate) {
        String url = properties.getBaseUrl() + path;
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<T> response = restTemplate.postForEntity(url, buildEntity(body), responseType);
                T result = response.getBody();
                if (result != null) {
                    return result;
                }
                // 200 但响应体为空：按可重试失败处理
                lastError = new IllegalStateException("响应体为空");
                log.warn("{} 调用返回空响应体（第 {}/{} 次）: url={}", action, attempt, maxAttempts, url);
            } catch (HttpClientErrorException e) {
                log.error("{} 调用失败（客户端错误，不重试）: url={}, status={}, body={}",
                        action, url, e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
                throw new KnowledgeServiceException(e);
            } catch (RestClientException | HttpMessageConversionException e) {
                lastError = e;
                log.warn("{} 调用失败（第 {}/{} 次）: url={}, error={}",
                        action, attempt, maxAttempts, url, e.getMessage());
            }
        }

        log.error("{} 调用失败，已重试 {} 次: url={}", action, maxAttempts - 1, url, lastError);
        throw new KnowledgeServiceException(lastError);
    }

    private HttpEntity<Object> buildEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 请求头携带 X-API-Key，与 rag-service 的 RAG_API_KEY 对应
        headers.set(properties.getApiKeyHeader(), properties.getApiKey());
        return new HttpEntity<>(body, headers);
    }

    private String abbreviate(String text) {
        return StringUtils.abbreviate(text, LOG_BODY_MAX_LENGTH);
    }

}
