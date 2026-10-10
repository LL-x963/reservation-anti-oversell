package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约车接口集成测试（用户端 /api/user/cart）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartApiTest {

    private static final String OPENID_PREFIX = "it_cart_user_";
    private static final String OPENID_A = OPENID_PREFIX + "a";
    private static final String OPENID_B = OPENID_PREFIX + "b";
    private static final String CATEGORY_NAME = "IT-购物车接口-分类";
    private static final String SERVICE_A = "IT-购物车接口-服务A";
    private static final String SERVICE_B = "IT-购物车接口-服务B";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    private Long serviceAId;
    private Long serviceBId;
    private Long offlineServiceId;

    private final Map<String, Long> userIds = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        Long categoryId = insertCategory(CATEGORY_NAME);
        serviceAId = insertServiceItem(categoryId, SERVICE_A, "99.00", "/upload/it-a.png", 1);
        serviceBId = insertServiceItem(categoryId, SERVICE_B, "199.50", "/upload/it-b.png", 1);
        offlineServiceId = insertServiceItem(categoryId, SERVICE_A + "下架", "50.00", null, 0);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void cartEndpointsShouldRequireUserToken() {
        assertThat(restTemplate.postForEntity("/api/user/cart/add",
                Map.of("serviceId", 1, "quantity", 1), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.getForEntity("/api/user/cart/list", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.exchange("/api/user/cart/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("serviceId", 1, "quantity", 1)), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/user/cart/delete?serviceId=1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/user/cart/clean", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 全流程 ====================

    @Test
    void cartFlowShouldWork() {
        String token = tokenFor(OPENID_A);

        // 添加
        assertThat(addToCart(token, serviceAId, 2).getInteger("code")).isEqualTo(1);

        // 列表含服务详情与小计
        JSONArray list = listFor(token);
        assertThat(list).hasSize(1);
        JSONObject item = list.getJSONObject(0);
        assertThat(item.getLong("serviceId")).isEqualTo(serviceAId);
        assertThat(item.getString("name")).isEqualTo(SERVICE_A);
        assertThat(item.getBigDecimal("price")).isEqualByComparingTo("99.00");
        assertThat(item.getString("image")).isEqualTo("/upload/it-a.png");
        assertThat(item.getInteger("quantity")).isEqualTo(2);
        assertThat(item.getBigDecimal("amount")).isEqualByComparingTo("198.00");

        // 重复添加累加
        assertThat(addToCart(token, serviceAId, 3).getInteger("code")).isEqualTo(1);
        assertThat(listFor(token).getJSONObject(0).getInteger("quantity")).isEqualTo(5);

        // 修改数量（覆盖）
        assertThat(updateCart(token, serviceAId, 1).getInteger("code")).isEqualTo(1);
        assertThat(listFor(token).getJSONObject(0).getInteger("quantity")).isEqualTo(1);

        // 再添加另一服务并删除其中之一
        assertThat(addToCart(token, serviceBId, 1).getInteger("code")).isEqualTo(1);
        assertThat(deleteCart(token, serviceAId).getInteger("code")).isEqualTo(1);
        assertThat(listFor(token)).hasSize(1);
        assertThat(listFor(token).getJSONObject(0).getLong("serviceId")).isEqualTo(serviceBId);

        // 清空
        assertThat(cleanCart(token).getInteger("code")).isEqualTo(1);
        assertThat(listFor(token)).isEmpty();
    }

    @Test
    void cartShouldBeIsolatedBetweenUsers() {
        String tokenA = tokenFor(OPENID_A);
        String tokenB = tokenFor(OPENID_B);

        addToCart(tokenA, serviceAId, 1);

        assertThat(listFor(tokenB)).isEmpty();

        addToCart(tokenB, serviceBId, 2);
        assertThat(listFor(tokenB)).hasSize(1);
        assertThat(listFor(tokenA)).hasSize(1);
        assertThat(listFor(tokenA).getJSONObject(0).getLong("serviceId")).isEqualTo(serviceAId);
    }

    @Test
    void listShouldAutoCleanOfflineService() {
        String token = tokenFor(OPENID_A);
        addToCart(token, serviceAId, 1);
        addToCart(token, serviceBId, 1);

        jdbcTemplate.update("UPDATE service_item SET status = 0 WHERE id = ?", serviceAId);

        JSONArray list = listFor(token);
        assertThat(list).hasSize(1);
        assertThat(list.getJSONObject(0).getLong("serviceId")).isEqualTo(serviceBId);
        assertThat(redisUtil.hHasKey(RedisKeyConstant.cartKey(userIds.get(OPENID_A)),
                String.valueOf(serviceAId))).isFalse();
    }

    // ==================== 业务规则 ====================

    @Test
    void addShouldRejectNonexistentOrOfflineService() {
        String token = tokenFor(OPENID_A);

        JSONObject nonexistent = addToCart(token, 99999999L, 1);
        assertThat(nonexistent.getInteger("code")).isZero();
        assertThat(nonexistent.getString("msg")).contains("服务不存在");

        JSONObject offline = addToCart(token, offlineServiceId, 1);
        assertThat(offline.getInteger("code")).isZero();
        assertThat(offline.getString("msg")).contains("服务已下架");
    }

    @Test
    void updateAndDeleteShouldRejectServiceNotInCart() {
        String token = tokenFor(OPENID_A);

        JSONObject update = updateCart(token, serviceAId, 2);
        assertThat(update.getInteger("code")).isZero();
        assertThat(update.getString("msg")).contains("预约车中不存在该服务");

        JSONObject delete = deleteCart(token, serviceAId);
        assertThat(delete.getInteger("code")).isZero();
        assertThat(delete.getString("msg")).contains("预约车中不存在该服务");
    }

    @Test
    void addShouldRejectExceedingMaxQuantity() {
        String token = tokenFor(OPENID_A);
        addToCart(token, serviceAId, 99);

        JSONObject response = addToCart(token, serviceAId, 1);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("单个服务数量不能超过99");
    }

    // ==================== 参数校验 ====================

    @Test
    void addShouldRejectInvalidParam() {
        String token = tokenFor(OPENID_A);

        Map<String, Object> zeroQuantity = new HashMap<>();
        zeroQuantity.put("serviceId", serviceAId);
        zeroQuantity.put("quantity", 0);
        JSONObject zero = postJson("/api/user/cart/add", zeroQuantity, token);
        assertThat(zero.getInteger("code")).isZero();
        assertThat(zero.getString("msg")).contains("quantity");

        Map<String, Object> tooMany = new HashMap<>();
        tooMany.put("serviceId", serviceAId);
        tooMany.put("quantity", 100);
        assertThat(postJson("/api/user/cart/add", tooMany, token).getInteger("code")).isZero();

        assertThat(postJson("/api/user/cart/add", Map.of("quantity", 1), token).getInteger("code")).isZero();
    }

    @Test
    void deleteShouldRejectMissingOrInvalidServiceId() {
        String token = tokenFor(OPENID_A);

        ResponseEntity<String> missing = restTemplate.exchange("/api/user/cart/delete", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(JSON.parseObject(missing.getBody()).getInteger("code")).isZero();

        ResponseEntity<String> invalid = restTemplate.exchange("/api/user/cart/delete?serviceId=0", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(JSON.parseObject(invalid.getBody()).getInteger("code")).isZero();
    }

    // ==================== 辅助方法 ====================

    private String tokenFor(String openid) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", openid), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        JSONObject data = body.getJSONObject("data");
        userIds.put(openid, data.getLong("userId"));
        return data.getString("token");
    }

    private JSONObject addToCart(String token, Long serviceId, Integer quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("serviceId", serviceId);
        body.put("quantity", quantity);
        return postJson("/api/user/cart/add", body, token);
    }

    private JSONObject updateCart(String token, Long serviceId, Integer quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("serviceId", serviceId);
        body.put("quantity", quantity);
        return putJson("/api/user/cart/update", body, token);
    }

    private JSONObject deleteCart(String token, Long serviceId) {
        ResponseEntity<String> response = restTemplate.exchange("/api/user/cart/delete?serviceId=" + serviceId,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONObject cleanCart(String token) {
        ResponseEntity<String> response = restTemplate.exchange("/api/user/cart/clean",
                HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONArray listFor(String token) {
        ResponseEntity<String> response = restTemplate.exchange("/api/user/cart/list", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONArray("data");
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

    private Long insertServiceItem(Long categoryId, String name, String price, String image, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, image, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", categoryId, name, new BigDecimal(price), image, status);
        return jdbcTemplate.queryForObject("SELECT id FROM service_item WHERE category_id = ? AND name = ?",
                Long.class, categoryId, name);
    }

    private void cleanUp() {
        List<Long> ids = new ArrayList<>(userIds.values());
        userIds.clear();
        ids.forEach(userId -> redisUtil.delete(RedisKeyConstant.cartKey(userId)));

        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name = ?)", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM service_category WHERE name = ?", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM `user` WHERE openid LIKE ?", OPENID_PREFIX + "%");
    }

}
