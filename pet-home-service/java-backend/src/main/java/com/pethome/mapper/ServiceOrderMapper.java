package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.ServiceOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 服务预约单 Mapper
 *
 * <p>订单状态流转一律使用条件更新（CAS）：把「当前状态」作为 WHERE 条件，
 * 影响行数为 0 即表示状态已被其他请求改变。相比「先查再无条件改」，
 * 可以避免并发下的重复支付、重复确认、状态回退与重复评价。</p>
 *
 * @author pet-home
 */
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {

    /**
     * 支付：待支付(1) → 待确认(2)
     *
     * @return 影响行数，0 表示状态已变更
     */
    @Update("UPDATE service_order SET status = 2, pay_time = #{payTime}, update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 1")
    int payCas(@Param("orderId") Long orderId, @Param("payTime") LocalDateTime payTime);

    /**
     * 确认：待确认(2) → 待上门(3)
     *
     * @return 影响行数，0 表示状态已变更
     */
    @Update("UPDATE service_order SET status = 3, message_id = #{messageId}, update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 2")
    int confirmCas(@Param("orderId") Long orderId, @Param("messageId") String messageId);

    /**
     * 完成服务：待上门(3) / 服务中(4) → 待评价(7)
     *
     * @return 影响行数，0 表示状态已变更
     */
    @Update("UPDATE service_order SET status = 7, update_time = NOW() "
            + "WHERE id = #{orderId} AND status IN (3, 4)")
    int completeCas(@Param("orderId") Long orderId);

    /**
     * 取消：仅待支付(1) / 待确认(2) / 待上门(3) 允许，服务中及之后一律拒绝
     *
     * @return 影响行数，0 表示状态不允许取消
     */
    @Update("UPDATE service_order SET status = 6, reminder_status = 2, update_time = NOW() "
            + "WHERE id = #{orderId} AND status IN (1, 2, 3)")
    int cancelCas(@Param("orderId") Long orderId);

    /**
     * 评价：待评价(7) → 已完成(5)，用于防止并发重复评价
     *
     * @return 影响行数，0 表示已被评价或状态不符
     */
    @Update("UPDATE service_order SET status = 5, update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 7")
    int commentCas(@Param("orderId") Long orderId);

    /**
     * 标记已提醒：仅待上门(3) 且未提醒(0) 时生效，重复消费不会重复提醒
     *
     * @return 影响行数，0 表示已提醒过或状态不符
     */
    @Update("UPDATE service_order SET reminder_status = 1, update_time = NOW() "
            + "WHERE id = #{orderId} AND status = 3 AND reminder_status = 0")
    int markRemindedCas(@Param("orderId") Long orderId);

    /**
     * 改派美容师：仅待确认(2) / 待上门(3) 允许
     *
     * @return 影响行数，0 表示状态已变更
     */
    @Update("UPDATE service_order SET beautician_id = #{beauticianId}, update_time = NOW() "
            + "WHERE id = #{orderId} AND status IN (2, 3)")
    int assignCas(@Param("orderId") Long orderId, @Param("beauticianId") Long beauticianId);

    /**
     * 改派美容师并切换排班：仅待确认(2) / 待上门(3) 允许
     *
     * @return 影响行数，0 表示状态已变更
     */
    @Update("UPDATE service_order SET beautician_id = #{beauticianId}, schedule_id = #{scheduleId}, "
            + "update_time = NOW() WHERE id = #{orderId} AND status IN (2, 3)")
    int reassignCas(@Param("orderId") Long orderId, @Param("beauticianId") Long beauticianId,
                    @Param("scheduleId") Long scheduleId);

}
