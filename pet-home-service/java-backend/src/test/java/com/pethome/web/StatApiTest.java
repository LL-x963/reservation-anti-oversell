package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.enums.OrderStatus;
import com.pethome.common.enums.UserType;
import com.pethome.common.constant.RedisKeyConstant;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据统计接口集成测试（管理端 /api/admin/stat）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatApiTest {

    private static final String OPENID_PREFIX = "it_stat_user_";
    private static final String CATEGORY_NAME = "IT-统计接口-分类";
    private static final String SERVICE_NAME = "IT-统计接口-服务";
    private static final String BEAUTICIAN_NAME = "IT-统计接口-美容师";
    private static final String EMP_USERNAME = "it_stat_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final LocalDate BASE_DATE = LocalDate.of(2035, 6, 15);

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    private Long userId;
    private Long beauticianId;
    private Long serviceId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        cleanUp();

        Employee employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("统计测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employee.setRole("ADMIN");
        employeeMapper.insert(employee);

        userId = insertUser(OPENID_PREFIX + "a");
        beauticianId = insertBeautician(BEAUTICIAN_NAME, "4.50");
        Long categoryId = insertCategory(CATEGORY_NAME);
        serviceId = insertServiceItem(categoryId, SERVICE_NAME, "100.00");

        // 已完成（5）100 + 待评价（7）50，均按支付时间落在 BASE_DATE
        orderId = insertOrder(OrderStatus.COMPLETED.getCode(), "100.00", BASE_DATE.atTime(10, 0), 1);
        insertOrder(OrderStatus.PENDING_COMMENT.getCode(), "50.00", BASE_DATE.atTime(11, 0), 2);
        // 已取消（6）不计入
        insertOrder(OrderStatus.CANCELED.getCode(), "888.00", BASE_DATE.atTime(12, 0), 1);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void statEndpointsShouldRequireEmployeeToken() {
        for (String path : new String[]{"/api/admin/stat/revenue", "/api/admin/stat/serviceRank",
                "/api/admin/stat/beauticianRank", "/api/admin/stat/repurchase"}) {
            assertThat(getJson(path, null).getResponse().getStatusCode().value())
                    .as("未登录访问 %s", path)
                    .isEqualTo(401);
        }

        // 用户 token 不能访问管理端统计
        String userToken = loginUser(OPENID_PREFIX + "a");
        assertThat(getJson("/api/admin/stat/revenue", userToken).getResponse().getStatusCode().value())
                .isEqualTo(401);
    }

    // ==================== 营业额 ====================

    @Test
    void revenueShouldReturnDaySeriesWithTotal() {
        JSONObject data = getJson("/api/admin/stat/revenue?date=" + BASE_DATE + "&type=day", adminToken())
                .getBody().getJSONObject("data");

        assertThat(data.getJSONArray("dates").getString(0)).isEqualTo(BASE_DATE.toString());
        assertThat(data.getJSONArray("revenues").getBigDecimal(0)).isEqualByComparingTo("150.00");
        assertThat(data.getBigDecimal("totalAmount")).isEqualByComparingTo("150.00");
    }

    @Test
    void revenueShouldSupportWeekAndMonth() {
        JSONObject week = getJson("/api/admin/stat/revenue?date=" + BASE_DATE + "&type=week", adminToken())
                .getBody().getJSONObject("data");
        assertThat(week.getJSONArray("dates")).hasSize(7);
        assertThat(week.getBigDecimal("totalAmount")).isEqualByComparingTo("150.00");
        assertThat(week.getJSONArray("dates").getString(0))
                .isEqualTo(BASE_DATE.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());

        JSONObject month = getJson("/api/admin/stat/revenue?date=" + BASE_DATE + "&type=month", adminToken())
                .getBody().getJSONObject("data");
        assertThat(month.getJSONArray("dates")).hasSize(BASE_DATE.lengthOfMonth());
        assertThat(month.getBigDecimal("totalAmount")).isEqualByComparingTo("150.00");
    }

    @Test
    void revenueShouldRejectInvalidType() {
        JSONObject body = getJson("/api/admin/stat/revenue?date=" + BASE_DATE + "&type=year", adminToken()).getBody();

        assertThat(body.getInteger("code")).isZero();
        assertThat(body.getString("msg")).contains("统计粒度");
    }

    // ==================== 服务销量排行 ====================

    @Test
    void serviceRankShouldRankCreatedService() {
        JSONArray ranks = getJson("/api/admin/stat/serviceRank", adminToken()).getBody().getJSONArray("data");

        assertThat(ranks.size()).isLessThanOrEqualTo(10);
        JSONObject mine = findByKey(ranks, "serviceId", serviceId);
        assertThat(mine).isNotNull();
        // 已完成订单：数量 1 + 2 = 3，已取消订单不计入
        assertThat(mine.getLong("sales")).isEqualTo(3L);
        assertThat(mine.getString("name")).isEqualTo(SERVICE_NAME);
        assertThat(mine.getBigDecimal("ratio")).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    // ==================== 美容师业绩排行 ====================

    @Test
    void beauticianRankShouldRankCreatedBeautician() {
        JSONArray ranks = getJson("/api/admin/stat/beauticianRank", adminToken()).getBody().getJSONArray("data");

        JSONObject mine = findByKey(ranks, "beauticianId", beauticianId);
        assertThat(mine).isNotNull();
        assertThat(mine.getLong("orderCount")).isEqualTo(2L);
        assertThat(mine.getBigDecimal("amount")).isEqualByComparingTo("150.00");
        assertThat(mine.getBigDecimal("score")).isEqualByComparingTo("4.50");
        assertThat(mine.getString("name")).isEqualTo(BEAUTICIAN_NAME);
    }

    // ==================== 复购率 ====================

    @Test
    void repurchaseShouldReturnRate() {
        JSONObject data = getJson("/api/admin/stat/repurchase", adminToken()).getBody().getJSONObject("data");

        assertThat(data.getLong("totalUsers")).isGreaterThanOrEqualTo(1L);
        assertThat(data.getLong("repeatUsers")).isGreaterThanOrEqualTo(0L);
        BigDecimal expectedRate = data.getLong("totalUsers") == 0
                ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(data.getLong("repeatUsers"))
                .divide(BigDecimal.valueOf(data.getLong("totalUsers")), 4, java.math.RoundingMode.HALF_UP);
        assertThat(data.getBigDecimal("repurchaseRate")).isEqualByComparingTo(expectedRate);
    }

    // ==================== 辅助方法 ====================

    private JSONObject findByKey(JSONArray array, String key, Long value) {
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (value.equals(item.getLong(key))) {
                return item;
            }
        }
        return null;
    }

    private String adminToken() {
        JSONObject body = postJson("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), null).getBody();
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private String loginUser(String openid) {
        JSONObject body = postJson("/api/user/login", Map.of("openid", openid), null).getBody();
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private ApiResponse getJson(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return new ApiResponse(response, JSON.parseObject(response.getBody()));
    }

    private ApiResponse postJson(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        ResponseEntity<String> response = restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
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

    private Long insertOrder(int status, String amount, LocalDateTime payTime, int quantity) {
        String orderNumber = "IT-STAT-" + System.nanoTime() + "-" + ORDER_SEQ.incrementAndGet();
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, beautician_id, appointment_date, "
                        + "time_slot, total_amount, status, pay_time, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, '10:00-12:00', ?, ?, ?, NOW(), NOW())",
                orderNumber, userId, beauticianId, BASE_DATE, new BigDecimal(amount), status, payTime);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM service_order WHERE order_number = ?", Long.class, orderNumber);
        jdbcTemplate.update("INSERT INTO order_detail (order_id, service_id, name, price, quantity, amount, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, serviceId, SERVICE_NAME, new BigDecimal(amount), quantity, new BigDecimal(amount));
        return id;
    }

    private Long insertUser(String openid) {
        jdbcTemplate.update("INSERT INTO `user` (openid, sex, create_time, update_time) VALUES (?, 0, NOW(), NOW())", openid);
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
    }

    private Long insertBeautician(String name, String score) {
        jdbcTemplate.update("INSERT INTO beautician (name, score, status, create_time, update_time) "
                + "VALUES (?, ?, 1, NOW(), NOW())", name, new BigDecimal(score));
        return jdbcTemplate.queryForObject("SELECT id FROM beautician WHERE name = ?", Long.class, name);
    }

    private Long insertCategory(String name) {
        jdbcTemplate.update("INSERT INTO service_category (name, sort, status, create_time, update_time) "
                + "VALUES (?, 0, 1, NOW(), NOW())", name);
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?", Long.class, name);
    }

    private Long insertServiceItem(Long categoryId, String name, String price) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, status, create_time, update_time) "
                + "VALUES (?, ?, ?, 1, NOW(), NOW())", categoryId, name, new BigDecimal(price));
        return jdbcTemplate.queryForObject("SELECT id FROM service_item WHERE category_id = ? AND name = ?",
                Long.class, categoryId, name);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_detail WHERE order_id IN "
                + "(SELECT id FROM service_order WHERE order_number LIKE 'IT-STAT-%')");
        jdbcTemplate.update("DELETE FROM service_order WHERE order_number LIKE 'IT-STAT-%'");
        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name = ?)", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM service_category WHERE name = ?", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM beautician WHERE name = ?", BEAUTICIAN_NAME);
        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);
        jdbcTemplate.update("DELETE FROM `user` WHERE openid LIKE ?", OPENID_PREFIX + "%");

        if (userId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId));
        }
        if (orderId != null) {
            redisUtil.delete(RedisKeyConstant.reminderKey(orderId, userId));
        }
    }

}
