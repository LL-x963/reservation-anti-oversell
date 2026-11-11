package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水
 *
 * <p>用于回调幂等（uk_trade_no）与对账：即使订单状态被并发改动，
 * 也能根据流水判断支付是否真实到账。</p>
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
public class PaymentRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 id */
    private Long orderId;

    /** 商户订单号 */
    private String orderNumber;

    /** 支付渠道 */
    private String channel;

    /** 渠道交易号（唯一，用于回调幂等） */
    private String tradeNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 状态：0 待支付 1 已支付 2 已关闭 */
    private Integer status;

    /** 回调原始报文（截断保存，便于排查） */
    private String callbackBody;

    /** 回调时间 */
    private LocalDateTime callbackTime;

}
