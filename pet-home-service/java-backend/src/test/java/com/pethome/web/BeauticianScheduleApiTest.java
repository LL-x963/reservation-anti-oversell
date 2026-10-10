package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排班管理接口集成测试（管理端 /api/admin/schedule）
 *
 * <p>需要本地 MySQL、Redis、RabbitMQ 均可用。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BeauticianScheduleApiTest {

    private static final String NAME_PREFIX = "IT-排班-";
    private static final String EMP_USERNAME = "it_schedule_admin";
    private static final String EMP_PASSWORD = "123456";
    private static final String USER_OPENID = "it_schedule_user";
    private static final String DATE = "2026-10-20";
    private static final String OTHER_DATE = "2026-10-21";
    private static final String SLOT_10_12 = "10:00-12:00";
    private static final String SLOT_12_14 = "12:00-14:00";
    private static final String SLOT_14_16 = "14:00-16:00";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    private Employee employee;
    private Long userId;
    private Long beauticianId;

    @BeforeEach
    void setUp() {
        cleanUp();

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("排班测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setSex(1);
        employee.setStatus(1);
        employeeMapper.insert(employee);

        beauticianId = insertBeautician(NAME_PREFIX + "小张");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    // ==================== 鉴权 ====================

    @Test
    void adminScheduleEndpointsShouldRequireToken() {
        assertThat(restTemplate.getForEntity("/api/admin/schedule/list?beauticianId=1&date=" + DATE, String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.postForEntity("/api/admin/schedule/batchSet",
                Map.of("beauticianId", 1, "date", DATE, "timeSlots", List.of(SLOT_10_12)), String.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/schedule/update/1", HttpMethod.PUT,
                new HttpEntity<>(Map.of("available", 0)), String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(restTemplate.exchange("/api/admin/schedule/1", HttpMethod.DELETE,
                new HttpEntity<>(new HttpHeaders()), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void userTokenShouldNotAccessAdminScheduleEndpoint() {
        String userToken = userToken();

        assertThat(restTemplate.exchange("/api/admin/schedule/list?beauticianId=" + beauticianId + "&date=" + DATE,
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class)
                .getStatusCode().value()).isEqualTo(401);
    }

    // ==================== 批量设置与查询 ====================

    @Test
    void batchSetAndListFlowShouldWork() {
        String token = adminToken();

        JSONObject batchResult = batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_12_14);
        assertThat(batchResult.getInteger("code")).isEqualTo(1);
        JSONObject data = batchResult.getJSONObject("data");
        assertThat(data.getInteger("total")).isEqualTo(2);
        assertThat(data.getInteger("addedCount")).isEqualTo(2);
        assertThat(data.getInteger("skippedCount")).isZero();
        assertThat(data.getJSONArray("addedSlots")).containsExactly(SLOT_10_12, SLOT_12_14);

        JSONArray list = listFor(token, beauticianId, DATE);
        assertThat(list).hasSize(2);
        assertThat(slotNames(list)).containsExactly(SLOT_10_12, SLOT_12_14);
        JSONObject first = list.getJSONObject(0);
        assertThat(first.getInteger("available")).isEqualTo(1);
        assertThat(first.getBoolean("occupied")).isFalse();
        assertThat(first.getString("date")).isEqualTo(DATE);

        // 其他日期无排班
        assertThat(listFor(token, beauticianId, OTHER_DATE)).isEmpty();
    }

    @Test
    void batchSetShouldBeIncremental() {
        String token = adminToken();
        batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_12_14);

        JSONObject result = batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_12_14, SLOT_14_16);

        JSONObject data = result.getJSONObject("data");
        assertThat(data.getInteger("addedCount")).isEqualTo(1);
        assertThat(data.getInteger("unchangedCount")).isEqualTo(2);
        assertThat(listFor(token, beauticianId, DATE)).hasSize(3);
    }

    @Test
    void batchSetShouldSkipOccupiedSlots() {
        String token = adminToken();
        insertSchedule(beauticianId, DATE, SLOT_14_16, 0, 77777L);

        JSONObject result = batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_14_16);

        JSONObject data = result.getJSONObject("data");
        assertThat(data.getInteger("addedCount")).isEqualTo(1);
        assertThat(data.getInteger("skippedCount")).isEqualTo(1);
        JSONObject skipped = data.getJSONArray("skippedSlots").getJSONObject(0);
        assertThat(skipped.getString("timeSlot")).isEqualTo(SLOT_14_16);
        assertThat(skipped.getLong("orderId")).isEqualTo(77777L);
        assertThat(skipped.getString("reason")).isNotBlank();

        // 占用时段状态未被改变
        JSONArray list = listFor(token, beauticianId, DATE);
        JSONObject occupied = findSlot(list, SLOT_14_16);
        assertThat(occupied.getInteger("available")).isZero();
        assertThat(occupied.getBoolean("occupied")).isTrue();
        assertThat(occupied.getLong("orderId")).isEqualTo(77777L);
    }

    @Test
    void batchSetShouldRejectIllegalTimeSlot() {
        String token = adminToken();

        JSONObject response = batchSet(token, beauticianId, DATE, "09:00-10:00");

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("非法时段");
    }

    // ==================== 修改可用状态 ====================

    @Test
    void updateAvailableShouldWorkAndRejectOccupiedSlot() {
        String token = adminToken();
        batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_12_14);

        Long normalId = findSlot(listFor(token, beauticianId, DATE), SLOT_10_12).getLong("id");
        Long occupiedId = insertSchedule(beauticianId, DATE, SLOT_14_16, 0, 33333L);

        // 非占用：可自由切换
        assertThat(updateAvailable(token, normalId, 0).getInteger("code")).isEqualTo(1);
        assertThat(findSlot(listFor(token, beauticianId, DATE), SLOT_10_12).getInteger("available")).isZero();
        assertThat(updateAvailable(token, normalId, 1).getInteger("code")).isEqualTo(1);

        // 被占用：禁止直接设为可接单
        JSONObject reject = updateAvailable(token, occupiedId, 1);
        assertThat(reject.getInteger("code")).isZero();
        assertThat(reject.getString("msg")).contains("已被订单占用");

        // 被占用：保持不可接单为幂等操作
        assertThat(updateAvailable(token, occupiedId, 0).getInteger("code")).isEqualTo(1);
    }

    @Test
    void updateNonexistentScheduleShouldFail() {
        String token = adminToken();

        JSONObject response = updateAvailable(token, 99999999L, 1);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("排班不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldSucceedWhenNotOccupiedAndRejectWhenOccupied() {
        String token = adminToken();
        batchSet(token, beauticianId, DATE, SLOT_10_12);
        Long normalId = findSlot(listFor(token, beauticianId, DATE), SLOT_10_12).getLong("id");
        Long occupiedId = insertSchedule(beauticianId, DATE, SLOT_14_16, 0, 22222L);

        JSONObject reject = deleteSchedule(token, occupiedId);
        assertThat(reject.getInteger("code")).isZero();
        assertThat(reject.getString("msg")).contains("已被订单占用");

        assertThat(deleteSchedule(token, normalId).getInteger("code")).isEqualTo(1);
        assertThat(listFor(token, beauticianId, DATE)).hasSize(1);
    }

    @Test
    void deleteNonexistentScheduleShouldFail() {
        String token = adminToken();

        JSONObject response = deleteSchedule(token, 99999999L);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("排班不存在");
    }

    // ==================== 参数校验 ====================

    @Test
    void batchSetShouldRejectInvalidParam() {
        String token = adminToken();

        // 缺少时段
        JSONObject missingSlots = postJson("/api/admin/schedule/batchSet",
                Map.of("beauticianId", beauticianId, "date", DATE), token);
        assertThat(missingSlots.getInteger("code")).isZero();

        // 空时段数组
        JSONObject emptySlots = batchSet(token, beauticianId, DATE, new String[0]);
        assertThat(emptySlots.getInteger("code")).isZero();

        // 缺少美容师 id
        Map<String, Object> noBeautician = new HashMap<>();
        noBeautician.put("date", DATE);
        noBeautician.put("timeSlots", List.of(SLOT_10_12));
        assertThat(postJson("/api/admin/schedule/batchSet", noBeautician, token).getInteger("code")).isZero();

        // 超过 5 个时段
        JSONObject tooMany = batchSet(token, beauticianId, DATE, SLOT_10_12, SLOT_12_14, SLOT_14_16,
                "16:00-18:00", "18:00-20:00", SLOT_10_12);
        assertThat(tooMany.getInteger("code")).isZero();
        assertThat(tooMany.getString("msg")).contains("时段数量");
    }

    @Test
    void listShouldRejectMissingParamAndInvalidPathId() {
        String token = adminToken();

        // 缺少 date
        JSONObject missingDate = getJson("/api/admin/schedule/list?beauticianId=" + beauticianId, token);
        assertThat(missingDate.getInteger("code")).isZero();
        assertThat(missingDate.getString("msg")).contains("date");

        // 非法路径 id
        assertThat(deleteSchedule(token, 0L).getInteger("code")).isZero();
        assertThat(updateAvailable(token, 0L, 1).getInteger("code")).isZero();
    }

    @Test
    void updateAvailableShouldRejectInvalidValue() {
        String token = adminToken();
        batchSet(token, beauticianId, DATE, SLOT_10_12);
        Long id = findSlot(listFor(token, beauticianId, DATE), SLOT_10_12).getLong("id");

        JSONObject response = updateAvailable(token, id, 9);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
    }

    // ==================== 辅助方法 ====================

    private String adminToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private String userToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/user/login",
                Map.of("openid", USER_OPENID), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        JSONObject data = body.getJSONObject("data");
        userId = data.getLong("userId");
        return data.getString("token");
    }

    private JSONObject batchSet(String token, Long beauticianId, String date, String... timeSlots) {
        Map<String, Object> body = new HashMap<>();
        body.put("beauticianId", beauticianId);
        body.put("date", date);
        body.put("timeSlots", Arrays.asList(timeSlots));
        return postJson("/api/admin/schedule/batchSet", body, token);
    }

    private JSONObject updateAvailable(String token, Long id, int available) {
        return putJson("/api/admin/schedule/update/" + id, Map.of("available", available), token);
    }

    private JSONObject deleteSchedule(String token, Long id) {
        ResponseEntity<String> response = restTemplate.exchange("/api/admin/schedule/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)), String.class);
        return JSON.parseObject(response.getBody());
    }

    private JSONArray listFor(String token, Long beauticianId, String date) {
        JSONObject response = getJson("/api/admin/schedule/list?beauticianId=" + beauticianId + "&date=" + date, token);
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

    private JSONObject findSlot(JSONArray array, String timeSlot) {
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (timeSlot.equals(item.getString("timeSlot"))) {
                return item;
            }
        }
        return null;
    }

    private List<String> slotNames(JSONArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            result.add(array.getJSONObject(i).getString("timeSlot"));
        }
        return result;
    }

    private Long insertBeautician(String name) {
        jdbcTemplate.update("INSERT INTO beautician (name, skill_tags, score, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, NOW(), NOW())", name, "洗护", new BigDecimal("5.00"), 1);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician WHERE name = ?", Long.class, name);
    }

    private Long insertSchedule(Long beauticianId, String date, String timeSlot, int available, Long orderId) {
        jdbcTemplate.update("INSERT INTO beautician_schedule (beautician_id, date, time_slot, available, order_id, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", beauticianId, LocalDate.parse(date), timeSlot, available, orderId);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                Long.class, beauticianId, LocalDate.parse(date), timeSlot);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM beautician_schedule WHERE beautician_id IN "
                + "(SELECT id FROM beautician WHERE name LIKE ?)", NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM beautician WHERE name LIKE ?", NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM `user` WHERE openid = ?", USER_OPENID);
        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);

        if (userId != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId));
            userId = null;
        }
        if (employee != null && employee.getId() != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.EMPLOYEE, employee.getId()));
        }
    }

}
