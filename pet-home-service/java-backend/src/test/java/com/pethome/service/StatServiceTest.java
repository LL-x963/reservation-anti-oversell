package com.pethome.service;

import com.pethome.common.enums.OrderStatus;
import com.pethome.common.exception.BusinessException;
import com.pethome.mapper.StatMapper;
import com.pethome.vo.BeauticianRankVO;
import com.pethome.vo.RepurchaseVO;
import com.pethome.vo.ServiceRankVO;
import com.pethome.vo.StatRevenueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 数据统计服务层测试（事务回滚，使用远期日期隔离历史数据）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class StatServiceTest {

    /** 远期基准日期，避免与库中既有数据相互影响 */
    private static final LocalDate BASE_DATE = LocalDate.of(2035, 6, 15);

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Autowired
    private StatService statService;

    @Autowired
    private StatMapper statMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId1;
    private Long userId2;
    private Long userId3;
    private Long beauticianAId;
    private Long beauticianBId;
    private Long serviceAId;
    private Long serviceBId;

    @BeforeEach
    void setUp() {
        userId1 = insertUser("IT-STAT-USER-1");
        userId2 = insertUser("IT-STAT-USER-2");
        userId3 = insertUser("IT-STAT-USER-3");

        beauticianAId = insertBeautician("IT-统计美容师A", "4.50");
        beauticianBId = insertBeautician("IT-统计美容师B", "5.00");

        Long categoryId = insertCategory("IT-统计分类");
        serviceAId = insertServiceItem(categoryId, "IT-统计服务A", "100.00");
        serviceBId = insertServiceItem(categoryId, "IT-统计服务B", "50.00");
    }

    // ==================== 营业额：按日 ====================

    @Test
    void revenueDayShouldSumFinishedOrdersOfThatDay() {
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(10, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId2, beauticianAId, serviceBId, OrderStatus.PENDING_COMMENT.getCode(),
                "50.00", BASE_DATE.atTime(18, 0), BASE_DATE, 1);
        // 待支付（无支付时间）不计入
        insertOrderWithDetail(userId3, beauticianBId, serviceAId, OrderStatus.PENDING_PAY.getCode(),
                "999.00", null, BASE_DATE, 1);
        // 已取消不计入
        insertOrderWithDetail(userId3, beauticianBId, serviceAId, OrderStatus.CANCELED.getCode(),
                "888.00", BASE_DATE.atTime(9, 0), BASE_DATE, 1);
        // 次日订单不在当天范围内
        insertOrderWithDetail(userId3, beauticianBId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "777.00", BASE_DATE.plusDays(1).atTime(9, 0), BASE_DATE.plusDays(1), 1);

        StatRevenueVO result = statService.revenue(BASE_DATE, "day");

        assertThat(result.getDates()).containsExactly(BASE_DATE);
        assertThat(result.getRevenues()).hasSize(1);
        assertThat(result.getRevenues().get(0)).isEqualByComparingTo("150.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void revenueDayShouldIgnoreFinishedOrderWithoutPayTime() {
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", null, BASE_DATE, 1);

        StatRevenueVO result = statService.revenue(BASE_DATE, "day");

        assertThat(result.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    // ==================== 营业额：按周（自然周，周一至周日） ====================

    @Test
    void revenueWeekShouldEnumerateMondayToSundayAndFillMissingDays() {
        LocalDate monday = BASE_DATE.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate tuesday = monday.plusDays(1);
        LocalDate thursday = monday.plusDays(3);
        LocalDate previousSunday = monday.minusDays(1);

        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", tuesday.atTime(12, 0), tuesday, 1);
        insertOrderWithDetail(userId2, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "200.00", thursday.atTime(12, 0), thursday, 1);
        // 上周日订单（区间外）
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "500.00", previousSunday.atTime(12, 0), previousSunday, 1);

        StatRevenueVO result = statService.revenue(BASE_DATE, "week");

        assertThat(result.getDates()).hasSize(7);
        assertThat(result.getDates().get(0)).isEqualTo(monday);
        assertThat(result.getDates().get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.getDates().get(6).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(result.getRevenues().get(0)).isEqualByComparingTo("0.00");
        assertThat(result.getRevenues().get(1)).isEqualByComparingTo("100.00");
        assertThat(result.getRevenues().get(2)).isEqualByComparingTo("0.00");
        assertThat(result.getRevenues().get(3)).isEqualByComparingTo("200.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("300.00");
    }

    // ==================== 营业额：按月（自然月） ====================

    @Test
    void revenueMonthShouldEnumerateWholeMonth() {
        LocalDate firstDay = BASE_DATE.withDayOfMonth(1);
        LocalDate lastDayOfPreviousMonth = firstDay.minusDays(1);

        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", firstDay.atTime(12, 0), firstDay, 1);
        insertOrderWithDetail(userId2, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "200.00", BASE_DATE.atTime(12, 0), BASE_DATE, 1);
        // 上月最后一天（区间外）
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "500.00", lastDayOfPreviousMonth.atTime(12, 0), lastDayOfPreviousMonth, 1);

        StatRevenueVO result = statService.revenue(BASE_DATE, "month");

        assertThat(result.getDates()).hasSize(firstDay.lengthOfMonth());
        assertThat(result.getDates().get(0)).isEqualTo(firstDay);
        assertThat(result.getRevenues().get(0)).isEqualByComparingTo("100.00");
        assertThat(result.getRevenues().get(BASE_DATE.getDayOfMonth() - 1)).isEqualByComparingTo("200.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void revenueShouldDefaultDateToTodayAndRejectInvalidType() {
        StatRevenueVO result = statService.revenue(null, null);
        assertThat(result.getDates()).containsExactly(LocalDate.now());

        Throwable throwable = catchThrowable(() -> statService.revenue(BASE_DATE, "year"));
        assertThat(throwable).isInstanceOf(BusinessException.class);
        assertThat(throwable.getMessage()).contains("统计粒度");
    }

    // ==================== 服务销量排行 ====================

    @Test
    void serviceRankShouldOrderBySalesDescAndComputeRatio() {
        // 服务A：已完成订单共 5 件
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "300.00", BASE_DATE.atTime(10, 0), BASE_DATE, 3);
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.PENDING_COMMENT.getCode(),
                "200.00", BASE_DATE.atTime(11, 0), BASE_DATE, 2);
        // 服务B：已完成订单共 2 件
        insertOrderWithDetail(userId2, beauticianAId, serviceBId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(12, 0), BASE_DATE, 2);
        // 待支付订单不计入
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.PENDING_PAY.getCode(),
                "9900.00", null, BASE_DATE, 99);

        List<ServiceRankVO> ranks = statService.serviceRank();

        assertThat(ranks.size()).isLessThanOrEqualTo(10);
        ServiceRankVO rankA = findServiceRank(ranks, serviceAId);
        ServiceRankVO rankB = findServiceRank(ranks, serviceBId);
        assertThat(rankA).isNotNull();
        assertThat(rankA.getSales()).isEqualTo(5L);
        assertThat(rankA.getAmount()).isEqualByComparingTo("500.00");
        assertThat(rankB.getSales()).isEqualTo(2L);

        // 销量降序：A 必须排在 B 之前
        assertThat(ranks.indexOf(rankA)).isLessThan(ranks.indexOf(rankB));

        // 占比分母为全部服务总销量
        BigDecimal totalSales = statMapper.selectTotalSales(OrderStatus.finishedCodes());
        BigDecimal expectedRatio = BigDecimal.valueOf(rankA.getSales())
                .divide(totalSales, 4, java.math.RoundingMode.HALF_UP);
        assertThat(rankA.getRatio()).isEqualByComparingTo(expectedRatio);
    }

    // ==================== 美容师业绩排行 ====================

    @Test
    void beauticianRankShouldAggregateFinishedOrders() {
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(10, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId2, beauticianAId, serviceAId, OrderStatus.PENDING_COMMENT.getCode(),
                "200.00", BASE_DATE.atTime(11, 0), BASE_DATE, 1);
        // 待支付订单不计入 A 的业绩
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.PENDING_PAY.getCode(),
                "999.00", null, BASE_DATE, 1);
        insertOrderWithDetail(userId3, beauticianBId, serviceBId, OrderStatus.COMPLETED.getCode(),
                "500.00", BASE_DATE.atTime(12, 0), BASE_DATE, 1);

        List<BeauticianRankVO> ranks = statService.beauticianRank();

        BeauticianRankVO rankA = findBeauticianRank(ranks, beauticianAId);
        BeauticianRankVO rankB = findBeauticianRank(ranks, beauticianBId);
        assertThat(rankA).isNotNull();
        assertThat(rankA.getOrderCount()).isEqualTo(2L);
        assertThat(rankA.getAmount()).isEqualByComparingTo("300.00");
        assertThat(rankA.getScore()).isEqualByComparingTo("4.50");
        assertThat(rankB.getOrderCount()).isEqualTo(1L);
        assertThat(rankB.getAmount()).isEqualByComparingTo("500.00");

        // 按营业额降序：B(500) 排在 A(300) 之前
        assertThat(ranks.indexOf(rankB)).isLessThan(ranks.indexOf(rankA));
    }

    // ==================== 复购率 ====================

    @Test
    void repurchaseShouldComputeRateWithSameScopeDenominator() {
        // user1：3 笔已完成
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(10, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(11, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId1, beauticianAId, serviceAId, OrderStatus.PENDING_COMMENT.getCode(),
                "100.00", BASE_DATE.atTime(12, 0), BASE_DATE, 1);
        // user2：1 笔已完成 + 1 笔待支付（复购判定只看已完成）
        insertOrderWithDetail(userId2, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(13, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId2, beauticianAId, serviceAId, OrderStatus.PENDING_PAY.getCode(),
                "100.00", null, BASE_DATE, 1);
        // user3：2 笔已完成
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(14, 0), BASE_DATE, 1);
        insertOrderWithDetail(userId3, beauticianAId, serviceAId, OrderStatus.COMPLETED.getCode(),
                "100.00", BASE_DATE.atTime(15, 0), BASE_DATE, 1);

        RepurchaseVO result = statService.repurchase();

        assertThat(result.getTotalUsers()).isGreaterThanOrEqualTo(3L);
        assertThat(result.getRepeatUsers()).isGreaterThanOrEqualTo(2L);
        BigDecimal expectedRate = BigDecimal.valueOf(result.getRepeatUsers())
                .divide(BigDecimal.valueOf(result.getTotalUsers()), 4, java.math.RoundingMode.HALF_UP);
        assertThat(result.getRepurchaseRate()).isEqualByComparingTo(expectedRate);
    }

    // ==================== 辅助方法 ====================

    private ServiceRankVO findServiceRank(List<ServiceRankVO> ranks, Long serviceId) {
        return ranks.stream().filter(rank -> serviceId.equals(rank.getServiceId())).findFirst().orElse(null);
    }

    private BeauticianRankVO findBeauticianRank(List<BeauticianRankVO> ranks, Long beauticianId) {
        return ranks.stream().filter(rank -> beauticianId.equals(rank.getBeauticianId())).findFirst().orElse(null);
    }

    private Long insertOrderWithDetail(Long userId, Long beauticianId, Long serviceId, int status,
                                       String amount, LocalDateTime payTime, LocalDate appointmentDate,
                                       int quantity) {
        String orderNumber = "IT-STAT-" + System.nanoTime() + "-" + ORDER_SEQ.incrementAndGet();
        jdbcTemplate.update("INSERT INTO service_order (order_number, user_id, beautician_id, appointment_date, "
                        + "time_slot, total_amount, status, pay_time, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, '10:00-12:00', ?, ?, ?, NOW(), NOW())",
                orderNumber, userId, beauticianId, appointmentDate,
                new BigDecimal(amount), status, payTime);
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM service_order WHERE order_number = ?",
                Long.class, orderNumber);

        jdbcTemplate.update("INSERT INTO order_detail (order_id, service_id, name, price, quantity, amount, create_time, update_time) "
                        + "VALUES (?, ?, 'IT-统计下单快照', ?, ?, ?, NOW(), NOW())",
                orderId, serviceId, new BigDecimal(amount), quantity, new BigDecimal(amount));
        return orderId;
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

}
