package com.pethome.payment;

import java.util.Map;

/**
 * 预下单结果
 *
 * @param channel   渠道标识
 * @param orderId   订单 id
 * @param mock      是否为模拟支付（前端据此决定是否直接触发模拟回调）
 * @param payParams 渠道支付参数（真实渠道为 prepay_id / 支付串等）
 * @author pet-home
 */
public record PaymentPrepayResult(String channel, Long orderId, boolean mock, Map<String, String> payParams) {
}
