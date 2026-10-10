package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务预约单表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_order")
public class ServiceOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号，全局唯一 */
    private String orderNumber;

    /** 下单用户 id */
    private Long userId;

    /** 服务地址 id */
    private Long addressId;

    /** 宠物 id */
    private Long petId;

    /** 美容师 id */
    private Long beauticianId;

    /** 占用的排班 id */
    private Long scheduleId;

    /** 预约日期 */
    private LocalDate appointmentDate;

    /** 预约时段 */
    private String timeSlot;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单状态 1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价 */
    private Integer status;

    /** MQ 消息 id，格式 cancel:{orderId} 或 remind:{orderId} */
    private String messageId;

    /** 提醒状态 0未提醒 1已提醒 2已取消 */
    private Integer reminderStatus;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 备注 */
    private String remark;

}
