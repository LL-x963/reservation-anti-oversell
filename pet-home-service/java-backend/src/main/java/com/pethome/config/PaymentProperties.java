package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付配置，对应 application.yaml 中的 pethome.payment
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.payment")
public class PaymentProperties {

    /**
     * 支付渠道：mock 模拟渠道 / wechat 微信支付 / alipay 支付宝
     *
     * <p>当前仅实现 mock，其余渠道接入时实现 {@code PaymentGateway} 即可，业务层无需改动。</p>
     */
    private String channel = "mock";

    /**
     * 是否允许使用模拟支付。
     *
     * <p>生产环境若未接入真实渠道，必须显式设为 true 才能启动，且启动日志会给出告警；
     * 默认 false（安全默认）。</p>
     */
    private boolean allowMockPayment = false;

    /**
     * 回调验签密钥（mock 渠道用 HMAC-SHA256 对请求体签名）
     */
    private String callbackSecret = "";

    /**
     * 预下单有效期（分钟），超时未支付的流水会被对账任务关闭
     */
    private int prepayExpireMinutes = 30;

    /**
     * 对账任务执行间隔（毫秒）
     */
    private long reconcileIntervalMillis = 300_000L;

}
