package com.pethome.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.TimeSlot;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.ScheduleBatchSetRequest;
import com.pethome.dto.ScheduleListQuery;
import com.pethome.entity.BeauticianSchedule;
import com.pethome.mapper.BeauticianScheduleMapper;
import com.pethome.service.BeauticianScheduleService;
import com.pethome.vo.ScheduleBatchSetVO;
import com.pethome.vo.ScheduleSkippedVO;
import com.pethome.vo.ScheduleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 美容师排班服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
public class BeauticianScheduleServiceImpl extends ServiceImpl<BeauticianScheduleMapper, BeauticianSchedule>
        implements BeauticianScheduleService {

    /** 可接单 */
    private static final int AVAILABLE = 1;
    /** 已占用 */
    private static final int UNAVAILABLE = 0;
    /** 跳过原因：已被订单占用 */
    private static final String REASON_OCCUPIED = "该时段已被订单占用，需先取消或改派订单";

    @Override
    public List<ScheduleVO> listByBeauticianAndDate(ScheduleListQuery query) {
        return lambdaQuery()
                .eq(BeauticianSchedule::getBeauticianId, query.getBeauticianId())
                .eq(BeauticianSchedule::getDate, query.getDate())
                // 固定时段字面值按字符串升序即为时间先后顺序
                .orderByAsc(BeauticianSchedule::getTimeSlot)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleBatchSetVO batchSet(ScheduleBatchSetRequest request) {
        // 0. 防御：Service 直接调用时也需保证时段非空（同时避免 IN () 空集合）
        if (CollectionUtils.isEmpty(request.getTimeSlots())) {
            throw new BusinessException("时段不能为空");
        }

        // 1. 校验时段合法性并去重（保持传入顺序）
        Set<String> timeSlots = new LinkedHashSet<>();
        for (String timeSlot : request.getTimeSlots()) {
            String trimmed = timeSlot == null ? null : timeSlot.trim();
            if (!TimeSlot.isValid(trimmed)) {
                throw new BusinessException("非法时段：" + timeSlot + "，可选值：" + TimeSlot.allValuesAsText());
            }
            timeSlots.add(trimmed);
        }

        // 2. 一次性查出该美容师该日期下涉及的已有时段
        Map<String, BeauticianSchedule> existingMap = lambdaQuery()
                .eq(BeauticianSchedule::getBeauticianId, request.getBeauticianId())
                .eq(BeauticianSchedule::getDate, request.getDate())
                .in(BeauticianSchedule::getTimeSlot, timeSlots)
                .list()
                .stream()
                .collect(Collectors.toMap(BeauticianSchedule::getTimeSlot, Function.identity(), (a, b) -> a));

        ScheduleBatchSetVO result = new ScheduleBatchSetVO();
        result.setBeauticianId(request.getBeauticianId());
        result.setDate(request.getDate());
        result.setTotal(timeSlots.size());

        try {
            for (String timeSlot : timeSlots) {
                BeauticianSchedule existing = existingMap.get(timeSlot);
                if (existing == null) {
                    save(buildNewSchedule(request, timeSlot));
                    result.getAddedSlots().add(timeSlot);
                } else if (isOccupied(existing)) {
                    // 已被订单占用：跳过，不影响其余时段
                    result.getSkippedSlots()
                            .add(new ScheduleSkippedVO(timeSlot, existing.getOrderId(), REASON_OCCUPIED));
                } else if (Integer.valueOf(AVAILABLE).equals(existing.getAvailable())) {
                    result.getUnchangedSlots().add(timeSlot);
                } else {
                    updateAvailableInternal(existing.getId(), AVAILABLE);
                    result.getUpdatedSlots().add(timeSlot);
                }
            }
        } catch (DuplicateKeyException e) {
            // 兜底：并发下唯一索引 uk_beautician_date_slot 冲突，统一转为友好提示
            log.warn("批量设置排班唯一索引冲突: beauticianId={}, date={}", request.getBeauticianId(),
                    request.getDate(), e);
            throw new BusinessException("该美容师该日期已存在相同时段，请刷新后重试");
        }

        result.setAddedCount(result.getAddedSlots().size());
        result.setUpdatedCount(result.getUpdatedSlots().size());
        result.setUnchangedCount(result.getUnchangedSlots().size());
        result.setSkippedCount(result.getSkippedSlots().size());

        log.info("批量设置排班完成: beauticianId={}, date={}, total={}, added={}, updated={}, unchanged={}, skipped={}",
                request.getBeauticianId(), request.getDate(), result.getTotal(), result.getAddedCount(),
                result.getUpdatedCount(), result.getUnchangedCount(), result.getSkippedCount());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAvailable(Long id, Integer available) {
        BeauticianSchedule existing = getById(id);
        if (existing == null) {
            throw new BusinessException("排班不存在");
        }
        if (Integer.valueOf(AVAILABLE).equals(available) && isOccupied(existing)) {
            throw new BusinessException(REASON_OCCUPIED + "，不能直接设为可接单");
        }
        updateAvailableInternal(id, available);
        log.info("修改排班可用状态成功: id={}, available={}", id, available);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BeauticianSchedule existing = getById(id);
        if (existing == null) {
            throw new BusinessException("排班不存在");
        }
        if (isOccupied(existing)) {
            throw new BusinessException(REASON_OCCUPIED + "，不能删除");
        }
        removeById(id);
        log.info("删除排班成功: id={}", id);
    }

    private BeauticianSchedule buildNewSchedule(ScheduleBatchSetRequest request, String timeSlot) {
        BeauticianSchedule entity = new BeauticianSchedule();
        entity.setBeauticianId(request.getBeauticianId());
        entity.setDate(request.getDate());
        entity.setTimeSlot(timeSlot);
        entity.setAvailable(AVAILABLE);
        return entity;
    }

    private void updateAvailableInternal(Long id, Integer available) {
        BeauticianSchedule entity = new BeauticianSchedule();
        entity.setId(id);
        entity.setAvailable(available);
        updateById(entity);
    }

    /**
     * 是否已被订单占用：available=0 且 order_id 不为空
     */
    private boolean isOccupied(BeauticianSchedule schedule) {
        return Integer.valueOf(UNAVAILABLE).equals(schedule.getAvailable()) && schedule.getOrderId() != null;
    }

    private ScheduleVO toVO(BeauticianSchedule entity) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(entity.getId());
        vo.setBeauticianId(entity.getBeauticianId());
        vo.setDate(entity.getDate());
        vo.setTimeSlot(entity.getTimeSlot());
        vo.setAvailable(entity.getAvailable());
        vo.setOrderId(entity.getOrderId());
        vo.setOccupied(isOccupied(entity));
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
