package com.pethome.config;

import com.pethome.common.enums.UserType;
import com.pethome.interceptor.AuthInterceptor;
import com.pethome.util.JwtUtil;
import com.pethome.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置：注册登录拦截器、跨域配置
 *
 * <p>按接口前缀区分身份：<br>
 * - /api/user/** 需要 C 端用户 token；<br>
 * - /api/admin/** 需要管理端员工 token。</p>
 *
 * @author pet-home
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 放行路径：登录接口、错误页、静态资源 */
    private static final String[] EXCLUDE_PATHS = {
            "/api/user/login",
            "/api/admin/login",
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
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

}
