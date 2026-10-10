package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单列表项
 *
 * @author pet-home
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String orderNumber;

    private Long userId;

    private Long petId;

    private Long beauticianId;

    /** 美容师姓名 */
    private String beauticianName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    private String timeSlot;

    private BigDecimal totalAmount;

    /** 订单状态 1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价 */
    private Integer status;

    /** 订单状态描述 */
    private String statusDesc;

    private LocalDateTime createTime;

}
