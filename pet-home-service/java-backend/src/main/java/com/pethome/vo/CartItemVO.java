package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预约车条目（含服务详情）
 *
 * @author pet-home
 */
@Data
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务项目 id */
    private Long serviceId;

    /** 服务名称 */
    private String name;

    /** 服务价格 */
    private BigDecimal price;

    /** 服务图片 */
    private String image;

    /** 数量 */
    private Integer quantity;

    /** 小计金额 = 价格 × 数量 */
    private BigDecimal amount;

}
