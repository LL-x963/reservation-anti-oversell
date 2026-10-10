package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.BeauticianSchedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 美容师排班 Mapper
 *
 * @author pet-home
 */
public interface BeauticianScheduleMapper extends BaseMapper<BeauticianSchedule> {

    /**
     * 占用排班时段（CAS 乐观锁）：仅当该时段仍可接单时占用成功
     *
     * @param scheduleId 排班 id
     * @param orderId    订单 id
     * @return 影响行数，0 表示已被并发抢占
     */
    @Update("UPDATE beautician_schedule SET available = 0, order_id = #{orderId} "
            + "WHERE id = #{scheduleId} AND available = 1")
    int occupySlot(@Param("scheduleId") Long scheduleId, @Param("orderId") Long orderId);

    /**
     * 释放排班时段：仅释放被指定订单占用的那条记录
     *
     * @param scheduleId 排班 id
     * @param orderId    订单 id
     * @return 影响行数
     */
    @Update("UPDATE beautician_schedule SET available = 1, order_id = NULL "
            + "WHERE id = #{scheduleId} AND order_id = #{orderId}")
    int releaseSlot(@Param("scheduleId") Long scheduleId, @Param("orderId") Long orderId);

    /**
     * 按订单释放排班（订单未记录排班 id 时的兜底）
     *
     * @param orderId 订单 id
     * @return 影响行数
     */
    @Update("UPDATE beautician_schedule SET available = 1, order_id = NULL WHERE order_id = #{orderId}")
    int releaseByOrderId(@Param("orderId") Long orderId);

}
