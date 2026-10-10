package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_detail")
public class OrderDetail extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 id */
    private Long orderId;

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
