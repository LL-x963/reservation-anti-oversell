package com.pethome.controller;

import com.pethome.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付渠道回调接口
 *
 * <p>该接口由支付渠道服务端调用，不经过登录拦截器（路径前缀不是 /api/user、/api/admin），
 * 安全性依赖回调验签。</p>
 *
 * <p>返回纯文本 success 是渠道侧约定的应答格式，验签失败会抛业务异常并返回错误信息，
 * 渠道会按自身策略重试。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 支付回调
     *
     * @param channel 渠道标识，如 mock
     * @param headers 请求头（含签名）
     * @param body    原始报文（必须用原始字符串验签）
     */
    @PostMapping("/callback/{channel}")
    public String callback(@PathVariable String channel,
                           @RequestHeader Map<String, String> headers,
                           @RequestBody String body) {
        paymentService.handleCallback(channel, headers, body);
        return "success";
    }

}
