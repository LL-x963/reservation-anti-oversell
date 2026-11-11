package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关配置，对应 application.yaml 中的 pethome.security
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.security")
public class SecurityProperties {

    /**
     * 是否跳过生产安全自检（仅限本地开发 / 自动化测试）
     */
    private boolean allowInsecureDevConfig = false;

    /**
     * 允许跨域的来源，逗号分隔；生产必须是明确域名，不允许 *
     */
    private String corsAllowedOrigins = "";

}
