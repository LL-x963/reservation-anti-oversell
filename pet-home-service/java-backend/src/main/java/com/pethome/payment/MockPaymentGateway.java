package com.pethome.payment;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.exception.BusinessException;
import com.pethome.config.PaymentProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * 模拟支付渠道。
 *
 * <p>用于未接入真实支付时跑通完整链路：预下单 → 回调 → 验签 → 幂等 → 更新订单。
 * 回调使用 HMAC-SHA256 对原始请求体签名，验签逻辑与真实渠道一致，
 * 接入微信 / 支付宝时只需替换实现类。</p>
 *
 * <p><b>该渠道不能用于真实收款</b>，生产启用需要显式配置 allow-mock-payment=true。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    /** 渠道标识 */
    public static final String CHANNEL = "mock";

    /** 回调签名请求头 */
    public static final String SIGN_HEADER = "X-Pay-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaymentProperties properties;

    public MockPaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PaymentPrepayResult prepay(PaymentPrepayCommand command) {
        ensureEnabled();
        return new PaymentPrepayResult(CHANNEL, command.orderId(), true, Map.of(
                "channel", CHANNEL,
                "orderNumber", command.orderNumber(),
                "amount", command.amount().toPlainString(),
                "notifyPath", "/api/payment/callback/" + CHANNEL));
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> headers, String body) {
        ensureEnabled();
        String signature = resolveSignature(headers);
        if (StringUtils.isBlank(signature)) {
            throw new BusinessException("支付回调缺少签名");
        }
        String expected = sign(body, properties.getCallbackSecret());
        // 常量时间比较，避免签名被时序侧信道猜测
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("支付回调签名校验失败");
        }

        JSONObject json;
        try {
            json = JSON.parseObject(body);
        } catch (Exception e) {
            throw new BusinessException("支付回调报文格式错误");
        }
        if (json == null) {
            throw new BusinessException("支付回调报文不能为空");
        }

        String orderNumber = json.getString("orderNumber");
        String tradeNo = json.getString("tradeNo");
        String amountText = json.getString("amount");
        Boolean success = json.getBoolean("success");
        if (StringUtils.isAnyBlank(orderNumber, tradeNo, amountText) || success == null) {
            throw new BusinessException("支付回调报文缺少必要字段");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText);
        } catch (NumberFormatException e) {
            throw new BusinessException("支付回调金额格式错误");
        }
        return new PaymentCallbackResult(orderNumber, tradeNo, amount, success);
    }

    /**
     * 生成回调签名，供模拟支付端（测试 / 联调页面）调用
     */
    public String sign(String body) {
        return sign(body, properties.getCallbackSecret());
    }

    private void ensureEnabled() {
        if (!properties.isAllowMockPayment()) {
            throw new BusinessException("模拟支付未启用，请接入真实支付渠道");
        }
        if (StringUtils.isBlank(properties.getCallbackSecret())) {
            throw new BusinessException("模拟支付回调密钥未配置");
        }
    }

    private String resolveSignature(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (SIGN_HEADER.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("支付回调签名计算失败", e);
            throw new BusinessException("支付回调签名计算失败");
        }
    }

}
