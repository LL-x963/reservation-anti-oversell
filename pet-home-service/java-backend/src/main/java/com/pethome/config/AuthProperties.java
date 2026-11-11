package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全配置，对应 application.yaml 中的 pethome.auth
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.auth")
public class AuthProperties {

    /**
     * 是否允许客户端直接传 openid 登录。
     *
     * <p>仅供本地开发与自动化测试使用，生产必须为 false（prod profile 固定 false，
     * 且 SecurityStartupValidator 会拒绝启动）。</p>
     */
    private boolean allowOpenidDirectLogin = false;

    /** 连续登录失败多少次后锁定 */
    private int maxFailCount = 5;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;

    /** 同一 IP 每分钟允许的登录请求次数 */
    private int rateLimitPerMinute = 20;

}
