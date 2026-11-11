package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.entity.Employee;
import com.pethome.entity.User;
import com.pethome.mapper.EmployeeMapper;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录 + 鉴权端到端集成测试
 *
 * <p>需要本地 MySQL（pet_home 库）、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIntegrationTest {

    private static final String USER_OPENID = "it_auth_flow_openid";
    private static final String EMP_USERNAME = "it_auth_flow_admin";
    private static final String EMP_PASSWORD = "123456";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    private Employee employee;

    /** 本次测试创建的 C 端用户 id，用于清理 */
    private Long createdUserId;

    @BeforeEach
    void setUp() {
        employeeMapper.delete(new LambdaQueryWrapper<Employee>().eq(Employee::getUsername, EMP_USERNAME));
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getOpenid, USER_OPENID));

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("集成测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employee.setRole("ADMIN");
        employeeMapper.insert(employee);
    }

    @AfterEach
    void tearDown() {
        if (createdUserId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, createdUserId));
        }
        redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.EMPLOYEE, employee.getId()));
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getOpenid, USER_OPENID));
        employeeMapper.deleteById(employee.getId());
    }

    @Test
    void requestWithoutTokenShouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/user/profile", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(0);
        assertThat(body.getString("msg")).contains("未登录");
    }

    @Test
    void requestWithInvalidTokenShouldReturn401() {
        ResponseEntity<String> response = restTemplate.exchange("/api/user/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders("invalid.token.value")), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.parseObject(response.getBody()).getInteger("code")).isEqualTo(0);
    }

    @Test
    void userLoginFlowShouldWorkEndToEnd() {
        // 1. openid 登录
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", USER_OPENID), String.class);
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);

        JSONObject loginBody = JSON.parseObject(loginResponse.getBody());
        assertThat(loginBody.getInteger("code")).isEqualTo(1);

        JSONObject loginData = loginBody.getJSONObject("data");
        String token = loginData.getString("token");
        createdUserId = loginData.getLong("userId");
        assertThat(token).isNotBlank();
        assertThat(createdUserId).isNotNull();

        // 2. 携带 token 访问受保护接口
        ResponseEntity<String> profileResponse = restTemplate.exchange("/api/user/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(profileResponse.getStatusCode().value()).isEqualTo(200);

        JSONObject profileBody = JSON.parseObject(profileResponse.getBody());
        assertThat(profileBody.getInteger("code")).isEqualTo(1);
        assertThat(profileBody.getJSONObject("data").getLong("id")).isEqualTo(createdUserId);

        // 3. C 端 token 访问管理端接口应被拒绝（身份不匹配）
        ResponseEntity<String> crossResponse = restTemplate.exchange("/api/admin/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(crossResponse.getStatusCode().value()).isEqualTo(401);

        // 4. 退出登录后 token 立即失效
        ResponseEntity<String> logoutResponse = restTemplate.exchange("/api/user/logout", HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(JSON.parseObject(logoutResponse.getBody()).getInteger("code")).isEqualTo(1);

        ResponseEntity<String> afterLogoutResponse = restTemplate.exchange("/api/user/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(afterLogoutResponse.getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.parseObject(afterLogoutResponse.getBody()).getString("msg")).contains("登录状态已失效");
    }

    @Test
    void employeeLoginShouldRejectWrongPassword() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", "wrong-password"), String.class);

        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(0);
        assertThat(body.getString("msg")).isEqualTo("用户名或密码错误");
    }

    @Test
    void employeeLoginFlowShouldWorkEndToEnd() {
        // 1. 账号密码登录
        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), String.class);

        JSONObject loginBody = JSON.parseObject(loginResponse.getBody());
        assertThat(loginBody.getInteger("code")).isEqualTo(1);

        String token = loginBody.getJSONObject("data").getString("token");
        assertThat(token).isNotBlank();

        // 2. 访问管理端受保护接口
        ResponseEntity<String> profileResponse = restTemplate.exchange("/api/admin/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(profileResponse.getStatusCode().value()).isEqualTo(200);

        JSONObject profileBody = JSON.parseObject(profileResponse.getBody());
        assertThat(profileBody.getInteger("code")).isEqualTo(1);
        assertThat(profileBody.getJSONObject("data").getString("username")).isEqualTo(EMP_USERNAME);
        // 不能返回密码字段
        assertThat(profileBody.getJSONObject("data").containsKey("password")).isFalse();

        // 3. 退出登录后 token 立即失效
        restTemplate.exchange("/api/admin/logout", HttpMethod.POST, new HttpEntity<>(authHeaders(token)), String.class);
        ResponseEntity<String> afterLogoutResponse = restTemplate.exchange("/api/admin/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        assertThat(afterLogoutResponse.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginParamValidationShouldReturnResultError() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", ""), String.class);

        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(0);
        assertThat(body.getString("msg")).contains("openid");
    }

    @Test
    void unknownPathShouldReturnUnifiedResult() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/not-exists", String.class);

        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(0);
        assertThat(body.getString("msg")).isNotBlank();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

}
