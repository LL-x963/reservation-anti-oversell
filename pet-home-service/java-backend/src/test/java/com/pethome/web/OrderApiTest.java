package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.OrderStatus;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约订单接口集成测试（用户端 /api/user/order，管理端 /api/admin/order）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiTest {

    private static final String OPENID_PREFIX = "it_order_user_";
    private static final String OPENID_A = OPENID_PREFIX + "a";
    private static final String OPENID_B = OPENID_PREFIX + "b";
    private static final String CATEGORY_NAME = "IT-订单接口-分类";
    private static final String SERVICE_NAME = "IT-订单接口-服务A";
    private static final String BEAUTICIAN_NAME = "IT-订单接口-美容师";
    private static final String EMP_USERNAME = "it_order_admin";
    private static final String EMP_PASSWORD = "123456";

    private static final String SLOT_10_12 = "10:00-12:00";
    private static final String SLOT_12_14 = "12:00-14:00";
    private static final LocalDate DATE = LocalDate.now().plusDays(3);
    private static final String DATE_TEXT = DATE.format(DateTimeFormatter.ISO_LOCAL_DATE);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private Long userIdA;
    private Long userIdB;
    private Long addressA;
    private Long petA;
    private Long beauticianId;
    private Long serviceId;

    private String tokenA;
    private String tokenB;
    private String adminToken;

    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        Employee employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("订单测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employeeMapper.insert(employee);

        Long categoryId = insertCategory(CATEGORY_NAME);
        serviceId = insertServiceItem(categoryId, SERVICE_NAME, "99.00", 1);

        beauticianId = insertBeautician(BEAUTICIAN_NAME, 1);
        insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null);
        insertSchedule(beauticianId, DATE, SLOT_12_14, 1, null);

        tokenA = login(OPENID_A);
        userIdA = queryUserId(OPENID_A);
        tokenB = login(OPENID_B);
        userIdB = queryUserId(OPENID_B);

        addressA = insertAddress(userIdA);
        petA = insertPet(userIdA, "IT-订单接口-宠物A");
        insertAddress(userIdB);
        insertPet(userIdB, "IT-订单接口-宠物B");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void allOrderEndpointsShouldRequireToken() {
        assertThat(postJson("/api/user/order/submit", Map.of("addressId", 1), null).getResponse()
                .getStatusCode().value()).isEqualTo(401);
        assertThat(postJson("/api/user/order/pay", Map.of("orderId", 1), null).getResponse()
                .getStatusCode().value()).isEqualTo(401);
        assertThat(getJson("/api/user/order/page", null).getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(getJson("/api/user/order/1", null).getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(putJson("/api/user/order/cancel/1", Map.of(), null).getResponse().getStatusCode().value())
                .isEqualTo(401);
        assertThat(postJson("/api/user/order/reminder/1", null, null).getResponse().getStatusCode().value())
                .isEqualTo(401);
        assertThat(postJson("/api/user/order/comment", Map.of("orderId", 1, "score", 5), null).getResponse()
                .getStatusCode().value()).isEqualTo(401);

        assertThat(getJson("/api/admin/order/page", null).getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(getJson("/api/admin/order/1", null).getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(putJson("/api/admin/order/confirm/1", Map.of(), null).getResponse().getStatusCode().value())
                .isEqualTo(401);
        assertThat(putJson("/api/admin/order/assign", Map.of("orderId", 1, "beauticianId", 1), null).getResponse()
                .getStatusCode().value()).isEqualTo(401);
        assertThat(putJson("/api/admin/order/cancel/1", Map.of(), null).getResponse().getStatusCode().value())
                .isEqualTo(401);
        assertThat(putJson("/api/admin/order/complete/1", Map.of(), null).getResponse().getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void tokenIdentityShouldNotBeCrossUsable() {
        // 用户 token 访问管理端订单接口
        assertThat(getJson("/api/admin/order/page", tokenA).getResponse().getStatusCode().value()).isEqualTo(401);
        // 员工 token 访问用户端订单接口
        assertThat(getJson("/api/user/order/page", adminToken()).getResponse().getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 提交与支付 ====================

    @Test
    void submitAndPayFlowShouldWork() {
        // 预置预约车，验证下单后被清空
        assertThat(postJson("/api/user/cart/add", Map.of("serviceId", serviceId, "quantity", 1), tokenA)
                .getBody().getInteger("code")).isEqualTo(1);

        JSONObject submitResult = submitOrder(tokenA, SLOT_10_12, 2);
        assertThat(submitResult.getInteger("code")).isEqualTo(1);

        JSONObject data = submitResult.getJSONObject("data");
        Long orderId = data.getLong("orderId");
        createdOrderIds.add(orderId);
        assertThat(orderId).isNotNull();
        assertThat(data.getString("orderNumber")).isNotBlank();
        assertThat(data.getBigDecimal("totalAmount")).isEqualByComparingTo("198.00");
        assertThat(data.getInteger("status")).isEqualTo(OrderStatus.PENDING_PAY.getCode());

        // 预约车已清空
        assertThat(getJson("/api/user/cart/list", tokenA).getBody().getJSONArray("data")).isEmpty();

        // 排班已被占用
        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT available, order_id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                beauticianId, DATE, SLOT_10_12);
        assertThat(((Number) schedule.get("available")).intValue()).isZero();
        assertThat(((Number) schedule.get("order_id")).longValue()).isEqualTo(orderId);

        // 详情
        JSONObject detail = getJson("/api/user/order/" + orderId, tokenA).getBody().getJSONObject("data");
        assertThat(detail.getString("statusDesc")).isEqualTo("待支付");
        assertThat(detail.getString("beauticianName")).isEqualTo(BEAUTICIAN_NAME);
        assertThat(detail.getJSONArray("items")).hasSize(1);
        assertThat(detail.getJSONArray("items").getJSONObject(0).getString("name")).isEqualTo(SERVICE_NAME);
        assertThat(detail.getJSONObject("comment")).isNull();

        // 支付
        assertThat(postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA).getBody().getInteger("code"))
                .isEqualTo(1);
        JSONObject paid = getJson("/api/user/order/" + orderId, tokenA).getBody().getJSONObject("data");
        assertThat(paid.getInteger("status")).isEqualTo(OrderStatus.PENDING_CONFIRM.getCode());
        assertThat(paid.getString("payTime")).isNotBlank();

        // 重复支付被拒
        JSONObject again = postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA).getBody();
        assertThat(again.getInteger("code")).isZero();
    }

    @Test
    void submitShouldRejectInvalidTimeSlotWithBizCode601() {
        JSONObject body = submitOrder(tokenA, "09:00-10:00", 1);

        assertThat(body.getInteger("code")).isZero();
        assertThat(body.getInteger("bizCode")).isEqualTo(601);
        assertThat(body.getString("msg")).contains("非法时段");
    }

    @Test
    void submitShouldRejectUnavailableScheduleWithBizCode601() {
        jdbcTemplate.update("UPDATE beautician_schedule SET available = 0, order_id = 88888 WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                beauticianId, DATE, SLOT_12_14);

        JSONObject body = submitOrder(tokenA, SLOT_12_14, 1);

        assertThat(body.getInteger("code")).isZero();
        assertThat(body.getInteger("bizCode")).isEqualTo(601);
        assertThat(body.getString("msg")).isEqualTo("该时段不可约");
    }

    @Test
    void submitShouldRejectInvalidParam() {
        // 过去日期
        Map<String, Object> pastDate = submitBody(SLOT_10_12, 1);
        pastDate.put("appointmentDate", LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
        JSONObject dateResponse = postJson("/api/user/order/submit", pastDate, tokenA).getBody();
        assertThat(dateResponse.getInteger("code")).isZero();
        assertThat(dateResponse.getString("msg")).contains("预约日期");

        // 缺少服务项
        Map<String, Object> noItems = submitBody(SLOT_10_12, 1);
        noItems.remove("items");
        assertThat(postJson("/api/user/order/submit", noItems, tokenA).getBody().getInteger("code")).isZero();

        // 数量非法
        Map<String, Object> badQuantity = submitBody(SLOT_10_12, 0);
        assertThat(postJson("/api/user/order/submit", badQuantity, tokenA).getBody().getInteger("code")).isZero();

        // 评价分数非法
        JSONObject scoreResponse = postJson("/api/user/order/comment", Map.of("orderId", 1, "score", 6), tokenA).getBody();
        assertThat(scoreResponse.getInteger("code")).isZero();
    }

    @Test
    void submitShouldRejectOtherUsersPetAndWrongIds() {
        Map<String, Object> othersPet = submitBody(SLOT_10_12, 1);
        othersPet.put("petId", queryPetId(userIdB));
        JSONObject petResponse = postJson("/api/user/order/submit", othersPet, tokenA).getBody();
        assertThat(petResponse.getString("msg")).isEqualTo("宠物不存在");

        Map<String, Object> othersAddress = submitBody(SLOT_10_12, 1);
        othersAddress.put("addressId", queryAddressId(userIdB));
        JSONObject addressResponse = postJson("/api/user/order/submit", othersAddress, tokenA).getBody();
        assertThat(addressResponse.getString("msg")).isEqualTo("地址不存在");

        Map<String, Object> badBeautician = submitBody(SLOT_10_12, 1);
        badBeautician.put("beauticianId", 99999999);
        JSONObject beauticianResponse = postJson("/api/user/order/submit", badBeautician, tokenA).getBody();
        assertThat(beauticianResponse.getString("msg")).isEqualTo("美容师不存在");

        Map<String, Object> badService = submitBody(SLOT_10_12, 1);
        badService.put("items", List.of(Map.of("serviceId", 99999999, "quantity", 1)));
        JSONObject serviceResponse = postJson("/api/user/order/submit", badService, tokenA).getBody();
        assertThat(serviceResponse.getString("msg")).isEqualTo("服务不存在");
    }

    // ==================== 取消 ====================

    @Test
    void cancelShouldReleaseScheduleAndRejectCompletedOrder() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);

        assertThat(putJson("/api/user/order/cancel/" + orderId, Map.of(), tokenA).getBody().getInteger("code"))
                .isEqualTo(1);

        JSONObject detail = getJson("/api/user/order/" + orderId, tokenA).getBody().getJSONObject("data");
        assertThat(detail.getInteger("status")).isEqualTo(OrderStatus.CANCELED.getCode());
        assertThat(detail.getInteger("reminderStatus")).isEqualTo(2);

        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT available, order_id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                beauticianId, DATE, SLOT_10_12);
        assertThat(((Number) schedule.get("available")).intValue()).isEqualTo(1);
        assertThat(schedule.get("order_id")).isNull();

        // 已取消订单不能再取消
        assertThat(putJson("/api/user/order/cancel/" + orderId, Map.of(), tokenA).getBody().getInteger("code"))
                .isZero();

        // 已完成的订单不能取消
        Long completedOrderId = completeAndComment(SLOT_12_14, 5);
        JSONObject canceled = putJson("/api/user/order/cancel/" + completedOrderId, Map.of(), tokenA).getBody();
        assertThat(canceled.getInteger("code")).isZero();
        assertThat(canceled.getString("msg")).contains("不能取消");
    }

    @Test
    void detailShouldRejectOtherUsersOrder() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);

        JSONObject response = getJson("/api/user/order/" + orderId, tokenB).getBody();

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isEqualTo("订单不存在");
    }

    // ==================== 催单 ====================

    @Test
    void reminderShouldRateLimitWithBizCode603() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);
        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);

        assertThat(postJson("/api/user/order/reminder/" + orderId, null, tokenA).getBody().getInteger("code"))
                .isEqualTo(1);

        JSONObject second = postJson("/api/user/order/reminder/" + orderId, null, tokenA).getBody();
        assertThat(second.getInteger("code")).isZero();
        assertThat(second.getInteger("bizCode")).isEqualTo(603);
        assertThat(second.getString("msg")).isEqualTo("催单过于频繁，请稍后再试");

        // 待支付订单不允许催单
        Long pendingPayOrderId = submitOrder(tokenA, SLOT_12_14, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(pendingPayOrderId);
        JSONObject wrongStatus = postJson("/api/user/order/reminder/" + pendingPayOrderId, null, tokenA).getBody();
        assertThat(wrongStatus.getString("msg")).contains("不能催单");
    }

    // ==================== 分页 ====================

    @Test
    void pageShouldFilterByStatusAndIsolateUsers() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);
        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);

        JSONObject pendingConfirm = getJson("/api/user/order/page?status=" + OrderStatus.PENDING_CONFIRM.getCode(), tokenA)
                .getBody().getJSONObject("data");
        assertThat(pendingConfirm.getLong("total")).isEqualTo(1L);
        assertThat(pendingConfirm.getJSONArray("records").getJSONObject(0).getString("statusDesc"))
                .isEqualTo("待确认");

        JSONObject canceled = getJson("/api/user/order/page?status=" + OrderStatus.CANCELED.getCode(), tokenA)
                .getBody().getJSONObject("data");
        assertThat(canceled.getLong("total")).isZero();

        // 其他用户看不到
        assertThat(getJson("/api/user/order/page", tokenB).getBody().getJSONObject("data").getLong("total")).isZero();
    }

    // ==================== 管理端：确认 / 分配 / 完成 / 评价 ====================

    @Test
    void adminFlowShouldCompleteOrderAndRecalculateBeauticianScore() {
        // 两单评价分数 4 + 2，平均分应为 3.00
        Long firstOrderId = completeAndComment(SLOT_10_12, 4);
        Long secondOrderId = completeAndComment(SLOT_12_14, 2);

        // 评价后订单状态为已完成
        assertThat(getJson("/api/user/order/" + firstOrderId, tokenA).getBody()
                .getJSONObject("data").getInteger("status")).isEqualTo(OrderStatus.COMPLETED.getCode());
        // 详情带评价信息
        JSONObject detail = getJson("/api/user/order/" + secondOrderId, tokenA).getBody().getJSONObject("data");
        assertThat(detail.getJSONObject("comment").getInteger("score")).isEqualTo(2);

        // 重复评价被拒
        JSONObject duplicate = postJson("/api/user/order/comment",
                Map.of("orderId", secondOrderId, "score", 5), tokenA).getBody();
        assertThat(duplicate.getInteger("code")).isZero();

        // 异步重算美容师平均评分
        awaitBeauticianScore(beauticianId, "3.00");
    }

    @Test
    void adminConfirmAssignAndCompleteShouldValidateStatus() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);

        // 未支付不能确认
        assertThat(putJson("/api/admin/order/confirm/" + orderId, Map.of(), adminToken()).getBody().getInteger("code"))
                .isZero();

        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);

        // 确认
        assertThat(putJson("/api/admin/order/confirm/" + orderId, Map.of(), adminToken()).getBody().getInteger("code"))
                .isEqualTo(1);
        JSONObject confirmed = getJson("/api/admin/order/" + orderId, adminToken()).getBody().getJSONObject("data");
        assertThat(confirmed.getInteger("status")).isEqualTo(OrderStatus.PENDING_VISIT.getCode());
        assertThat(confirmed.getString("messageId")).isEqualTo("remind:" + orderId);

        // 改派美容师
        Long anotherBeauticianId = insertBeautician(BEAUTICIAN_NAME + "-B", 1);
        Map<String, Object> assign = new HashMap<>();
        assign.put("orderId", orderId);
        assign.put("beauticianId", anotherBeauticianId);
        assertThat(putJson("/api/admin/order/assign", assign, adminToken()).getBody().getInteger("code")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT beautician_id FROM service_order WHERE id = ?",
                Long.class, orderId)).isEqualTo(anotherBeauticianId);

        // 不存在的美容师
        assign.put("beauticianId", 99999999);
        assertThat(putJson("/api/admin/order/assign", assign, adminToken()).getBody().getString("msg"))
                .isEqualTo("美容师不存在");

        // 完成订单 → 待评价
        assertThat(putJson("/api/admin/order/complete/" + orderId, Map.of(), adminToken()).getBody().getInteger("code"))
                .isEqualTo(1);
        assertThat(getJson("/api/admin/order/" + orderId, adminToken()).getBody()
                .getJSONObject("data").getInteger("status")).isEqualTo(OrderStatus.PENDING_COMMENT.getCode());

        // 再次完成被拒
        assertThat(putJson("/api/admin/order/complete/" + orderId, Map.of(), adminToken()).getBody().getInteger("code"))
                .isZero();
    }

    @Test
    void adminPageShouldFilterByStatusDateAndBeautician() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);
        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);

        String token = adminToken();
        JSONObject byStatus = getJson("/api/admin/order/page?status=" + OrderStatus.PENDING_CONFIRM.getCode(), token)
                .getBody().getJSONObject("data");
        assertThat(byStatus.getLong("total")).isEqualTo(1L);

        JSONObject byDate = getJson("/api/admin/order/page?appointmentDate=" + DATE_TEXT, token)
                .getBody().getJSONObject("data");
        assertThat(byDate.getLong("total")).isEqualTo(1L);

        JSONObject byBeautician = getJson("/api/admin/order/page?beauticianId=" + beauticianId, token)
                .getBody().getJSONObject("data");
        assertThat(byBeautician.getLong("total")).isEqualTo(1L);

        JSONObject byOtherBeautician = getJson("/api/admin/order/page?beauticianId=99999999", token)
                .getBody().getJSONObject("data");
        assertThat(byOtherBeautician.getLong("total")).isZero();
    }

    @Test
    void adminCancelShouldReleaseSchedule() {
        Long orderId = submitOrder(tokenA, SLOT_10_12, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);
        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);

        assertThat(putJson("/api/admin/order/cancel/" + orderId, Map.of(), adminToken()).getBody().getInteger("code"))
                .isEqualTo(1);

        assertThat(((Number) jdbcTemplate.queryForMap(
                "SELECT available FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                beauticianId, DATE, SLOT_10_12).get("available")).intValue()).isEqualTo(1);
    }

    // ==================== 辅助方法 ====================

    /**
     * 走完整流程：下单 → 支付 → 确认 → 完成 → 评价
     *
     * @return 订单 id
     */
    private Long completeAndComment(String timeSlot, int score) {
        Long orderId = submitOrder(tokenA, timeSlot, 1).getJSONObject("data").getLong("orderId");
        createdOrderIds.add(orderId);

        postJson("/api/user/order/pay", Map.of("orderId", orderId), tokenA);
        putJson("/api/admin/order/confirm/" + orderId, Map.of(), adminToken());
        putJson("/api/admin/order/complete/" + orderId, Map.of(), adminToken());

        JSONObject comment = postJson("/api/user/order/comment",
                Map.of("orderId", orderId, "score", score, "content", "IT-评价内容"), tokenA).getBody();
        assertThat(comment.getInteger("code")).isEqualTo(1);

        return orderId;
    }

    private void awaitBeauticianScore(Long beauticianId, String expected) {
        BigDecimal target = new BigDecimal(expected);
        for (int i = 0; i < 50; i++) {
            BigDecimal score = jdbcTemplate.queryForObject("SELECT score FROM beautician WHERE id = ?",
                    BigDecimal.class, beauticianId);
            if (score != null && score.compareTo(target) == 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT score FROM beautician WHERE id = ?", BigDecimal.class, beauticianId))
                .isEqualByComparingTo(expected);
    }

    private Map<String, Object> submitBody(String timeSlot, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("addressId", addressA);
        body.put("petId", petA);
        body.put("beauticianId", beauticianId);
        body.put("appointmentDate", DATE_TEXT);
        body.put("timeSlot", timeSlot);
        body.put("items", List.of(Map.of("serviceId", serviceId, "quantity", quantity)));
        return body;
    }

    private JSONObject submitOrder(String token, String timeSlot, int quantity) {
        return postJson("/api/user/order/submit", submitBody(timeSlot, quantity), token).getBody();
    }

    private String login(String openid) {
        JSONObject body = postJson("/api/user/login", Map.of("openid", openid), null).getBody();
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private String adminToken() {
        if (adminToken == null) {
            JSONObject body = postJson("/api/admin/login",
                    Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), null).getBody();
            assertThat(body.getInteger("code")).isEqualTo(1);
            adminToken = body.getJSONObject("data").getString("token");
        }
        return adminToken;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return headers;
    }

    private ApiResponse getJson(String path, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);
        return new ApiResponse(response, JSON.parseObject(response.getBody()));
    }

    private ApiResponse postJson(String path, Map<String, Object> body, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), String.class);
        return new ApiResponse(response, JSON.parseObject(response.getBody()));
    }

    private ApiResponse putJson(String path, Map<String, Object> body, String token) {
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), String.class);
        return new ApiResponse(response, JSON.parseObject(response.getBody()));
    }

    private record ApiResponse(ResponseEntity<String> response, JSONObject body) {

        ResponseEntity<String> getResponse() {
            return response;
        }

        JSONObject getBody() {
            return body;
        }
    }

    private Long queryUserId(String openid) {
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
    }

    private Long queryPetId(Long userId) {
        return jdbcTemplate.queryForObject("SELECT id FROM pet_profile WHERE user_id = ?", Long.class, userId);
    }

    private Long queryAddressId(Long userId) {
        return jdbcTemplate.queryForObject("SELECT id FROM address_book WHERE user_id = ?", Long.class, userId);
    }

    private Long insertCategory(String name) {
        jdbcTemplate.update("INSERT INTO service_category (name, sort, status, create_time, update_time) "
                + "VALUES (?, 0, 1, NOW(), NOW())", name);
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?", Long.class, name);
    }

    private Long insertServiceItem(Long categoryId, String name, String price, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, NOW(), NOW())", categoryId, name, new BigDecimal(price), status);
        return jdbcTemplate.queryForObject("SELECT id FROM service_item WHERE category_id = ? AND name = ?",
                Long.class, categoryId, name);
    }

    private Long insertBeautician(String name, int status) {
        jdbcTemplate.update("INSERT INTO beautician (name, score, status, create_time, update_time) "
                + "VALUES (?, 5.00, ?, NOW(), NOW())", name, status);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician WHERE name = ?", Long.class, name);
    }

    private void insertSchedule(Long beauticianId, LocalDate date, String timeSlot, int available, Long orderId) {
        jdbcTemplate.update("INSERT INTO beautician_schedule (beautician_id, date, time_slot, available, order_id, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", beauticianId, date, timeSlot, available, orderId);
    }

    private Long insertAddress(Long userId) {
        jdbcTemplate.update("INSERT INTO address_book (user_id, consignee, phone, detail, is_default, create_time, update_time) "
                + "VALUES (?, 'IT-收件人', '13800000000', 'IT-详细地址', 1, NOW(), NOW())", userId);
        return queryAddressId(userId);
    }

    private Long insertPet(Long userId, String name) {
        jdbcTemplate.update("INSERT INTO pet_profile (user_id, name, create_time, update_time) "
                + "VALUES (?, ?, NOW(), NOW())", userId, name);
        return queryPetId(userId);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_comment WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?)", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM order_comment WHERE order_id IN "
                + "(SELECT id FROM service_order WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?))", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM order_detail WHERE order_id IN "
                + "(SELECT id FROM service_order WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?))", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_order WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?)", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM beautician_schedule WHERE beautician_id IN "
                + "(SELECT id FROM beautician WHERE name LIKE ?)", BEAUTICIAN_NAME + "%");
        jdbcTemplate.update("DELETE FROM beautician WHERE name LIKE ?", BEAUTICIAN_NAME + "%");
        jdbcTemplate.update("DELETE FROM address_book WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?)", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM pet_profile WHERE user_id IN "
                + "(SELECT id FROM `user` WHERE openid LIKE ?)", OPENID_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name = ?)", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM service_category WHERE name = ?", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);
        jdbcTemplate.update("DELETE FROM `user` WHERE openid LIKE ?", OPENID_PREFIX + "%");

        if (userIdA != null) {
            createdOrderIds.forEach(orderId -> redisUtil.delete(RedisKeyConstant.reminderKey(orderId, userIdA)));
            redisUtil.delete(RedisKeyConstant.cartKey(userIdA));
        }
        createdOrderIds.clear();
        if (userIdB != null) {
            redisUtil.delete(RedisKeyConstant.cartKey(userIdB));
        }
        adminToken = null;
    }

}
