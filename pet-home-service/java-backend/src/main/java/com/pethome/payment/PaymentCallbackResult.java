package com.pethome.payment;

import java.math.BigDecimal;

/**
 * 支付回调解析结果
 *
 * @param orderNumber 商户订单号
 * @param tradeNo     渠道交易号（用于回调幂等与对账）
 * @param amount      实付金额
 * @param success     渠道是否判定支付成功
 * @author pet-home
 */
public record PaymentCallbackResult(String orderNumber, String tradeNo, BigDecimal amount, boolean success) {
}
