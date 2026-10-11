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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户端美容师接口测试（H5 提交预约页选择美容师的数据来源）
 *
 * <p>需要本地 MySQL（pet_home 库）、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserBeauticianApiTest {

    private static final String DEMO_PHONE = "13800138000";
    private static final String DEMO_PASSWORD = "123456";

    /** 用例自建的美容师名（一个在职、一个离职），结束时清理 */
    private static final String ON_DUTY_NAME = "IT-用户端美容师-在职";
    private static final String OFF_DUTY_NAME = "IT-用户端美容师-离职";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    private final List<Long> loggedUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanBeauticians();
    }

    @AfterEach
    void tearDown() {
        loggedUserIds.forEach(userId ->
                redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId)));
        loggedUserIds.clear();
        cleanBeauticians();
    }

    @Test
    void beauticianListShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/user/beautician/list", String.class)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void beauticianListShouldReturnOnDutyAndHidePrivateFields() {
        insertBeautician(ON_DUTY_NAME, 1);
        insertBeautician(OFF_DUTY_NAME, 0);

        JSONObject response = getJson("/api/user/beautician/list", login());
        assertThat(response.getInteger("code")).isEqualTo(1);

        JSONArray data = response.getJSONArray("data");
        assertThat(data).isNotEmpty();

        Set<String> names = new HashSet<>();
        data.forEach(item -> names.add(((JSONObject) item).getString("name")));
        assertThat(names).contains(ON_DUTY_NAME).doesNotContain(OFF_DUTY_NAME);

        // 只暴露选择所需字段，手机号等隐私信息不外泄
        JSONObject first = data.getJSONObject(0);
        assertThat(first.containsKey("id")).isTrue();
        assertThat(first.containsKey("name")).isTrue();
        assertThat(first.containsKey("score")).isTrue();
        assertThat(first.containsKey("phone")).isFalse();
        assertThat(first.containsKey("idNumber")).isFalse();
        assertThat(first.containsKey("skillTags")).isFalse();
    }

    @Test
    void beauticianListShouldMatchOnDutyRowsInDatabase() {
        JSONObject response = getJson("/api/user/beautician/list", login());

        Set<Long> returnedIds = new HashSet<>();
        response.getJSONArray("data").forEach(item -> returnedIds.add(((JSONObject) item).getLong("id")));

        List<Long> onDutyIds = jdbcTemplate.queryForList(
                "SELECT id FROM beautician WHERE status = 1", Long.class);

        // 在职的全部返回、且不包含任何离职美容师
        assertThat(returnedIds).containsExactlyInAnyOrderElementsOf(new HashSet<>(onDutyIds));
    }

    // ==================== 辅助方法 ====================

    private String login() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("phone", DEMO_PHONE, "password", DEMO_PASSWORD), String.class);
        JSONObject data = JSON.parseObject(response.getBody()).getJSONObject("data");
        loggedUserIds.add(data.getLong("userId"));
        return data.getString("token");
    }

    private JSONObject getJson(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return JSON.parseObject(response.getBody());
    }

    private void insertBeautician(String name, int status) {
        jdbcTemplate.update("INSERT INTO beautician (name, score, status, create_time, update_time)"
                + " VALUES (?, ?, ?, NOW(), NOW())", name, new BigDecimal("4.80"), status);
    }

    private void cleanBeauticians() {
        jdbcTemplate.update("DELETE FROM beautician WHERE name IN (?, ?)", ON_DUTY_NAME, OFF_DUTY_NAME);
    }

}
