package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置项，对应 application.yaml 中的 pethome.jwt
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求长度不少于 32 字节 */
    private String secret;

    /** token 有效期（秒），默认 24 小时 */
    private long expireSeconds = 86400L;

    /** 存放 token 的请求头名称 */
    private String header = "Authorization";

    /** token 前缀 */
    private String tokenPrefix = "Bearer ";

}
