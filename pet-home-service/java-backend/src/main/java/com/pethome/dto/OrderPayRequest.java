package com.pethome.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单支付请求
 *
 * @author pet-home
 */
@Data
public class OrderPayRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 id */
    @NotNull(message = "订单id不能为空")
    @Min(value = 1, message = "订单id不合法")
    private Long orderId;

}
