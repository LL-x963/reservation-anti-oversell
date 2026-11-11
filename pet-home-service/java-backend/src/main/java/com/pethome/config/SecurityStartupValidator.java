package com.pethome.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 生产安全自检。
 *
 * <p>在应用启动完成后检查关键安全配置，不满足直接抛异常终止启动（fail-fast），
 * 避免带着默认密钥、弱口令或开放 CORS 上线。</p>
 *
 * <p>本地开发 / 自动化测试通过 {@code pethome.security.allow-insecure-dev-config=true} 跳过；
 * prod profile 下该开关必须为 false。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

    /** 常见弱口令，生产出现即拒绝启动 */
    private static final Set<String> WEAK_PASSWORDS = Set.of("123456", "root", "password", "admin", "guest");

    private final Environment environment;

    private final JwtProperties jwtProperties;

    @Override
    public void run(ApplicationArguments args) {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean allowInsecure = Boolean.parseBoolean(
                environment.getProperty("pethome.security.allow-insecure-dev-config", "false"));

        if (prodProfile && allowInsecure) {
            throw new IllegalStateException(
                    "prod profile 下 pethome.security.allow-insecure-dev-config 必须为 false，已终止启动");
        }
        if (allowInsecure) {
            log.warn("已跳过生产安全自检（allow-insecure-dev-config=true），该配置仅限本地开发与自动化测试");
            return;
        }

        List<String> errors = new ArrayList<>();

        String secret = jwtProperties.getSecret();
        if (StringUtils.isBlank(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add("pethome.jwt.secret 未配置或长度不足 32 字节");
        }

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (StringUtils.isBlank(dbPassword) || WEAK_PASSWORDS.contains(dbPassword)) {
            errors.add("spring.datasource.password 为空或使用了弱口令");
        }

        if (StringUtils.isBlank(environment.getProperty("spring.data.redis.password"))) {
            errors.add("spring.data.redis.password 未配置");
        }

        String corsOrigins = environment.getProperty("pethome.security.cors-allowed-origins");
        if (StringUtils.isBlank(corsOrigins) || corsOrigins.contains("*")) {
            errors.add("pethome.security.cors-allowed-origins 必须配置明确来源，且不能包含 *");
        }

        if (Boolean.parseBoolean(environment.getProperty("pethome.auth.allow-openid-direct-login", "false"))) {
            errors.add("pethome.auth.allow-openid-direct-login 必须为 false（openid 直登仅限本地开发）");
        }

        boolean ragEnabled = Boolean.parseBoolean(environment.getProperty("pethome.knowledge.enabled", "true"));
        if (ragEnabled && StringUtils.isBlank(environment.getProperty("pethome.knowledge.api-key"))) {
            errors.add("pethome.knowledge.api-key 未配置");
        }

        String payChannel = environment.getProperty("pethome.payment.channel", "mock");
        boolean allowMockPay = Boolean.parseBoolean(
                environment.getProperty("pethome.payment.allow-mock-payment", "false"));
        if ("mock".equalsIgnoreCase(payChannel) && !allowMockPay) {
            errors.add("支付渠道为 mock 但 pethome.payment.allow-mock-payment=false，支付不可用");
        }
        if ("mock".equalsIgnoreCase(payChannel) && allowMockPay) {
            log.warn("当前使用模拟支付渠道（allow-mock-payment=true），仅适用于内部演示，不能用于真实收款");
        }
        if (StringUtils.isBlank(environment.getProperty("pethome.payment.callback-secret"))) {
            errors.add("pethome.payment.callback-secret 未配置，支付回调无法验签");
        }

        if ("local".equalsIgnoreCase(environment.getProperty("pethome.upload.storage-type", "local"))) {
            log.warn("当前使用本地磁盘存储上传文件：容器重启或多实例部署会导致图片丢失，建议切换 storage-type=oss");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("生产安全检查未通过，已终止启动：\n - " + String.join("\n - ", errors));
        }
        log.info("生产安全自检通过：JWT 密钥、数据库 / Redis 口令、CORS、鉴权开关均为生产配置");
    }

}
