package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.rag.KnowledgeClient;
import com.pethome.rag.dto.RagAskResponse;
import com.pethome.rag.dto.RagReference;
import com.pethome.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 用户端知识库问答接口集成测试（/api/user/knowledge）
 *
 * <p>RAG 客户端用 Mock 替换（本地不需要启动 rag-service），其余走真实 HTTP + MySQL + Redis。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KnowledgeApiTest {

    private static final String BASE = "/api/user/knowledge";
    private static final String USER_A_OPENID = "it_knowledge_ai_a";
    private static final String USER_B_OPENID = "it_knowledge_ai_b";
    private static final String PET_PREFIX = "IT-KB-宠物-";
    private static final String QUESTION = "IT-KB-猫咪掉毛怎么办";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @MockitoBean
    private KnowledgeClient knowledgeClient;

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void knowledgeEndpointsShouldRequireToken() {
        assertThat(restTemplate.postForEntity(BASE + "/ask", Map.of("question", QUESTION), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.getForEntity(BASE + "/history/page", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.exchange(BASE + "/history/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange(BASE + "/history", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void knowledgeEndpointsShouldRejectInvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token");

        assertThat(restTemplate.exchange(BASE + "/history/page", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 问答主流程 ====================

    @Test
    void askShouldReturnAnswerAndRecordHistory() {
        String token = loginAsA();
        stubRagAnswer();

        Map<String, Object> body = new HashMap<>();
        body.put("question", QUESTION);
        body.put("conversationHistory", List.of(
                Map.of("role", "user", "content", "之前问过什么"),
                Map.of("role", "assistant", "content", "之前回答过什么")));

        JSONObject askResponse = ask(token, body);
        assertThat(askResponse.getInteger("code")).isEqualTo(1);
        JSONObject data = askResponse.getJSONObject("data");
        assertThat(data.getString("answer")).contains("建议每天给猫咪梳毛");
        assertThat(data.getJSONArray("references")).hasSize(1);
        assertThat(data.getJSONArray("references").getJSONObject(0).getLong("docId")).isEqualTo(1L);
        assertThat(data.getJSONArray("references").getJSONObject(0).getString("title")).isEqualTo("猫咪护理");

        // 历史分页
        JSONObject page = getJson(BASE + "/history/page?page=1&pageSize=10", token);
        assertThat(page.getInteger("code")).isEqualTo(1);
        JSONObject pageData = page.getJSONObject("data");
        assertThat(pageData.getLong("total")).isEqualTo(1L);
        JSONObject record = pageData.getJSONArray("records").getJSONObject(0);
        assertThat(record.getString("question")).isEqualTo(QUESTION);
        assertThat(record.getString("answer")).contains("以上内容仅供科普参考");
        assertThat(record.getJSONArray("references")).hasSize(1);
        assertThat(record.getString("createTime")).isNotBlank();

        // 删除单条历史
        Long historyId = record.getLong("id");
        assertThat(deleteJson(BASE + "/history/" + historyId, token).getInteger("code")).isEqualTo(1);
        assertThat(getJson(BASE + "/history/page", token).getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void askShouldReturnBizCode600WhenRagServiceUnavailable() {
        String token = loginAsA();
        when(knowledgeClient.ask(any())).thenThrow(new KnowledgeServiceException());

        JSONObject response = ask(token, Map.of("question", QUESTION));

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getInteger("bizCode")).isEqualTo(600);
        assertThat(response.getString("msg")).isEqualTo(KnowledgeServiceException.MESSAGE);
        // 调用失败不落库
        assertThat(getJson(BASE + "/history/page", token).getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void askShouldRejectPetOfOtherUser() {
        // 先让 B 登录（openid 自动注册），再为 B 创建一只宠物
        loginAsB();
        Long otherPetId = insertPet(USER_B_OPENID, PET_PREFIX + "别人的狗");
        String token = loginAsA();

        Map<String, Object> body = new HashMap<>();
        body.put("question", QUESTION);
        body.put("petId", otherPetId);

        JSONObject response = ask(token, body);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("宠物不存在");
        assertThat(getJson(BASE + "/history/page", token).getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void askShouldRejectInvalidParam() {
        String token = loginAsA();

        // 问题为空
        assertThat(ask(token, Map.of("question", " ")).getInteger("code")).isZero();

        // 对话角色非法
        JSONObject badRole = ask(token, Map.of("question", QUESTION,
                "conversationHistory", List.of(Map.of("role", "robot", "content", "你好"))));
        assertThat(badRole.getInteger("code")).isZero();
        assertThat(badRole.getString("msg")).contains("对话角色");

        // 对话历史超过 20 条
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(Map.of("role", "user", "content", "问题" + i));
        }
        assertThat(ask(token, Map.of("question", QUESTION, "conversationHistory", tooMany)).getInteger("code")).isZero();

        // 分页参数非法
        JSONObject badPage = getJson(BASE + "/history/page?page=1&pageSize=1000", token);
        assertThat(badPage.getInteger("code")).isZero();
        assertThat(badPage.getString("msg")).contains("每页条数");
    }

    // ==================== 历史记录归属 ====================

    @Test
    void historyShouldBeIsolatedBetweenUsers() {
        String tokenA = loginAsA();
        stubRagAnswer();
        assertThat(ask(tokenA, Map.of("question", QUESTION)).getInteger("code")).isEqualTo(1);
        Long historyId = getJson(BASE + "/history/page", tokenA)
                .getJSONObject("data").getJSONArray("records").getJSONObject(0).getLong("id");

        // 用户 B 看不到 A 的历史，也删不掉
        String tokenB = loginAsB();
        assertThat(getJson(BASE + "/history/page", tokenB).getJSONObject("data").getLong("total")).isZero();

        JSONObject deleteResponse = deleteJson(BASE + "/history/" + historyId, tokenB);
        assertThat(deleteResponse.getInteger("code")).isZero();
        assertThat(deleteResponse.getString("msg")).contains("问答记录不存在");
        assertThat(getJson(BASE + "/history/page", tokenA).getJSONObject("data").getLong("total")).isEqualTo(1L);

        // 删除不存在的记录
        JSONObject notFound = deleteJson(BASE + "/history/99999999", tokenA);
        assertThat(notFound.getInteger("code")).isZero();
        assertThat(notFound.getString("msg")).contains("问答记录不存在");
    }

    @Test
    void clearHistoryShouldRemoveOnlyCurrentUserHistory() {
        String tokenA = loginAsA();
        stubRagAnswer();
        assertThat(ask(tokenA, Map.of("question", QUESTION)).getInteger("code")).isEqualTo(1);
        assertThat(ask(tokenA, Map.of("question", QUESTION + "2")).getInteger("code")).isEqualTo(1);
        assertThat(getJson(BASE + "/history/page", tokenA).getJSONObject("data").getLong("total")).isEqualTo(2L);

        assertThat(deleteJson(BASE + "/history", tokenA).getInteger("code")).isEqualTo(1);

        assertThat(getJson(BASE + "/history/page", tokenA).getJSONObject("data").getLong("total")).isZero();
    }

    // ==================== 辅助方法 ====================

    private void stubRagAnswer() {
        RagReference reference = new RagReference();
        reference.setDocId(1L);
        reference.setTitle("猫咪护理");
        reference.setContent("每周梳毛2-3次");

        RagAskResponse response = new RagAskResponse();
        response.setAnswer("建议每天给猫咪梳毛一次，并注意按时驱虫。\n\n以上内容仅供科普参考，若宠物出现不适或症状严重，请及时前往宠物医院就医。");
        response.setReferences(new ArrayList<>(List.of(reference)));
        when(knowledgeClient.ask(any())).thenReturn(response);
    }

    private String loginAsA() {
        JSONObject data = login(USER_A_OPENID);
        userAId = data.getLong("userId");
        return data.getString("token");
    }

    private String loginAsB() {
        JSONObject data = login(USER_B_OPENID);
        userBId = data.getLong("userId");
        return data.getString("token");
    }

    private JSONObject login(String openid) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", openid), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data");
    }

    private JSONObject ask(String token, Map<String, Object> body) {
        ResponseEntity<String> response = restTemplate.exchange(BASE + "/ask", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONObject getJson(String path, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONObject deleteJson(String path, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private Long insertPet(String openid, String name) {
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
        jdbcTemplate.update("INSERT INTO pet_profile (user_id, name, breed, gender, birthday, weight, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                userId, name, "金毛", 1, java.sql.Date.valueOf(LocalDate.now().minusYears(3)), new BigDecimal("20.00"));
        return jdbcTemplate.queryForObject("SELECT id FROM pet_profile WHERE user_id = ? AND name = ?",
                Long.class, userId, name);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM ai_conversation WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid IN (?, ?))", USER_A_OPENID, USER_B_OPENID);
        jdbcTemplate.update("DELETE FROM pet_profile WHERE name LIKE ?", PET_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `user` WHERE openid IN (?, ?)", USER_A_OPENID, USER_B_OPENID);

        if (userAId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userAId));
            userAId = null;
        }
        if (userBId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userBId));
            userBId = null;
        }
    }

}
