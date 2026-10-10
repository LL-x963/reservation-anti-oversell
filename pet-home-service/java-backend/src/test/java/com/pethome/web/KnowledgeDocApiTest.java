package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.entity.Employee;
import com.pethome.mapper.EmployeeMapper;
import com.pethome.rag.RagIndexClient;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 知识库文档管理接口集成测试（/api/admin/knowledge/doc）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KnowledgeDocApiTest {

    private static final String TITLE_PREFIX = "IT-知识库-";
    private static final String EMP_USERNAME = "it_knowledge_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final String USER_OPENID = "it_knowledge_user";

    private static final String BASE = "/api/admin/knowledge/doc";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    /** RAG 索引客户端用 Mock 替换，避免测试依赖真实 rag-service */
    @MockitoBean
    private RagIndexClient ragIndexClient;

    private Employee employee;
    private Long userId;

    @BeforeEach
    void setUp() {
        cleanUp();

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("知识库测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employeeMapper.insert(employee);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void adminEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity(BASE + "/page", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.getForEntity(BASE + "/indexStatus", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.getForEntity(BASE + "/1", String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.postForEntity(BASE + "/add",
                Map.of("title", "x", "category", "护理", "content", "y"), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange(BASE + "/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "title", "x", "category", "护理", "content", "y")), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange(BASE + "/status", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "enabled", 0)), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.postForEntity(BASE + "/reindex", new HttpEntity<>(new HttpHeaders()), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange(BASE + "/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void userTokenShouldNotAccessAdminEndpoints() {
        String token = userToken();

        assertThat(restTemplate.exchange(BASE + "/page", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 管理端主流程 ====================

    @Test
    void adminCrudAndReindexFlowShouldWork() {
        String token = adminToken();
        String title = TITLE_PREFIX + "猫咪护理指南";

        // 新增
        Long id = addDoc(token, title, "护理", "猫咪护理正文");
        assertThat(id).isNotNull().isPositive();

        // 分页：分类 + 启用状态筛选，列表不返回大字段 content
        JSONObject pageData = getJson(BASE + "/page?category=护理&enabled=1&page=1&pageSize=10", token)
                .getJSONObject("data");
        assertThat(pageData.getLong("total")).isEqualTo(1L);
        JSONObject record = pageData.getJSONArray("records").getJSONObject(0);
        assertThat(record.getString("title")).isEqualTo(title);
        assertThat(record.getInteger("enabled")).isEqualTo(1);
        assertThat(record.getInteger("indexStatus")).isZero();
        assertThat(record.getString("indexStatusDesc")).isEqualTo("未索引");
        assertThat(record.containsKey("content")).isFalse();

        // 详情返回正文
        JSONObject detail = getJson(BASE + "/" + id, token).getJSONObject("data");
        assertThat(detail.getString("title")).isEqualTo(title);
        assertThat(detail.getString("content")).isEqualTo("猫咪护理正文");

        // 修改
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", id);
        updateBody.put("title", title + "-改");
        updateBody.put("category", "饮食");
        updateBody.put("content", "更新后的正文");
        assertThat(putJson(BASE + "/update", updateBody, token).getInteger("code")).isEqualTo(1);

        JSONObject updated = getJson(BASE + "/" + id, token).getJSONObject("data");
        assertThat(updated.getString("title")).isEqualTo(title + "-改");
        assertThat(updated.getString("category")).isEqualTo("饮食");
        assertThat(updated.getString("content")).isEqualTo("更新后的正文");
        // 内容变更后索引失效
        assertThat(updated.getInteger("indexStatus")).isZero();

        // 禁用
        assertThat(putJson(BASE + "/status", Map.of("id", id, "enabled", 0), token).getInteger("code")).isEqualTo(1);
        assertThat(getJson(BASE + "/page?enabled=1", token).getJSONObject("data").getLong("total")).isZero();
        assertThat(getJson(BASE + "/page?enabled=0", token).getJSONObject("data").getLong("total")).isEqualTo(1L);

        // 重新启用
        assertThat(putJson(BASE + "/status", Map.of("id", id, "enabled", 1), token).getInteger("code")).isEqualTo(1);

        // 重建索引：异步执行，立即返回（RAG 索引客户端为 Mock，按成功处理）
        when(ragIndexClient.rebuildIndex(any())).thenReturn(true);
        assertThat(postJson(BASE + "/reindex", Map.of(), token).getInteger("code")).isEqualTo(1);
        JSONObject indexStatus = awaitRebuildFinished(token);
        assertThat(indexStatus.getBoolean("rebuilding")).isFalse();
        assertThat(indexStatus.getLong("normalCount")).isGreaterThanOrEqualTo(1L);

        // 重建后文档索引状态为 2正常
        JSONObject afterReindex = getJson(BASE + "/" + id, token).getJSONObject("data");
        assertThat(afterReindex.getInteger("indexStatus")).isEqualTo(2);
        assertThat(afterReindex.getString("indexStatusDesc")).isEqualTo("正常");

        // 再次修改内容后索引状态重置为 0未索引
        updateBody.put("content", "第二次更新正文");
        assertThat(putJson(BASE + "/update", updateBody, token).getInteger("code")).isEqualTo(1);
        assertThat(getJson(BASE + "/" + id, token).getJSONObject("data").getInteger("indexStatus")).isZero();

        // 删除
        assertThat(deleteDoc(token, id).getInteger("code")).isEqualTo(1);
        assertThat(getJson(BASE + "/page?enabled=1", token).getJSONObject("data").getLong("total")).isZero();
    }

    // ==================== 业务规则 ====================

    @Test
    void addShouldRejectDuplicatedTitle() {
        String token = adminToken();
        addDoc(token, TITLE_PREFIX + "重名文档", "护理", "正文");

        JSONObject response = postJson(BASE + "/add",
                Map.of("title", TITLE_PREFIX + "重名文档", "category", "饮食", "content", "另一份正文"), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("文档标题已存在");
    }

    @Test
    void addShouldRejectInvalidCategory() {
        String token = adminToken();

        JSONObject response = postJson(BASE + "/add",
                Map.of("title", TITLE_PREFIX + "非法分类", "category", "其他分类", "content", "正文"), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("文档分类只能是");
    }

    @Test
    void addShouldRejectBlankTitleOrContent() {
        String token = adminToken();

        JSONObject response = postJson(BASE + "/add",
                Map.of("title", " ", "category", "护理", "content", " "), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
    }

    @Test
    void reindexShouldBeRejectedWhenAlreadyIndexing() {
        String token = adminToken();
        Long id = addDoc(token, TITLE_PREFIX + "索引中", "护理", "正文");
        jdbcTemplate.update("UPDATE knowledge_doc SET index_status = 1 WHERE id = ?", id);

        JSONObject response = postJson(BASE + "/reindex", Map.of(), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("正在重建中");
    }

    // ==================== 参数校验 ====================

    @Test
    void pageShouldRejectInvalidQueryParam() {
        String token = adminToken();

        JSONObject pageSize = getJson(BASE + "/page?page=1&pageSize=1000", token);
        assertThat(pageSize.getInteger("code")).isZero();
        assertThat(pageSize.getString("msg")).contains("每页条数");

        JSONObject category = getJson(BASE + "/page?category=其他分类", token);
        assertThat(category.getInteger("code")).isZero();
        assertThat(category.getString("msg")).contains("文档分类只能是");

        JSONObject enabled = getJson(BASE + "/page?enabled=9", token);
        assertThat(enabled.getInteger("code")).isZero();
        assertThat(enabled.getString("msg")).contains("启用状态");
    }

    @Test
    void statusShouldRejectInvalidEnabledValue() {
        String token = adminToken();
        Long id = addDoc(token, TITLE_PREFIX + "非法启用状态", "护理", "正文");

        JSONObject response = putJson(BASE + "/status", Map.of("id", id, "enabled", 9), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("启用状态");
    }

    @Test
    void detailAndDeleteNonexistentDocShouldFail() {
        String token = adminToken();

        JSONObject detail = getJson(BASE + "/99999999", token);
        assertThat(detail.getInteger("code")).isZero();
        assertThat(detail.getString("msg")).contains("文档不存在");

        JSONObject delete = deleteDoc(token, 99999999L);
        assertThat(delete.getInteger("code")).isZero();
        assertThat(delete.getString("msg")).contains("文档不存在");
    }

    @Test
    void indexStatusShouldReturnOverviewFields() {
        String token = adminToken();
        Long id = addDoc(token, TITLE_PREFIX + "概览", "护理", "正文");

        JSONObject response = getJson(BASE + "/indexStatus", token);

        assertThat(response.getInteger("code")).isEqualTo(1);
        JSONObject data = response.getJSONObject("data");
        assertThat(data.getLong("totalCount")).isGreaterThanOrEqualTo(1L);
        assertThat(data.getLong("enabledCount")).isGreaterThanOrEqualTo(1L);
        assertThat(data.getLong("disabledCount")).isZero();
        assertThat(data.getLong("notIndexedCount")).isGreaterThanOrEqualTo(1L);
        assertThat(data.getLong("indexingCount")).isZero();
        assertThat(data.getLong("normalCount")).isZero();
        assertThat(data.getLong("failedCount")).isZero();
        assertThat(data.getBoolean("rebuilding")).isFalse();

        // 新增文档默认为 0未索引
        assertThat(getJson(BASE + "/" + id, token).getJSONObject("data").getInteger("indexStatus")).isZero();
    }

    // ==================== 辅助方法 ====================

    private String adminToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private String userToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", USER_OPENID), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        JSONObject data = body.getJSONObject("data");
        userId = data.getLong("userId");
        return data.getString("token");
    }

    private Long addDoc(String token, String title, String category, String content) {
        JSONObject response = postJson(BASE + "/add",
                Map.of("title", title, "category", category, "content", content), token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONObject deleteDoc(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange(BASE + "/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    /**
     * 轮询等待异步重建索引结束（最长 15 秒），返回索引状态概览 data
     */
    private JSONObject awaitRebuildFinished(String token) {
        long deadline = System.currentTimeMillis() + 15_000L;
        JSONObject data = getJson(BASE + "/indexStatus", token).getJSONObject("data");
        while (Boolean.TRUE.equals(data.getBoolean("rebuilding")) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待索引重建被中断", e);
            }
            data = getJson(BASE + "/indexStatus", token).getJSONObject("data");
        }
        return data;
    }

    private JSONObject getJson(String path, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONObject postJson(String path, Map<String, Object> body, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONObject putJson(String path, Map<String, Object> body, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM knowledge_doc WHERE title LIKE ?", TITLE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `user` WHERE openid = ?", USER_OPENID);
        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);

        if (userId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId));
            userId = null;
        }
        if (employee != null && employee.getId() != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.EMPLOYEE, employee.getId()));
        }
    }

}
