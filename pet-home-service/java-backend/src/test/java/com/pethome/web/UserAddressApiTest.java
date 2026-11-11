package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.entity.AddressBook;
import com.pethome.entity.User;
import com.pethome.mapper.AddressBookMapper;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** 模拟“另一个用户”：先注册再登录，用例结束后清理 */
    private static final String NEW_PHONE = "13900003333";
    private static final String NEW_PASSWORD = "abc123456";

    /** 测试地址的联系电话与拼接后的完整地址 */
    private static final String TEST_PHONE = "13900001111";
    private static final String TEST_FULL_ADDRESS = "广东省深圳市南山区科技园 1 号";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private RedisUtil redisUtil;

    /** 本次登录过的用户 id，用于清理 Redis 中的 token */
    private final List<Long> loggedUserIds = new ArrayList<>();

    /** 本次注册的测试用户 id，用于清理其地址 */
    private final List<Long> newUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User leftover = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, NEW_PHONE));
        if (leftover != null) {
            deleteAddressesOf(leftover.getId());
            userMapper.deleteById(leftover.getId());
        }
    }

    @AfterEach
    void tearDown() {
        loggedUserIds.forEach(userId ->
                redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId)));
        loggedUserIds.clear();
        newUserIds.forEach(this::deleteAddressesOf);
        newUserIds.clear();
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
        // 新注册用户没有任何地址，也不应看到别人的地址
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);

        JSONObject response = getJson("/api/user/address/list", token);

        assertThat(response.getInteger("code")).isEqualTo(1);
        assertThat(response.getJSONArray("data")).isEmpty();
    }

    // ==================== 新增 / 修改 / 删除 ====================

    @Test
    void createAddressShouldRequireToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange("/api/user/address", HttpMethod.POST,
                new HttpEntity<>(addressBody("张三", false), headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void firstCreatedAddressShouldBecomeDefault() {
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);

        Long addressId = createAddress(token, "张三", false);

        JSONArray data = listAddresses(token);
        assertThat(data).hasSize(1);

        JSONObject address = data.getJSONObject(0);
        assertThat(address.getLong("id")).isEqualTo(addressId);
        assertThat(address.getString("consignee")).isEqualTo("张三");
        assertThat(address.getString("phone")).isEqualTo(TEST_PHONE);
        assertThat(address.getString("fullAddress")).isEqualTo(TEST_FULL_ADDRESS);
        assertThat(address.getString("label")).isEqualTo("公司");
        // 第一条地址自动设为默认
        assertThat(address.getInteger("isDefault")).isEqualTo(1);
    }

    @Test
    void createAddressShouldRejectInvalidRequest() {
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);

        Map<String, Object> blankConsignee = addressBody("张三", false);
        blankConsignee.put("consignee", " ");
        JSONObject blankResponse = exchangeJson("/api/user/address", HttpMethod.POST, blankConsignee, token);
        assertThat(blankResponse.getInteger("code")).isEqualTo(0);
        assertThat(blankResponse.getString("msg")).contains("请输入联系人");

        Map<String, Object> invalidPhone = addressBody("张三", false);
        invalidPhone.put("phone", "12345");
        JSONObject phoneResponse = exchangeJson("/api/user/address", HttpMethod.POST, invalidPhone, token);
        assertThat(phoneResponse.getInteger("code")).isEqualTo(0);
        assertThat(phoneResponse.getString("msg")).contains("手机号格式不正确");

        // 校验失败不应落库
        assertThat(listAddresses(token)).isEmpty();
    }

    @Test
    void updateAddressShouldModifyFieldsAndDefault() {
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);
        Long first = createAddress(token, "张三", false);
        Long second = createAddress(token, "李四", false);
        assertThat(findAddress(token, first).getInteger("isDefault")).isEqualTo(1);

        Map<String, Object> body = addressBody("王五", true);
        body.put("detail", "深南大道 2 号");
        JSONObject response = exchangeJson("/api/user/address/" + second, HttpMethod.PUT, body, token);
        assertThat(response.getInteger("code")).as("修改地址应成功: %s", response).isEqualTo(1);

        JSONObject updated = findAddress(token, second);
        assertThat(updated.getString("consignee")).isEqualTo("王五");
        assertThat(updated.getString("fullAddress")).isEqualTo("广东省深圳市南山区深南大道 2 号");
        assertThat(updated.getInteger("isDefault")).isEqualTo(1);
        // 默认地址互斥：原默认地址应被取消
        assertThat(findAddress(token, first).getInteger("isDefault")).isEqualTo(0);
    }

    @Test
    void setDefaultShouldSwitchDefaultAddress() {
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);
        Long first = createAddress(token, "张三", false);
        Long second = createAddress(token, "李四", false);

        JSONObject response = exchangeJson("/api/user/address/" + second + "/default",
                HttpMethod.PUT, null, token);
        assertThat(response.getInteger("code")).as("设为默认应成功: %s", response).isEqualTo(1);

        assertThat(findAddress(token, second).getInteger("isDefault")).isEqualTo(1);
        assertThat(findAddress(token, first).getInteger("isDefault")).isEqualTo(0);
    }

    @Test
    void deleteAddressShouldRemoveAndPromoteNextDefault() {
        String token = registerAndLogin(NEW_PHONE, NEW_PASSWORD);
        Long first = createAddress(token, "张三", false);
        Long second = createAddress(token, "李四", false);

        // 删除默认地址后，剩余最近新增的一条补为默认
        JSONObject response = exchangeJson("/api/user/address/" + first, HttpMethod.DELETE, null, token);
        assertThat(response.getInteger("code")).as("删除地址应成功: %s", response).isEqualTo(1);

        JSONArray data = listAddresses(token);
        assertThat(data).hasSize(1);
        assertThat(data.getJSONObject(0).getLong("id")).isEqualTo(second);
        assertThat(data.getJSONObject(0).getInteger("isDefault")).isEqualTo(1);

        exchangeJson("/api/user/address/" + second, HttpMethod.DELETE, null, token);
        assertThat(listAddresses(token)).isEmpty();
    }

    @Test
    void shouldNotOperateOtherUsersAddress() {
        String ownerToken = registerAndLogin(NEW_PHONE, NEW_PASSWORD);
        Long addressId = createAddress(ownerToken, "张三", false);

        // 演示账号不能操作别人的地址，且提示与「地址不存在」一致以便防探测
        String otherToken = login(DEMO_PHONE, DEMO_PASSWORD);

        JSONObject updateResponse = exchangeJson("/api/user/address/" + addressId,
                HttpMethod.PUT, addressBody("李四", false), otherToken);
        assertThat(updateResponse.getInteger("code")).isEqualTo(0);
        assertThat(updateResponse.getString("msg")).isEqualTo("地址不存在");

        JSONObject defaultResponse = exchangeJson("/api/user/address/" + addressId + "/default",
                HttpMethod.PUT, null, otherToken);
        assertThat(defaultResponse.getInteger("code")).isEqualTo(0);
        assertThat(defaultResponse.getString("msg")).isEqualTo("地址不存在");

        JSONObject deleteResponse = exchangeJson("/api/user/address/" + addressId,
                HttpMethod.DELETE, null, otherToken);
        assertThat(deleteResponse.getInteger("code")).isEqualTo(0);
        assertThat(deleteResponse.getString("msg")).isEqualTo("地址不存在");

        // 地址仍归原用户所有且内容未变
        JSONObject address = findAddress(ownerToken, addressId);
        assertThat(address.getString("consignee")).isEqualTo("张三");
    }

    // ==================== 辅助方法 ====================

    /**
     * 先注册再登录：手机号登录不再自动建号
     */
    private String registerAndLogin(String phone, String password) {
        ResponseEntity<String> register = restTemplate.postForEntity("/api/user/register",
                Map.of("phone", phone, "password", password), String.class);
        assertThat(JSON.parseObject(register.getBody()).getInteger("code"))
                .as("注册应成功: %s", register.getBody()).isEqualTo(1);
        String token = login(phone, password);
        newUserIds.add(loggedUserIds.get(loggedUserIds.size() - 1));
        return token;
    }

    /**
     * 构造新增 / 修改地址的请求体
     */
    private Map<String, Object> addressBody(String consignee, boolean isDefault) {
        Map<String, Object> body = new HashMap<>();
        body.put("consignee", consignee);
        body.put("phone", TEST_PHONE);
        body.put("sex", 1);
        body.put("provinceName", "广东省");
        body.put("cityName", "深圳市");
        body.put("districtName", "南山区");
        body.put("detail", "科技园 1 号");
        body.put("label", "公司");
        body.put("isDefault", isDefault);
        return body;
    }

    private Long createAddress(String token, String consignee, boolean isDefault) {
        JSONObject response = exchangeJson("/api/user/address", HttpMethod.POST,
                addressBody(consignee, isDefault), token);
        assertThat(response.getInteger("code")).as("新增地址应成功: %s", response).isEqualTo(1);
        return response.getLong("data");
    }

    private JSONArray listAddresses(String token) {
        JSONObject response = getJson("/api/user/address/list", token);
        assertThat(response.getInteger("code")).isEqualTo(1);
        return response.getJSONArray("data");
    }

    private JSONObject findAddress(String token, Long addressId) {
        JSONArray data = listAddresses(token);
        for (int i = 0; i < data.size(); i++) {
            JSONObject address = data.getJSONObject(i);
            if (addressId.equals(address.getLong("id"))) {
                return address;
            }
        }
        throw new AssertionError("地址列表中未找到 id=" + addressId);
    }

    private void deleteAddressesOf(Long userId) {
        addressBookMapper.delete(new LambdaQueryWrapper<AddressBook>()
                .eq(AddressBook::getUserId, userId));
    }

    private String login(String phone, String password) {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("phone", phone, "password", password), String.class);
        JSONObject data = JSON.parseObject(response.getBody()).getJSONObject("data");
        loggedUserIds.add(data.getLong("userId"));
        return data.getString("token");
    }

    private JSONObject getJson(String url, String token) {
        return exchangeJson(url, HttpMethod.GET, null, token);
    }

    /**
     * 携带 token 调用接口并解析统一响应；token 为 null 时不带 Authorization 头
     */
    private JSONObject exchangeJson(String url, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        ResponseEntity<String> response = restTemplate.exchange(url, method,
                new HttpEntity<>(body, headers), String.class);
        return JSON.parseObject(response.getBody());
    }

}
