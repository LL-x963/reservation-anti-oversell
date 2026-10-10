package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 服务销量排行项
 *
 * @author pet-home
 */
@Data
public class ServiceRankVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long serviceId;

    /** 服务名称 */
    private String name;

    /** 销量（已完成订单的服务数量合计） */
    private Long sales;

    /** 销售额 */
    private BigDecimal amount;

    /** 销量占比（0~1，保留 4 位小数，分母为全部服务总销量） */
    private BigDecimal ratio;

}
