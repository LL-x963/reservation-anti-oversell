package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细项
 *
 * @author pet-home
 */
@Data
public class OrderItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务项目 id */
    private Long serviceId;

    /** 服务名称（下单快照） */
    private String name;

    /** 服务价格（下单快照） */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计金额 */
    private BigDecimal amount;

}
