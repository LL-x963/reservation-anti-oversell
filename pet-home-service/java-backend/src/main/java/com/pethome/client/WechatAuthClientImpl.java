package com.pethome.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.exception.BusinessException;
import com.pethome.config.WechatProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信 code2session 实现
 *
 * <p>注意：请求 URL 中包含 secret（微信接口要求的传参方式），日志中绝不打印完整 URL。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
public class WechatAuthClientImpl implements WechatAuthClient {

    /** 微信侧 code 无效 / 已使用的错误码 */
    private static final int ERRCODE_INVALID_CODE = 40029;

    /** 微信侧 code 已被使用的错误码 */
    private static final int ERRCODE_CODE_USED = 40163;

    private final WechatProperties properties;

    private final RestTemplate restTemplate;

    public WechatAuthClientImpl(WechatProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .build();
    }

    @Override
    public String code2Openid(String code) {
        if (!properties.configured()) {
            // 未配置 appid/secret 时直接禁用该入口，不允许退化为信任客户端 openid
            throw new BusinessException("微信登录未启用，请使用手机号登录");
        }
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("微信登录 code 不能为空");
        }

        String url = UriComponentsBuilder.fromUriString(properties.getCode2sessionUrl())
                .queryParam("appid", properties.getAppid())
                .queryParam("secret", properties.getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .encode()
                .toUriString();

        String body;
        try {
            body = restTemplate.getForObject(url, String.class);
        } catch (RestClientException e) {
            log.error("调用微信 code2session 失败: 网络异常", e);
            throw new BusinessException("微信登录服务暂时不可用，请稍后重试");
        }

        JSONObject json = StringUtils.isBlank(body) ? null : JSON.parseObject(body);
        if (json == null) {
            log.warn("微信 code2session 返回空响应");
            throw new BusinessException("微信登录失败，请重新授权");
        }

        Integer errcode = json.getInteger("errcode");
        if (errcode != null && errcode != 0) {
            String errmsg = json.getString("errmsg");
            log.warn("微信 code2session 返回错误: errcode={}, errmsg={}", errcode, errmsg);
            if (errcode == ERRCODE_INVALID_CODE || errcode == ERRCODE_CODE_USED) {
                throw new BusinessException("微信登录凭证已失效，请重新授权");
            }
            throw new BusinessException("微信登录失败，请重新授权");
        }

        String openid = json.getString("openid");
        if (StringUtils.isBlank(openid)) {
            log.warn("微信 code2session 响应中缺少 openid");
            throw new BusinessException("微信登录失败：未获取到用户标识");
        }
        return openid;
    }

}
