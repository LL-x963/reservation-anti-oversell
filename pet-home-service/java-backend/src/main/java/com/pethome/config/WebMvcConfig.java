package com.pethome.config;

import com.pethome.common.enums.UserType;
import com.pethome.interceptor.AuthInterceptor;
import com.pethome.interceptor.RoleInterceptor;
import com.pethome.util.JwtUtil;
import com.pethome.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * WebMvc 配置：注册登录 / 角色拦截器、跨域配置
 *
 * <p>按接口前缀区分身份：<br>
 * - /api/user/** 需要 C 端用户 token；<br>
 * - /api/admin/** 需要管理端员工 token，并按 {@code @RequireRole} 校验角色。</p>
 *
 * @author pet-home
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 放行路径：登录 / 注册接口（含别名）、错误页、静态资源 */
    private static final String[] EXCLUDE_PATHS = {
            "/api/user/login",
            "/api/user/register",
            "/api/admin/login",
            // 管理后台登录页使用的别名路径
            "/api/admin/employee/login",
            "/error",
            "/favicon.ico",
            "/static/**",
            "/doc.html",
            "/webjars/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final JwtProperties jwtProperties;
    private final UploadProperties uploadProperties;
    private final SecurityProperties securityProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // C 端用户接口
        registry.addInterceptor(new AuthInterceptor(jwtUtil, redisUtil, jwtProperties, UserType.USER))
                .addPathPatterns("/api/user/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(1);

        // 管理端员工接口
        registry.addInterceptor(new AuthInterceptor(jwtUtil, redisUtil, jwtProperties, UserType.EMPLOYEE))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(2);

        // 管理端角色校验：必须在身份校验之后执行
        registry.addInterceptor(new RoleInterceptor())
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(EXCLUDE_PATHS)
                .order(3);
    }

    /**
     * 上传目录静态资源映射，使上传后的图片可通过 urlPrefix 直接访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(uploadProperties.normalizedUrlPrefix() + "**")
                .addResourceLocations(location);
    }

    /**
     * 跨域配置：只放行 pethome.security.cors-allowed-origins 中显式配置的来源。
     *
     * <p>未配置时不放行任何跨域来源（浏览器同源策略生效），避免默认全开。
     * 生产环境由启动自检保证该配置非空且不含 *。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = securityProperties.getCorsAllowedOrigins();
        if (StringUtils.isBlank(origins)) {
            return;
        }
        String[] patterns = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toArray(String[]::new);
        if (patterns.length == 0) {
            return;
        }
        registry.addMapping("/**")
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
