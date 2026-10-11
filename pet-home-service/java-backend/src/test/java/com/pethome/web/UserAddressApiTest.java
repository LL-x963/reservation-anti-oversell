package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户端地址簿接口测试（H5 提交预约页选择服务地址的数据来源）
 *
 * <p>需要本地 MySQL（pet_home 库）、Redis、RabbitMQ 均可用，且 pet_home.sql 的演示账号种子数据已导入。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAddressApiTest {

    /** pet_home.sql 种子的演示账号 */
    private static final String DEMO_PHONE = "13800138000";
    private static final String DEMO_PASSWORD = "123456";
    private static final String DEMO_CONSIGNEE = "演示用户";
    private static final String DEMO_FULL_ADDRESS = "浙江省杭州市西湖区文一西路 100 号 1 单元 101 室";

    /** 模拟“另一个用户”：手机号登录会自动注册，用例结束后清理 */
    private static final String NEW_PHONE = "13900003333";
    private static final String NEW_PASSWORD = "abc123456";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtil redisUtil;

    /** 本次登录过的用户 id，用于清理 Redis 中的 token */
    private final List<Long> loggedUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getPhone, NEW_PHONE));
    }

    @AfterEach
    void tearDown() {
        loggedUserIds.forEach(userId ->
                redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId)));
        loggedUserIds.clear();
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getPhone, NEW_PHONE));
    }

    @Test
    void addressListShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/user/address/list", String.class)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void demoAccountShouldHaveSeededAddress() {
        String token = login(DEMO_PHONE, DEMO_PASSWORD);

        JSONObject response = getJson("/api/user/address/list", token);
        assertThat(response.getInteger("code")).isEqualTo(1);

        JSONArray data = response.getJSONArray("data");
        assertThat(data).isNotEmpty();

        JSONObject first = data.getJSONObject(0);
        assertThat(first.getString("consignee")).isEqualTo(DEMO_CONSIGNEE);
        assertThat(first.getString("phone")).isEqualTo(DEMO_PHONE);
        // 完整地址由「省 + 市 + 区 + 详细地址」拼接而成
        assertThat(first.getString("fullAddress")).isEqualTo(DEMO_FULL_ADDRESS);
        assertThat(first.getInteger("isDefault")).isEqualTo(1);
        assertThat(first.getLong("id")).isNotNull();
    }

    @Test
    void addressListShouldOnlyReturnOwnAddresses() {
        // 新用户自动注册后没有任何地址，也不应看到别人的地址
        String token = login(NEW_PHONE, NEW_PASSWORD);

        JSONObject response = getJson("/api/user/address/list", token);

        assertThat(response.getInteger("code")).isEqualTo(1);
        assertThat(response.getJSONArray("data")).isEmpty();
    }

    // ==================== 辅助方法 ====================

    private String login(String phone, String password) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("phone", phone, "password", password), String.class);
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

}
