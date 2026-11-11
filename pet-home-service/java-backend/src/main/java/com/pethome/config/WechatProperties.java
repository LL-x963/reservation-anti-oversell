package com.pethome.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 微信小程序登录配置，对应 application.yaml 中的 pethome.wechat
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.wechat")
public class WechatProperties {

    /** 小程序 appid */
    private String appid;

    /** 小程序 secret（仅服务端持有，绝不返回给前端） */
    private String secret;

    /** code2session 地址 */
    private String code2sessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    private Duration connectTimeout = Duration.ofSeconds(3);

    private Duration readTimeout = Duration.ofSeconds(5);

    /**
     * 是否已完成配置：appid 与 secret 都齐全才允许使用 code 登录
     */
    public boolean configured() {
        return StringUtils.isNotBlank(appid) && StringUtils.isNotBlank(secret);
    }

}
