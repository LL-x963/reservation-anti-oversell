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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务项目接口集成测试（管理端 /api/admin/service，用户端 /api/user/service）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceItemApiTest {

    private static final String CAT_PREFIX = "IT-服务分类-";
    private static final String ITEM_PREFIX = "IT-服务项目-";
    private static final String ORDER_PREFIX = "IT-ORD-";
    private static final String EMP_USERNAME = "it_service_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final String USER_OPENID = "it_service_user";

    private static final String CAT_NAME = CAT_PREFIX + "基础洗护";
    private static final String CAT_NAME_2 = CAT_PREFIX + "高级美容";

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
    private Long catId;
    private Long catId2;

    @BeforeEach
    void setUp() {
        cleanUp();

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("服务项目测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employeeMapper.insert(employee);

        catId = insertCategory(CAT_NAME);
        catId2 = insertCategory(CAT_NAME_2);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void adminServiceEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/admin/service/page", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/admin/service/add",
                Map.of("categoryId", catId, "name", ITEM_PREFIX + "x", "price", "99.00"), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/service/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "categoryId", catId, "name", "x", "price", "1.00")), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/service/status/1", HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", 0)), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/service/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void userServiceEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/user/service/page", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.getForEntity("/api/user/service/1", String.class).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void tokenIdentityShouldNotBeCrossUsable() {
        String adminToken = adminToken();
        String userToken = userToken();

        assertThat(restTemplate.exchange("/api/user/service/page", HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/service/page", HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 管理端 CRUD ====================

    @Test
    void adminCrudAndStatusFlowShouldWork() {
        String token = adminToken();
        String itemName = ITEM_PREFIX + "猫咪洗澡";

        // 新增
        Long id = addItem(token, catId, itemName, "99.00", "猫", null, null);
        assertThat(id).isNotNull().isPositive();

        // 分页查询命中
        JSONObject pageBody = getJson("/api/admin/service/page?categoryId=" + catId + "&page=1&pageSize=10", token);
        assertThat(pageBody.getInteger("code")).isEqualTo(1);
        JSONObject data = pageBody.getJSONObject("data");
        assertThat(data.getLong("total")).isEqualTo(1L);
        JSONObject record = data.getJSONArray("records").getJSONObject(0);
        assertThat(record.getString("name")).isEqualTo(itemName);
        assertThat(record.getInteger("status")).isEqualTo(1);
        assertThat(record.getString("createTime")).isNotBlank();

        // 修改
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", id);
        updateBody.put("categoryId", catId);
        updateBody.put("name", itemName + "改");
        updateBody.put("price", "128.00");
        updateBody.put("serviceDuration", 90);
        updateBody.put("petType", "通用");
        assertThat(putJson("/api/admin/service/update", updateBody, token).getInteger("code")).isEqualTo(1);

        JSONObject updated = firstRecord("/api/admin/service/page?categoryId=" + catId, token);
        assertThat(updated.getString("name")).isEqualTo(itemName + "改");
        assertThat(updated.getBigDecimal("price")).isEqualByComparingTo("128.00");

        // 下架
        assertThat(putJson("/api/admin/service/status/" + id, Map.of("status", 0), token).getInteger("code")).isEqualTo(1);
        assertThat(firstRecord("/api/admin/service/page?categoryId=" + catId, token).getInteger("status")).isZero();

        // 下架后用户端详情不可见
        String userToken = userToken();
        JSONObject offlineDetail = getJson("/api/user/service/" + id, userToken);
        assertThat(offlineDetail.getInteger("code")).isZero();
        assertThat(offlineDetail.getString("msg")).contains("服务不存在或已下架");

        // 重新上架后可见
        assertThat(putJson("/api/admin/service/status/" + id, Map.of("status", 1), token).getInteger("code")).isEqualTo(1);
        JSONObject detail = getJson("/api/user/service/" + id, userToken);
        assertThat(detail.getInteger("code")).isEqualTo(1);
        assertThat(detail.getJSONObject("data").getString("name")).isEqualTo(itemName + "改");

        // 删除
        assertThat(deleteItem(token, id).getInteger("code")).isEqualTo(1);
        assertThat(getJson("/api/admin/service/page?categoryId=" + catId, token)
                .getJSONObject("data").getLong("total")).isZero();
    }

    @Test
    void addShouldRejectDuplicatedNameInSameCategory() {
        String token = adminToken();
        String itemName = ITEM_PREFIX + "重名服务";
        addItem(token, catId, itemName, "99.00", "猫", null, null);

        JSONObject response = postJson("/api/admin/service/add",
                Map.of("categoryId", catId, "name", itemName, "price", "199.00"), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("该分类下服务名称已存在");
    }

    @Test
    void addShouldAllowSameNameInDifferentCategory() {
        String token = adminToken();
        String itemName = ITEM_PREFIX + "跨分类同名";
        addItem(token, catId, itemName, "99.00", "猫", null, null);

        JSONObject response = postJson("/api/admin/service/add",
                Map.of("categoryId", catId2, "name", itemName, "price", "199.00"), token);

        assertThat(response.getInteger("code")).isEqualTo(1);
    }

    @Test
    void addShouldRejectNonexistentCategory() {
        String token = adminToken();

        JSONObject response = postJson("/api/admin/service/add",
                Map.of("categoryId", 99999999, "name", ITEM_PREFIX + "无分类", "price", "99.00"), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("所属分类不存在");
    }

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        String token = adminToken();
        Long id = addItem(token, catId, ITEM_PREFIX + "有未完成订单", "99.00", "猫", null, null);
        insertOrderDetail(id, 2);

        JSONObject response = deleteItem(token, id);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("未完成订单");
        assertThat(getJson("/api/admin/service/page?categoryId=" + catId, token)
                .getJSONObject("data").getLong("total")).isEqualTo(1L);
    }

    @Test
    void deleteShouldSucceedWhenOnlyFinishedOrderExists() {
        String token = adminToken();
        Long id = addItem(token, catId, ITEM_PREFIX + "只有已完成订单", "99.00", "猫", null, null);
        insertOrderDetail(id, 5);

        assertThat(deleteItem(token, id).getInteger("code")).isEqualTo(1);
    }

    @Test
    void updateAndDeleteNonexistentItemShouldFail() {
        String token = adminToken();

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", 99999999L);
        updateBody.put("categoryId", catId);
        updateBody.put("name", ITEM_PREFIX + "不存在");
        updateBody.put("price", "99.00");

        JSONObject updateResp = putJson("/api/admin/service/update", updateBody, token);
        assertThat(updateResp.getInteger("code")).isZero();
        assertThat(updateResp.getString("msg")).contains("服务项目不存在");

        JSONObject deleteResp = deleteItem(token, 99999999L);
        assertThat(deleteResp.getInteger("code")).isZero();
        assertThat(deleteResp.getString("msg")).contains("服务项目不存在");
    }

    // ==================== 用户端 ====================

    @Test
    void userPageShouldOnlyReturnOnSaleItemsWithFilters() {
        String adminToken = adminToken();
        addItem(adminToken, catId, ITEM_PREFIX + "上架猫洗", "100.00", "猫", null, 1);
        addItem(adminToken, catId, ITEM_PREFIX + "上架狗美", "200.00", "狗", null, 1);
        addItem(adminToken, catId, ITEM_PREFIX + "已下架", "150.00", "猫", null, 0);

        String userToken = userToken();

        // 只查上架
        JSONObject all = getJson("/api/user/service/page?categoryId=" + catId, userToken);
        assertThat(all.getInteger("code")).isEqualTo(1);
        assertThat(all.getJSONObject("data").getLong("total")).isEqualTo(2L);
        JSONArray records = all.getJSONObject("data").getJSONArray("records");
        assertThat(records.getJSONObject(0).containsKey("status")).isFalse();

        // 宠物类型筛选
        JSONObject byPetType = getJson("/api/user/service/page?categoryId=" + catId + "&petType=猫", userToken);
        assertThat(byPetType.getJSONObject("data").getLong("total")).isEqualTo(1L);

        // 价格区间筛选
        JSONObject byPrice = getJson("/api/user/service/page?categoryId=" + catId
                + "&minPrice=150.00&maxPrice=250.00", userToken);
        assertThat(byPrice.getJSONObject("data").getLong("total")).isEqualTo(1L);
        assertThat(byPrice.getJSONObject("data").getJSONArray("records").getJSONObject(0).getString("name"))
                .isEqualTo(ITEM_PREFIX + "上架狗美");
    }

    @Test
    void userPageShouldRejectInvalidPriceRange() {
        String userToken = userToken();

        JSONObject response = getJson("/api/user/service/page?minPrice=300.00&maxPrice=100.00", userToken);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("最低价格不能大于最高价格");
    }

    @Test
    void userDetailShouldRejectNonexistentItem() {
        String userToken = userToken();

        JSONObject response = getJson("/api/user/service/99999999", userToken);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("服务不存在或已下架");
    }

    // ==================== 参数校验 ====================

    @Test
    void addShouldRejectInvalidParam() {
        String token = adminToken();

        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", 0);
        body.put("name", "");
        body.put("price", "0");

        JSONObject response = postJson("/api/admin/service/add", body, token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
    }

    @Test
    void pageShouldRejectInvalidPageSize() {
        String token = adminToken();

        JSONObject response = getJson("/api/admin/service/page?page=1&pageSize=1000", token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("每页条数");
    }

    @Test
    void statusShouldRejectInvalidValue() {
        String token = adminToken();
        Long id = addItem(token, catId, ITEM_PREFIX + "非法状态", "99.00", "猫", null, null);

        JSONObject response = putJson("/api/admin/service/status/" + id, Map.of("status", 9), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
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

    private Long addItem(String token, Long categoryId, String name, String price, String petType,
                         Integer serviceDuration, Integer status) {
        Map<String, Object> body = new HashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("price", price);
        body.put("petType", petType);
        if (serviceDuration != null) {
            body.put("serviceDuration", serviceDuration);
        }
        if (status != null) {
            body.put("status", status);
        }
        JSONObject response = postJson("/api/admin/service/add", body, token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONObject deleteItem(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange("/api/admin/service/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
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

    private Long insertCategory(String name) {
        jdbcTemplate.update("INSERT INTO service_category (name, sort, status, create_time, update_time) "
                + "VALUES (?, ?, ?, NOW(), NOW())", name, 0, 1);
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?", Long.class, name);
    }

    private void insertOrderDetail(Long serviceId, int orderStatus) {
        String orderNumber = ORDER_PREFIX + System.nanoTime();
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                orderNumber, 1L, new BigDecimal("99.00"), orderStatus);
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM service_order WHERE order_number = ?",
                Long.class, orderNumber);
        jdbcTemplate.update("INSERT INTO order_detail (order_id, service_id, name, price, quantity, amount, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                orderId, serviceId, "IT-订单快照", new BigDecimal("99.00"), 1, new BigDecimal("99.00"));
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_detail WHERE order_id IN "
                + "(SELECT id FROM service_order WHERE order_number LIKE ?)", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_order WHERE order_number LIKE ?", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name LIKE ?)", CAT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_item WHERE name LIKE ?", ITEM_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_category WHERE name LIKE ?", CAT_PREFIX + "%");
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
