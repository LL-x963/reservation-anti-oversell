package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 种子管理员账号可用性测试
 *
 * <p>pet_home.sql 初始化的 admin 账号密码必须是 BCrypt 密文：登录走的是
 * {@link BCryptPasswordEncoder#matches}，若脚本写成 MD5 则管理端登录必然失败
 * （历史上此处误用 MD5，导致 admin/123456 无法登录，故补充本回归用例）。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminSeedLoginTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "123456";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    private Long adminId;

    @AfterEach
    void tearDown() {
        if (adminId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.EMPLOYEE, adminId));
            adminId = null;
        }
    }

    @Test
    void seedAdminPasswordShouldBeBcryptEncoded() {
        String password = jdbcTemplate.queryForObject(
                "SELECT password FROM employee WHERE username = ?", String.class, ADMIN_USERNAME);

        assertThat(password).as("admin 密码应为 BCrypt 密文").startsWith("$2");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, password)).isTrue();
    }

    @Test
    void seedAdminShouldLoginAndAccessProtectedApi() {
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD), String.class);

        JSONObject body = JSON.parseObject(loginResponse.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        JSONObject data = body.getJSONObject("data");
        assertThat(data.getString("token")).isNotBlank();
        adminId = data.getLong("userId");

        // 用该 token 访问受保护接口（前端登录后立即会调 /api/admin/profile 顶栏展示姓名）
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + data.getString("token"));
        JSONObject profile = JSON.parseObject(restTemplate.exchange("/api/admin/profile", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getBody());

        assertThat(profile.getInteger("code")).isEqualTo(1);
        assertThat(profile.getJSONObject("data").getString("username")).isEqualTo(ADMIN_USERNAME);
        assertThat(profile.getJSONObject("data").getString("name")).isEqualTo("超级管理员");
    }

}
