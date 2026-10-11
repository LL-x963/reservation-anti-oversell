package com.pethome.web;

import com.alibaba.fastjson2.JSON;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 端手机号 + 密码登录接口测试
 *
 * <p>覆盖范围：</p>
 * <ul>
 *   <li>pet_home.sql 种子演示账号 13800138000 / 123456 可登录且密码为 BCrypt 密文（回归）；</li>
 *   <li>手机号未注册时自动注册（密码即注册密码），重复登录不会重复建号；</li>
 *   <li>密码错误、手机号格式、密码长度、两种登录方式互斥等校验；</li>
 *   <li>原有 openid 登录方式不受改造影响。</li>
 * </ul>
 *
 * <p>需要本地 MySQL（pet_home 库）、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserLoginApiTest {

    /** pet_home.sql 种子演示账号 */
    private static final String DEMO_PHONE = "13800138000";
    private static final String DEMO_PASSWORD = "123456";
    private static final String DEMO_NAME = "演示用户";

    /** 自动注册用例使用的手机号（用例前后清理，保证可重复执行） */
    private static final String NEW_PHONE = "13900001111";
    private static final String NEW_PASSWORD = "abc123456";

    /** openid 回归用例使用 */
    private static final String OPENID = "it_user_login_openid";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    /** 本次测试登录过的用户 id，用于清理 Redis 中的登录 token */
    private final List<Long> loggedUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanTestUsers();
    }

    @AfterEach
    void tearDown() {
        loggedUserIds.forEach(userId ->
                redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId)));
        loggedUserIds.clear();
        cleanTestUsers();
    }

    // ==================== 演示账号（种子数据） ====================

    @Test
    void demoAccountShouldLoginByPhoneAndPassword() {
        JSONObject data = loginOk(Map.of("phone", DEMO_PHONE, "password", DEMO_PASSWORD));

        assertThat(data.getString("token")).isNotBlank();
        assertThat(data.getString("name")).isEqualTo(DEMO_NAME);
        assertThat(data.getInteger("userType")).isEqualTo(1);

        // 登录后可用该 token 访问受保护接口
        JSONObject profile = JSON.parseObject(restTemplate.exchange("/api/user/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(data.getString("token"))), String.class).getBody());
        assertThat(profile.getInteger("code")).isEqualTo(1);
        assertThat(profile.getJSONObject("data").getString("phone")).isEqualTo(DEMO_PHONE);
        // 响应不得泄露密码字段
        assertThat(profile.getJSONObject("data").containsKey("password")).isFalse();
    }

    @Test
    void demoAccountPasswordShouldBeBcryptEncoded() {
        String password = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, DEMO_PHONE)).getPassword();

        assertThat(password).as("演示账号密码应为 BCrypt 密文").startsWith("$2");
        assertThat(passwordEncoder.matches(DEMO_PASSWORD, password)).isTrue();
    }

    @Test
    void wrongPasswordShouldBeRejected() {
        JSONObject result = postLogin(Map.of("phone", DEMO_PHONE, "password", "wrong-password"));

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("手机号或密码错误");
    }

    // ==================== 手机号自动注册 ====================

    @Test
    void unregisteredPhoneShouldAutoRegister() {
        JSONObject data = loginOk(Map.of("phone", NEW_PHONE, "password", NEW_PASSWORD));
        Long userId = data.getLong("userId");

        User created = userMapper.selectById(userId);
        assertThat(created.getPhone()).isEqualTo(NEW_PHONE);
        // 手机号注册的账号不绑定微信
        assertThat(created.getOpenid()).isNull();
        // 密码必须是 BCrypt 密文且不等于明文
        assertThat(created.getPassword()).startsWith("$2").isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, created.getPassword())).isTrue();

        // 同一手机号再次登录：复用该账号，不会重复建号
        JSONObject again = loginOk(Map.of("phone", NEW_PHONE, "password", NEW_PASSWORD));
        assertThat(again.getLong("userId")).isEqualTo(userId);
        assertThat(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, NEW_PHONE))).isEqualTo(1L);
    }

    // ==================== 参数校验 ====================

    @Test
    void invalidPhoneFormatShouldBeRejected() {
        JSONObject result = postLogin(Map.of("phone", "12345", "password", DEMO_PASSWORD));

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("手机号格式不正确");
    }

    @Test
    void tooShortPasswordShouldBeRejected() {
        JSONObject result = postLogin(Map.of("phone", NEW_PHONE, "password", "123"));

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("密码长度");
    }

    @Test
    void missingPasswordShouldBeRejected() {
        JSONObject result = postLogin(Map.of("phone", NEW_PHONE));

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("请输入密码");
    }

    @Test
    void bothLoginModesShouldBeRejected() {
        JSONObject result = postLogin(Map.of(
                "openid", OPENID, "phone", DEMO_PHONE, "password", DEMO_PASSWORD));

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("二选一");
    }

    @Test
    void emptyRequestShouldBeRejected() {
        JSONObject result = postLogin(Map.of());

        assertThat(result.getInteger("code")).isZero();
        assertThat(result.getString("msg")).contains("请传入");
    }

    // ==================== openid 登录回归 ====================

    @Test
    void openidLoginShouldStillWork() {
        JSONObject data = loginOk(Map.of("openid", OPENID));

        assertThat(data.getString("token")).isNotBlank();
        assertThat(data.getInteger("userType")).isEqualTo(1);

        // 自动注册的用户没有密码（未绑定手机号）
        User created = userMapper.selectById(data.getLong("userId"));
        assertThat(created.getOpenid()).isEqualTo(OPENID);
        assertThat(created.getPassword()).isNull();
    }

    // ==================== 辅助方法 ====================

    /**
     * 调用登录接口，返回完整响应体
     */
    private JSONObject postLogin(Map<String, Object> body) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login", body, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return JSON.parseObject(response.getBody());
    }

    /**
     * 调用登录接口并断言成功，返回 data 并登记用户 id 以便清理 token
     */
    private JSONObject loginOk(Map<String, Object> body) {
        JSONObject result = postLogin(body);
        assertThat(result.getInteger("code")).as("登录应成功: %s", result.getString("msg")).isEqualTo(1);
        JSONObject data = result.getJSONObject("data");
        loggedUserIds.add(data.getLong("userId"));
        return data;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return headers;
    }

    /**
     * 清理用例产生的用户（自动注册的手机号 + openid 回归用户）
     */
    private void cleanTestUsers() {
        userMapper.delete(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, NEW_PHONE).or().eq(User::getOpenid, OPENID));
    }

}
