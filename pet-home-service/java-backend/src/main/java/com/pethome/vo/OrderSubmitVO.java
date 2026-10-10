package com.pethome.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提交预约订单结果
 *
 * @author pet-home
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 id */
    private Long orderId;

    /** 订单号 */
    private String orderNumber;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 订单状态 1待支付 */
    private Integer status;

}
