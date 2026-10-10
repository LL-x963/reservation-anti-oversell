package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
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
 * 宠物档案接口集成测试（用户端 /api/user/pet）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PetProfileApiTest {

    private static final String OPENID_PREFIX = "it_pet_user_";
    private static final String OPENID_A = OPENID_PREFIX + "a";
    private static final String OPENID_B = OPENID_PREFIX + "b";
    private static final String PET_PREFIX = "IT-宠物-";
    private static final String ORDER_PREFIX = "IT-PET-ORD-";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    private final Map<String, Long> userIds = new HashMap<>();

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
    void petEndpointsShouldRequireUserToken() {
        assertThat(restTemplate.getForEntity("/api/user/pet/list", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.getForEntity("/api/user/pet/1", String.class).getStatusCode().value())
                .isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/user/pet/add", Map.of("name", PET_PREFIX + "x"), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/user/pet/update", HttpMethod.PUT,
                new HttpEntity<>(Map.of("id", 1, "name", PET_PREFIX + "x")), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/user/pet/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    // ==================== CRUD ====================

    @Test
    void crudFlowShouldWork() {
        String token = tokenFor(OPENID_A);
        String name = PET_PREFIX + "旺财";

        // 新增
        Long id = addPet(token, name, "金毛", 1, "2021-05-01", "59.90");
        assertThat(id).isNotNull().isPositive();

        // 详情
        JSONObject detail = getJson("/api/user/pet/" + id, token);
        assertThat(detail.getInteger("code")).isEqualTo(1);
        JSONObject data = detail.getJSONObject("data");
        assertThat(data.getString("name")).isEqualTo(name);
        assertThat(data.getString("breed")).isEqualTo("金毛");
        assertThat(data.getInteger("gender")).isEqualTo(1);
        assertThat(data.getString("birthday")).isEqualTo("2021-05-01");
        assertThat(data.getBigDecimal("weight")).isEqualByComparingTo("59.90");

        // 列表
        assertThat(names(listFor(token))).contains(name);

        // 修改
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", id);
        updateBody.put("name", name + "改名");
        updateBody.put("breed", "柯基");
        updateBody.put("gender", 2);
        updateBody.put("birthday", "2022-03-08");
        updateBody.put("weight", "12.50");
        updateBody.put("vaccine", "已接种三针");
        assertThat(putJson("/api/user/pet/update", updateBody, token).getInteger("code")).isEqualTo(1);

        JSONObject updated = getJson("/api/user/pet/" + id, token).getJSONObject("data");
        assertThat(updated.getString("name")).isEqualTo(name + "改名");
        assertThat(updated.getString("breed")).isEqualTo("柯基");
        assertThat(updated.getInteger("gender")).isEqualTo(2);
        assertThat(updated.getString("birthday")).isEqualTo("2022-03-08");

        // 删除
        assertThat(deletePet(token, id).getInteger("code")).isEqualTo(1);
        assertThat(getJson("/api/user/pet/" + id, token).getInteger("code")).isZero();
    }

    @Test
    void listShouldOnlyReturnCurrentUserPets() {
        String tokenA = tokenFor(OPENID_A);
        String tokenB = tokenFor(OPENID_B);

        addPet(tokenA, PET_PREFIX + "A的狗", null, null, null, null);
        addPet(tokenB, PET_PREFIX + "B的猫", null, null, null, null);

        assertThat(names(listFor(tokenA))).containsExactly(PET_PREFIX + "A的狗");
        assertThat(names(listFor(tokenB))).containsExactly(PET_PREFIX + "B的猫");
    }

    // ==================== 业务规则 ====================

    @Test
    void addShouldRejectDuplicatedNameInSameUser() {
        String token = tokenFor(OPENID_A);
        String name = PET_PREFIX + "重名";
        addPet(token, name, null, null, null, null);

        JSONObject response = postJson("/api/user/pet/add", Map.of("name", name), token);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("宠物名称已存在");
    }

    @Test
    void addShouldAllowSameNameForDifferentUser() {
        String tokenA = tokenFor(OPENID_A);
        String tokenB = tokenFor(OPENID_B);
        String name = PET_PREFIX + "跨用户同名";
        addPet(tokenA, name, null, null, null, null);

        JSONObject response = postJson("/api/user/pet/add", Map.of("name", name), tokenB);

        assertThat(response.getInteger("code")).isEqualTo(1);
    }

    @Test
    void otherUsersPetShouldBeInaccessible() {
        String tokenA = tokenFor(OPENID_A);
        String tokenB = tokenFor(OPENID_B);
        Long petId = addPet(tokenA, PET_PREFIX + "A的宠物", null, null, null, null);

        // B 的列表里看不到 A 的宠物
        assertThat(names(listFor(tokenB))).doesNotContain(PET_PREFIX + "A的宠物");

        // B 不能查看/修改/删除 A 的宠物
        JSONObject detail = getJson("/api/user/pet/" + petId, tokenB);
        assertThat(detail.getInteger("code")).isZero();
        assertThat(detail.getString("msg")).isEqualTo("宠物不存在");

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("id", petId);
        updateBody.put("name", PET_PREFIX + "越权改名");
        JSONObject update = putJson("/api/user/pet/update", updateBody, tokenB);
        assertThat(update.getInteger("code")).isZero();
        assertThat(update.getString("msg")).isEqualTo("宠物不存在");

        JSONObject delete = deletePet(tokenB, petId);
        assertThat(delete.getInteger("code")).isZero();
        assertThat(delete.getString("msg")).isEqualTo("宠物不存在");

        // A 仍然能看到且未被改动
        assertThat(getJson("/api/user/pet/" + petId, tokenA).getJSONObject("data").getString("name"))
                .isEqualTo(PET_PREFIX + "A的宠物");
    }

    @Test
    void deleteShouldRejectWhenUnfinishedOrderExists() {
        String token = tokenFor(OPENID_A);
        Long petId = addPet(token, PET_PREFIX + "有未完成订单", null, null, null, null);
        insertOrder(petId, 2);

        JSONObject response = deletePet(token, petId);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("未完成订单");
        assertThat(getJson("/api/user/pet/" + petId, token).getInteger("code")).isEqualTo(1);
    }

    @Test
    void deleteShouldSucceedWhenOnlyFinishedOrderExists() {
        String token = tokenFor(OPENID_A);
        Long petId = addPet(token, PET_PREFIX + "只有已完成订单", null, null, null, null);
        insertOrder(petId, 4);

        assertThat(deletePet(token, petId).getInteger("code")).isEqualTo(1);
    }

    // ==================== 参数校验 ====================

    @Test
    void addShouldRejectInvalidParam() {
        String token = tokenFor(OPENID_A);

        assertThat(postJson("/api/user/pet/add", Map.of("name", ""), token).getInteger("code")).isZero();

        Map<String, Object> invalidGender = new HashMap<>();
        invalidGender.put("name", PET_PREFIX + "非法性别");
        invalidGender.put("gender", 5);
        assertThat(postJson("/api/user/pet/add", invalidGender, token).getInteger("code")).isZero();

        Map<String, Object> futureBirthday = new HashMap<>();
        futureBirthday.put("name", PET_PREFIX + "未来生日");
        futureBirthday.put("birthday", "2999-01-01");
        JSONObject birthdayResponse = postJson("/api/user/pet/add", futureBirthday, token);
        assertThat(birthdayResponse.getInteger("code")).isZero();
        assertThat(birthdayResponse.getString("msg")).contains("生日");

        Map<String, Object> invalidWeight = new HashMap<>();
        invalidWeight.put("name", PET_PREFIX + "非法体重");
        invalidWeight.put("weight", "0");
        assertThat(postJson("/api/user/pet/add", invalidWeight, token).getInteger("code")).isZero();
    }

    @Test
    void pathIdShouldBeValidated() {
        String token = tokenFor(OPENID_A);

        assertThat(getJson("/api/user/pet/0", token).getInteger("code")).isZero();
        assertThat(deletePet(token, 0L).getInteger("code")).isZero();
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

    private Long addPet(String token, String name, String breed, Integer gender, String birthday, String weight) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (breed != null) {
            body.put("breed", breed);
        }
        if (gender != null) {
            body.put("gender", gender);
        }
        if (birthday != null) {
            body.put("birthday", birthday);
        }
        if (weight != null) {
            body.put("weight", weight);
        }
        JSONObject response = postJson("/api/user/pet/add", body, token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONObject deletePet(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange("/api/user/pet/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONArray listFor(String token) {
        JSONObject response = getJson("/api/user/pet/list", token);
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

    private List<String> names(JSONArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getJSONObject(i).getString("name"));
        }
        return result;
    }

    private void insertOrder(Long petId, int orderStatus) {
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, pet_id, total_amount, status, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                ORDER_PREFIX + System.nanoTime(), 1L, petId, new BigDecimal("99.00"), orderStatus);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM pet_profile WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?)", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM pet_profile WHERE name LIKE ?", PET_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_order WHERE order_number LIKE ?", ORDER_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `user` WHERE openid LIKE ?", OPENID_PREFIX + "%");

        userIds.values().forEach(userId ->
                redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId)));
        userIds.clear();
    }

}
