package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.entity.Employee;
import com.pethome.mapper.EmployeeMapper;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 美容师管理接口集成测试（管理端 /api/admin/beautician）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BeauticianApiTest {

    private static final String NAME_PREFIX = "IT-美容师-";
    private static final String ORDER_PREFIX = "IT-BEA-ORD-";
    private static final String EMP_USERNAME = "it_beautician_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final String USER_OPENID = "it_beautician_user";

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

    private Employee employee;
    private Long userId;

    @BeforeEach
    void setUp() {
        cleanUp();

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("美容师测试员工");
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
    void adminBeauticianEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/admin/beautician/page", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.getForEntity("/api/admin/beautician/list", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/admin/beautician/add",
                Map.of("name", NAME_PREFIX + "x"), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/beautician/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "name", NAME_PREFIX + "x", "status", 1)), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/beautician/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void userTokenShouldNotAccessAdminBeauticianEndpoint() {
        String userToken = userToken();

        assertThat(restTemplate.exchange("/api/admin/beautician/list", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== CRUD ====================

    @Test
    void crudFlowShouldWork() {
        String token = adminToken();
        String name = NAME_PREFIX + "小王";

        // 新增：技能标签传数组
        Long id = addBeautician(token, name, Arrays.asList("洗护", "美容"), 1, "2024-03-01");
        assertThat(id).isNotNull().isPositive();

        // 分页查询命中，返回评分默认 5.00、技能标签为数组
        JSONObject record = firstRecord("/api/admin/beautician/page?name=" + NAME_PREFIX + "小王", token);
        assertThat(record.getString("name")).isEqualTo(name);
        assertThat(record.getBigDecimal("score")).isEqualByComparingTo("5.00");
        assertThat(record.getInteger("status")).isEqualTo(1);
        assertThat(record.getString("entryTime")).isEqualTo("2024-03-01");
        assertThat(record.getJSONArray("skillTags")).containsExactly("洗护", "美容");

        // 在职列表可查
        assertThat(names(listOnDuty(token))).contains(name);

        // 修改：改为离职
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", id);
        updateBody.put("name", name + "改");
        updateBody.put("skillTags", Arrays.asList("SPA"));
        updateBody.put("serviceRange", "朝阳区");
        updateBody.put("entryTime", "2023-01-01");
        updateBody.put("status", 0);
        assertThat(putJson("/api/admin/beautician/update", updateBody, token).getInteger("code")).isEqualTo(1);

        JSONObject updated = firstRecord("/api/admin/beautician/page?name=" + NAME_PREFIX + "小王改", token);
        assertThat(updated.getJSONArray("skillTags")).containsExactly("SPA");
        assertThat(updated.getString("serviceRange")).isEqualTo("朝阳区");
        assertThat(updated.getInteger("status")).isZero();
        assertThat(updated.getBigDecimal("score")).isEqualByComparingTo("5.00");

        // 离职后不在在职列表
        assertThat(names(listOnDuty(token))).doesNotContain(name + "改");

        // 删除
        assertThat(deleteBeautician(token, id).getInteger("code")).isEqualTo(1);
        assertThat(getJson("/api/admin/beautician/page?name=" + NAME_PREFIX + "小王改", token)
                .getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void pageShouldFilterBySkillTagExactMatch() {
        String token = adminToken();
        addBeautician(token, NAME_PREFIX + "洗护师", Arrays.asList("洗护"), 1, null);
        addBeautician(token, NAME_PREFIX + "美容师", Arrays.asList("美容"), 1, null);

        JSONObject matched = getJson("/api/admin/beautician/page?skillTag=洗护", token);
        assertThat(matched.getJSONObject("data").getLong("total")).isEqualTo(1L);
        assertThat(matched.getJSONObject("data").getJSONArray("records").getJSONObject(0).getString("name"))
                .isEqualTo(NAME_PREFIX + "洗护师");

        // 部分匹配不命中
        JSONObject partial = getJson("/api/admin/beautician/page?skillTag=洗", token);
        assertThat(partial.getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void listShouldOnlyReturnOnDutyBeauticians() {
        String token = adminToken();
        addBeautician(token, NAME_PREFIX + "在职", null, 1, null);
        addBeautician(token, NAME_PREFIX + "离职", null, 0, null);

        List<String> names = names(listOnDuty(token));

        assertThat(names).contains(NAME_PREFIX + "在职").doesNotContain(NAME_PREFIX + "离职");
    }

    @Test
    void addShouldRejectNonexistentPathIdOnDelete() {
        String token = adminToken();

        JSONObject response = deleteBeautician(token, 0L);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("id");
    }

    @Test
    void updateNonexistentBeauticianShouldFail() {
        String token = adminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("id", 99999999L);
        body.put("name", NAME_PREFIX + "不存在");
        body.put("status", 1);

        JSONObject response = putJson("/api/admin/beautician/update", body, token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("美容师不存在");
    }

    // ==================== 业务规则 ====================

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        String token = adminToken();
        Long id = addBeautician(token, NAME_PREFIX + "有未完成订单", null, 1, null);
        insertOrder(id, 2);

        JSONObject response = deleteBeautician(token, id);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("未完成订单");
        assertThat(getJson("/api/admin/beautician/page?name=" + NAME_PREFIX + "有未完成订单", token)
                .getJSONObject("data").getLong("total")).isEqualTo(1L);
    }

    @Test
    void deleteShouldSucceedWhenOnlyFinishedOrderExists() {
        String token = adminToken();
        Long id = addBeautician(token, NAME_PREFIX + "只有已完成订单", null, 1, null);
        insertOrder(id, 5);

        assertThat(deleteBeautician(token, id).getInteger("code")).isEqualTo(1);
    }

    // ==================== 参数校验 ====================

    @Test
    void addShouldRejectInvalidParam() {
        String token = adminToken();

        assertThat(postJson("/api/admin/beautician/add", Map.of("name", ""), token).getInteger("code")).isZero();

        Map<String, Object> invalidStatus = new HashMap<>();
        invalidStatus.put("name", NAME_PREFIX + "非法状态");
        invalidStatus.put("status", 5);
        assertThat(postJson("/api/admin/beautician/add", invalidStatus, token).getInteger("code")).isZero();

        Map<String, Object> futureEntry = new HashMap<>();
        futureEntry.put("name", NAME_PREFIX + "未来入职");
        futureEntry.put("entryTime", "2999-01-01");
        JSONObject entryResponse = postJson("/api/admin/beautician/add", futureEntry, token);
        assertThat(entryResponse.getInteger("code")).isZero();
        assertThat(entryResponse.getString("msg")).contains("入职时间");

        Map<String, Object> tooManyTags = new HashMap<>();
        tooManyTags.put("name", NAME_PREFIX + "标签过多");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tags.add("标签" + i);
        }
        tooManyTags.put("skillTags", tags);
        JSONObject tagResponse = postJson("/api/admin/beautician/add", tooManyTags, token);
        assertThat(tagResponse.getInteger("code")).isZero();
        assertThat(tagResponse.getString("msg")).contains("技能标签数量");
    }

    @Test
    void pageShouldRejectInvalidPageSize() {
        String token = adminToken();

        JSONObject response = getJson("/api/admin/beautician/page?page=1&pageSize=1000", token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("每页条数");
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

    private Long addBeautician(String token, String name, List<String> skillTags, Integer status, String entryTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (skillTags != null) {
            body.put("skillTags", skillTags);
        }
        if (status != null) {
            body.put("status", status);
        }
        if (entryTime != null) {
            body.put("entryTime", entryTime);
        }
        JSONObject response = postJson("/api/admin/beautician/add", body, token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONObject deleteBeautician(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange("/api/admin/beautician/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONArray listOnDuty(String token) {
        JSONObject response = getJson("/api/admin/beautician/list", token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getJSONArray("data");
    }

    private JSONObject firstRecord(String path, String token) {
        JSONObject response = getJson(path, token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getJSONObject("data").getJSONArray("records").getJSONObject(0);
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

    private List<String> names(JSONArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getJSONObject(i).getString("name"));
        }
        return result;
    }

    private void insertOrder(Long beauticianId, int orderStatus) {
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, beautician_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                ORDER_PREFIX + System.nanoTime(), 1L, beauticianId, new BigDecimal("99.00"), orderStatus);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM service_order WHERE order_number LIKE ?", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM beautician WHERE name LIKE ?", NAME_PREFIX + "%");
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
