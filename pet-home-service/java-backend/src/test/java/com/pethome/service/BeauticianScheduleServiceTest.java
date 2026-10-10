package com.pethome.service;

import com.pethome.common.enums.TimeSlot;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ScheduleBatchSetRequest;
import com.pethome.dto.ScheduleListQuery;
import com.pethome.vo.ScheduleBatchSetVO;
import com.pethome.vo.ScheduleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 排班服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class BeauticianScheduleServiceTest {

    private static final String SLOT_10_12 = "10:00-12:00";
    private static final String SLOT_12_14 = "12:00-14:00";
    private static final String SLOT_14_16 = "14:00-16:00";
    private static final String SLOT_16_18 = "16:00-18:00";
    private static final String SLOT_18_20 = "18:00-20:00";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 20);
    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 10, 21);

    @Autowired
    private BeauticianScheduleService scheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long beauticianId;
    private Long otherBeauticianId;

    @BeforeEach
    void setUp() {
        beauticianId = insertBeautician("IT-排班美容师A");
        otherBeauticianId = insertBeautician("IT-排班美容师B");
    }

    // ==================== 批量设置 ====================

    @Test
    void batchSetShouldAddNewSlots() {
        ScheduleBatchSetVO result = batchSet(beauticianId, DATE, SLOT_10_12, SLOT_12_14);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getAddedCount()).isEqualTo(2);
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getUnchangedCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getAddedSlots()).containsExactly(SLOT_10_12, SLOT_12_14);

        assertThat(countSchedule(beauticianId, DATE)).isEqualTo(2);
        assertThat(queryAvailable(beauticianId, DATE, SLOT_10_12)).isEqualTo(1);
    }

    @Test
    void batchSetShouldBeIncrementalAndIdempotent() {
        batchSet(beauticianId, DATE, SLOT_10_12, SLOT_12_14);

        // 重复传入已有时段 + 一个新时段
        ScheduleBatchSetVO second = batchSet(beauticianId, DATE, SLOT_10_12, SLOT_12_14, SLOT_14_16);
        assertThat(second.getAddedCount()).isEqualTo(1);
        assertThat(second.getAddedSlots()).containsExactly(SLOT_14_16);
        assertThat(second.getUnchangedCount()).isEqualTo(2);
        assertThat(second.getUnchangedSlots()).containsExactly(SLOT_10_12, SLOT_12_14);

        // 未传入的已有时段保留不动（增量式）
        batchSet(beauticianId, DATE, SLOT_16_18);
        assertThat(countSchedule(beauticianId, DATE)).isEqualTo(4);
        assertThat(countSchedule(beauticianId, DATE, SLOT_10_12)).isEqualTo(1);
    }

    @Test
    void batchSetShouldUpdateUnavailableToAvailable() {
        insertSchedule(beauticianId, DATE, SLOT_10_12, 0, null);

        ScheduleBatchSetVO result = batchSet(beauticianId, DATE, SLOT_10_12);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getUpdatedSlots()).containsExactly(SLOT_10_12);
        assertThat(queryAvailable(beauticianId, DATE, SLOT_10_12)).isEqualTo(1);
    }

    @Test
    void batchSetShouldSkipOccupiedSlots() {
        insertSchedule(beauticianId, DATE, SLOT_14_16, 0, 88888L);

        ScheduleBatchSetVO result = batchSet(beauticianId, DATE, SLOT_10_12, SLOT_14_16);

        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getAddedSlots()).containsExactly(SLOT_10_12);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getSkippedSlots()).hasSize(1);
        assertThat(result.getSkippedSlots().get(0).getTimeSlot()).isEqualTo(SLOT_14_16);
        assertThat(result.getSkippedSlots().get(0).getOrderId()).isEqualTo(88888L);
        assertThat(result.getSkippedSlots().get(0).getReason()).isNotBlank();

        // 被占用的排班未被改动
        assertThat(queryAvailable(beauticianId, DATE, SLOT_14_16)).isZero();
        assertThat(queryOrderId(beauticianId, DATE, SLOT_14_16)).isEqualTo(88888L);
    }

    @Test
    void batchSetShouldDeduplicateAndTrimSlots() {
        ScheduleBatchSetVO result = batchSet(beauticianId, DATE, SLOT_10_12, SLOT_10_12, "  " + SLOT_12_14 + " ");

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getAddedSlots()).containsExactly(SLOT_10_12, SLOT_12_14);
        assertThat(countSchedule(beauticianId, DATE)).isEqualTo(2);
    }

    @Test
    void batchSetShouldRejectIllegalTimeSlot() {
        assertThatThrownBy(() -> batchSet(beauticianId, DATE, "09:00-10:00"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法时段")
                .hasMessageContaining(SLOT_10_12);
    }

    @Test
    void batchSetShouldRejectEmptyTimeSlots() {
        ScheduleBatchSetRequest request = new ScheduleBatchSetRequest();
        request.setBeauticianId(beauticianId);
        request.setDate(DATE);
        request.setTimeSlots(Collections.emptyList());

        assertThatThrownBy(() -> scheduleService.batchSet(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("时段不能为空");
    }

    @Test
    void batchSetShouldNotAffectOtherBeauticians() {
        batchSet(beauticianId, DATE, SLOT_10_12);

        assertThat(countSchedule(otherBeauticianId, DATE)).isZero();

        batchSet(otherBeauticianId, DATE, SLOT_10_12);
        assertThat(countSchedule(otherBeauticianId, DATE)).isEqualTo(1);
        assertThat(countSchedule(beauticianId, DATE)).isEqualTo(1);
    }

    @Test
    void duplicateSlotShouldHitUniqueIndex() {
        insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null);

        // 规则：同一美容师同一日期同一时段不可重复（库层 uk_beautician_date_slot 生效）
        assertThatThrownBy(() -> insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null))
                .isInstanceOf(DuplicateKeyException.class);

        // 不同日期或不同美容师则可重复
        assertThat(insertSchedule(beauticianId, OTHER_DATE, SLOT_10_12, 1, null)).isNotNull();
        assertThat(insertSchedule(otherBeauticianId, DATE, SLOT_10_12, 1, null)).isNotNull();
    }

    @Test
    void timeSlotEnumShouldContainFiveFixedSlots() {
        assertThat(TimeSlot.allValues())
                .containsExactly(SLOT_10_12, SLOT_12_14, SLOT_14_16, SLOT_16_18, SLOT_18_20);
        assertThat(TimeSlot.isValid(SLOT_10_12)).isTrue();
        assertThat(TimeSlot.isValid("09:00-10:00")).isFalse();
        assertThat(TimeSlot.isValid(null)).isFalse();
    }

    // ==================== 查询 ====================

    @Test
    void listShouldReturnSlotsOrderedByTime() {
        batchSet(beauticianId, DATE, SLOT_18_20, SLOT_10_12);

        List<ScheduleVO> list = list(beauticianId, DATE);

        assertThat(list).extracting(ScheduleVO::getTimeSlot).containsExactly(SLOT_10_12, SLOT_18_20);
        assertThat(list).allSatisfy(vo -> {
            assertThat(vo.getBeauticianId()).isEqualTo(beauticianId);
            assertThat(vo.getDate()).isEqualTo(DATE);
            assertThat(vo.getAvailable()).isEqualTo(1);
            assertThat(vo.getOccupied()).isFalse();
            assertThat(vo.getCreateTime()).isNotNull();
        });
    }

    @Test
    void listShouldExposeOccupiedFlag() {
        insertSchedule(beauticianId, DATE, SLOT_10_12, 0, 66666L);

        List<ScheduleVO> list = list(beauticianId, DATE);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getOccupied()).isTrue();
        assertThat(list.get(0).getOrderId()).isEqualTo(66666L);
    }

    @Test
    void listShouldReturnEmptyForOtherDate() {
        batchSet(beauticianId, DATE, SLOT_10_12);

        assertThat(list(beauticianId, OTHER_DATE)).isEmpty();
        assertThat(list(otherBeauticianId, DATE)).isEmpty();
    }

    // ==================== 修改可用状态 ====================

    @Test
    void updateAvailableShouldSwitchBothWays() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null);

        scheduleService.updateAvailable(id, 0);
        assertThat(queryAvailable(beauticianId, DATE, SLOT_10_12)).isZero();

        scheduleService.updateAvailable(id, 1);
        assertThat(queryAvailable(beauticianId, DATE, SLOT_10_12)).isEqualTo(1);
    }

    @Test
    void updateAvailableShouldRejectOccupiedSlotToAvailable() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 0, 55555L);

        assertThatThrownBy(() -> scheduleService.updateAvailable(id, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被订单占用");
        // 原状态未被改变
        assertThat(queryOrderId(beauticianId, DATE, SLOT_10_12)).isEqualTo(55555L);
    }

    @Test
    void updateAvailableShouldAllowOccupiedSlotKeepUnavailable() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 0, 55555L);

        scheduleService.updateAvailable(id, 0);

        assertThat(queryAvailable(beauticianId, DATE, SLOT_10_12)).isZero();
        assertThat(queryOrderId(beauticianId, DATE, SLOT_10_12)).isEqualTo(55555L);
    }

    @Test
    void updateAvailableNonexistentScheduleShouldFail() {
        assertThatThrownBy(() -> scheduleService.updateAvailable(99999999L, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排班不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldSucceedWhenNotOccupied() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null);

        scheduleService.delete(id);

        assertThat(scheduleService.getById(id)).isNull();
    }

    @Test
    void deleteShouldSucceedWhenUnavailableButNotOccupied() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 0, null);

        scheduleService.delete(id);

        assertThat(scheduleService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectWhenOccupied() {
        Long id = insertSchedule(beauticianId, DATE, SLOT_10_12, 0, 44444L);

        assertThatThrownBy(() -> scheduleService.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被订单占用");
        assertThat(scheduleService.getById(id)).isNotNull();
    }

    @Test
    void deleteNonexistentScheduleShouldFail() {
        assertThatThrownBy(() -> scheduleService.delete(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排班不存在");
    }

    // ==================== 辅助方法 ====================

    private ScheduleBatchSetVO batchSet(Long beauticianId, LocalDate date, String... timeSlots) {
        ScheduleBatchSetRequest request = new ScheduleBatchSetRequest();
        request.setBeauticianId(beauticianId);
        request.setDate(date);
        request.setTimeSlots(Arrays.asList(timeSlots));
        return scheduleService.batchSet(request);
    }

    private List<ScheduleVO> list(Long beauticianId, LocalDate date) {
        ScheduleListQuery query = new ScheduleListQuery();
        query.setBeauticianId(beauticianId);
        query.setDate(date);
        return scheduleService.listByBeauticianAndDate(query);
    }

    private Long insertBeautician(String name) {
        jdbcTemplate.update("INSERT INTO beautician (name, skill_tags, score, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, NOW(), NOW())", name, "洗护", new BigDecimal("5.00"), 1);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician WHERE name = ?", Long.class, name);
    }

    private Long insertSchedule(Long beauticianId, LocalDate date, String timeSlot, int available, Long orderId) {
        jdbcTemplate.update("INSERT INTO beautician_schedule (beautician_id, date, time_slot, available, order_id, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", beauticianId, date, timeSlot, available, orderId);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                Long.class, beauticianId, date, timeSlot);
    }

    private int countSchedule(Long beauticianId, LocalDate date, String... timeSlots) {
        if (timeSlots.length == 0) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM beautician_schedule WHERE beautician_id = ? AND date = ?",
                    Integer.class, beauticianId, date);
            return count == null ? 0 : count;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                Integer.class, beauticianId, date, timeSlots[0]);
        return count == null ? 0 : count;
    }

    private Integer queryAvailable(Long beauticianId, LocalDate date, String timeSlot) {
        return jdbcTemplate.queryForObject(
                "SELECT available FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                Integer.class, beauticianId, date, timeSlot);
    }

    private Long queryOrderId(Long beauticianId, LocalDate date, String timeSlot) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT order_id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                beauticianId, date, timeSlot);
        return row.get("order_id") == null ? null : ((Number) row.get("order_id")).longValue();
    }

}
