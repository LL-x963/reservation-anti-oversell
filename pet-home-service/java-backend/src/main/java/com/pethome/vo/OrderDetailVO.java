package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单详情
 *
 * @author pet-home
 */
@Data
public class OrderDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNumber;

    private Long userId;

    /** 用户名（优先取姓名，姓名为空时回退手机号） */
    private String userName;

    private Long addressId;

    /** 收货联系人 */
    private String consignee;

    /** 收货联系电话 */
    private String consigneePhone;

    /** 完整收货地址（省市区 + 详细地址，地址已删除时为 null） */
    private String address;

    private Long petId;

    /** 宠物名 */
    private String petName;

    private Long beauticianId;

    /** 美容师姓名 */
    private String beauticianName;

    private Long scheduleId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    private String timeSlot;

    private BigDecimal totalAmount;

    /** 订单状态 */
    private Integer status;

    /** 订单状态描述 */
    private String statusDesc;

    /** MQ 消息 id */
    private String messageId;

    /** 提醒状态 0未提醒 1已提醒 2已取消 */
    private Integer reminderStatus;

    private LocalDateTime payTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 服务明细 */
    private List<OrderItemVO> items = new ArrayList<>();

    /** 评价信息，未评价为 null */
    private OrderCommentVO comment;

}
