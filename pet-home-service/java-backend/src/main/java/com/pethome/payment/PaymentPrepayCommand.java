package com.pethome.payment;

import java.math.BigDecimal;

/**
 * 预下单参数
 *
 * @param orderId     订单 id
 * @param orderNumber 订单号（渠道侧商户订单号）
 * @param amount      支付金额
 * @param subject     支付标题
 * @author pet-home
 */
public record PaymentPrepayCommand(Long orderId, String orderNumber, BigDecimal amount, String subject) {
}
