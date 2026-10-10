package com.pethome.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 分配/改派美容师请求
 *
 * @author pet-home
 */
@Data
public class OrderAssignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 id */
    @NotNull(message = "订单id不能为空")
    @Min(value = 1, message = "订单id不合法")
    private Long orderId;

    /** 美容师 id */
    @NotNull(message = "美容师id不能为空")
    @Min(value = 1, message = "美容师id不合法")
    private Long beauticianId;

}
