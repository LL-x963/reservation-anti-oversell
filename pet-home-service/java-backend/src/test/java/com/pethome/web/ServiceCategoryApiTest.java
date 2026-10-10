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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务分类接口集成测试（管理端 /api/admin/category，用户端 /api/user/category）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceCategoryApiTest {

    private static final String NAME_PREFIX = "IT-分类-";
    private static final String EMP_USERNAME = "it_category_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final String USER_OPENID = "it_category_user";

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

    /** 登录产生的 C 端用户 id，用于清理 */
    private Long userId;

    @BeforeEach
    void setUp() {
        cleanUp();
        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("分类测试员工");
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
    void adminCategoryEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/admin/category/list", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/admin/category/add", Map.of("name", NAME_PREFIX + "无token"),
                String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/category/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "name", "x", "sort", 0, "status", 1)), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/category/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void userCategoryEndpointShouldRequireUserToken() {
        assertThat(restTemplate.getForEntity("/api/user/category/list", String.class).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void tokenIdentityShouldNotBeCrossUsable() {
        String adminToken = adminToken();
        String userToken = userToken();

        // 员工 token 访问用户端接口
        assertThat(restTemplate.exchange("/api/user/category/list", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), String.class).getStatusCode().value()).isEqualTo(401);

        // 用户 token 访问管理端接口
        assertThat(restTemplate.exchange("/api/admin/category/list", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 管理端 CRUD ====================

    @Test
    void adminCrudFlowShouldWork() {
        String token = adminToken();
        String name = NAME_PREFIX + "基础洗护";

        // 新增
        Long id = addCategory(token, name, 2, 1);
        assertThat(id).isNotNull().isPositive();

        JSONArray list = listForAdmin(token);
        JSONObject added = findById(list, id);
        assertThat(added).isNotNull();
        assertThat(added.getString("name")).isEqualTo(name);
        assertThat(added.getInteger("sort")).isEqualTo(2);
        assertThat(added.getInteger("status")).isEqualTo(1);
        assertThat(added.getString("createTime")).isNotBlank();
        assertThat(added.getString("updateTime")).isNotBlank();

        // 修改
        String newName = NAME_PREFIX + "洗护改名";
        JSONObject updateResp = updateCategory(token, id, newName, 5, 0);
        assertThat(updateResp.getInteger("code")).isEqualTo(1);

        JSONObject updated = findById(listForAdmin(token), id);
        assertThat(updated.getString("name")).isEqualTo(newName);
        assertThat(updated.getInteger("sort")).isEqualTo(5);
        assertThat(updated.getInteger("status")).isZero();

        // 删除
        JSONObject deleteResp = deleteCategory(token, id);
        assertThat(deleteResp.getInteger("code")).isEqualTo(1);
        assertThat(findById(listForAdmin(token), id)).isNull();
    }

    @Test
    void addShouldRejectDuplicatedName() {
        String token = adminToken();
        String name = NAME_PREFIX + "重名";
        addCategory(token, name, 0, 1);

        JSONObject response = postJson("/api/admin/category/add", Map.of("name", name), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("分类名称已存在");
    }

    @Test
    void updateShouldRejectDuplicatedName() {
        String token = adminToken();
        Long firstId = addCategory(token, NAME_PREFIX + "第一个", 0, 1);
        Long secondId = addCategory(token, NAME_PREFIX + "第二个", 0, 1);

        JSONObject response = updateCategory(token, secondId, NAME_PREFIX + "第一个", 0, 1);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("分类名称已存在");
        // 第一个分类未受影响
        assertThat(findById(listForAdmin(token), firstId).getString("name")).isEqualTo(NAME_PREFIX + "第一个");
    }

    @Test
    void deleteShouldRejectWhenServiceItemRelated() {
        String token = adminToken();
        Long id = addCategory(token, NAME_PREFIX + "有关联项目", 0, 1);
        insertServiceItem(id, 1);

        JSONObject response = deleteCategory(token, id);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("服务项目");
        assertThat(findById(listForAdmin(token), id)).isNotNull();
    }

    @Test
    void updateAndDeleteNonexistentCategoryShouldFail() {
        String token = adminToken();

        JSONObject updateResp = updateCategory(token, 99999999L, NAME_PREFIX + "不存在", 0, 1);
        assertThat(updateResp.getInteger("code")).isZero();
        assertThat(updateResp.getString("msg")).contains("分类不存在");

        JSONObject deleteResp = deleteCategory(token, 99999999L);
        assertThat(deleteResp.getInteger("code")).isZero();
        assertThat(deleteResp.getString("msg")).contains("分类不存在");
    }

    @Test
    void addShouldRejectInvalidParam() {
        String token = adminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("name", "");
        body.put("sort", -1);
        body.put("status", 2);

        JSONObject response = postJson("/api/admin/category/add", body, token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
    }

    @Test
    void deleteShouldRejectInvalidPathId() {
        String token = adminToken();

        JSONObject response = deleteCategory(token, 0L);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("id");
    }

    // ==================== 用户端 ====================

    @Test
    void userListShouldOnlyReturnEnabledCategories() {
        String adminToken = adminToken();
        String enabledName = NAME_PREFIX + "用户端可见";
        String disabledName = NAME_PREFIX + "用户端不可见";
        addCategory(adminToken, enabledName, 1, 1);
        addCategory(adminToken, disabledName, 2, 0);

        String userToken = userToken();
        JSONObject response = getJson("/api/user/category/list", userToken);

        assertThat(response.getInteger("code")).isEqualTo(1);
        JSONArray data = response.getJSONArray("data");
        assertThat(names(data)).contains(enabledName).doesNotContain(disabledName);
        // 用户端不暴露 status 等管理字段
        assertThat(data.getJSONObject(0).containsKey("status")).isFalse();
        assertThat(data.getJSONObject(0).containsKey("createTime")).isFalse();
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

    private Long addCategory(String token, String name, int sort, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("sort", sort);
        body.put("status", status);
        JSONObject response = postJson("/api/admin/category/add", body, token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONObject updateCategory(String token, Long id, String name, int sort, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("name", name);
        body.put("sort", sort);
        body.put("status", status);
        return putJson("/api/admin/category/update", body, token);
    }

    private JSONObject deleteCategory(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange("/api/admin/category/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONArray listForAdmin(String token) {
        JSONObject response = getJson("/api/admin/category/list", token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getJSONArray("data");
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

    private JSONObject findById(JSONArray array, Long id) {
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (id.equals(item.getLong("id"))) {
                return item;
            }
        }
        return null;
    }

    private List<String> names(JSONArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getJSONObject(i).getString("name"));
        }
        return result;
    }

    private void insertServiceItem(Long categoryId, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                categoryId, "IT-测试服务项目", new BigDecimal("99.00"), status);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name LIKE ?)", NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_category WHERE name LIKE ?", NAME_PREFIX + "%");
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
