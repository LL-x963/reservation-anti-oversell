package com.pethome.config;

import com.alibaba.fastjson2.JSON;
import com.pethome.common.Result;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 登录 / 注册接口限流。
 *
 * <p>按来源 IP 做固定窗口计数，防止登录、注册接口被高频刷取（撞库、批量注册）。
 * 计数器基于 Redis，多实例部署时同样生效。</p>
 *
 * <p>说明：这里只取 {@code request.getRemoteAddr()}，不信任可伪造的 X-Forwarded-For；
 * 部署在反向代理之后时应由代理设置可信的转发头并开启
 * {@code server.forward-headers-strategy}，届时 remoteAddr 已是真实客户端地址。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /** 受保护的路径 */
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/user/login",
            "/api/user/register",
            "/api/admin/login",
            "/api/admin/employee/login");

    private final RedisUtil redisUtil;

    private final AuthProperties authProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!PROTECTED_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        String key = RedisKeyConstant.loginRateKey(clientIp);
        long count = redisUtil.increment(key, 1);
        if (count == 1) {
            redisUtil.expire(key, 1, TimeUnit.MINUTES);
        }
        if (count > authProperties.getRateLimitPerMinute()) {
            log.warn("登录接口触发限流: ip={}, count={}", clientIp, count);
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        // Servlet API 未定义 429 常量，直接使用标准状态码
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(Result.error("操作过于频繁，请稍后再试")));
    }

}
