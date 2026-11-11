package com.pethome.payment;

import java.util.Map;

/**
 * 支付渠道网关抽象。
 *
 * <p>业务层只依赖本接口：接入微信支付 / 支付宝时新增一个实现类并配置
 * {@code pethome.payment.channel} 即可，订单与回调逻辑不需要改动。</p>
 *
 * @author pet-home
 */
public interface PaymentGateway {

    /**
     * 渠道标识，与配置项 pethome.payment.channel 对应
     */
    String channel();

    /**
     * 预下单：生成给客户端的支付参数
     *
     * @param command 预下单参数
     * @return 支付参数（真实渠道返回 prepay_id / 支付串，模拟渠道仅返回提示）
     */
    PaymentPrepayResult prepay(PaymentPrepayCommand command);

    /**
     * 校验并解析支付回调
     *
     * @param headers 回调请求头（验签需要）
     * @param body    回调原始请求体（必须用原始字符串验签，不能先用 JSON 反序列化）
     * @return 解析后的回调结果
     * @throws com.pethome.common.exception.BusinessException 验签失败或报文非法
     */
    PaymentCallbackResult verifyCallback(Map<String, String> headers, String body);

}
