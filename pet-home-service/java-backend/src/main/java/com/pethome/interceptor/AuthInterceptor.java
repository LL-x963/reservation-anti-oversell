package com.pethome.interceptor;

import com.alibaba.fastjson2.JSON;
import com.pethome.common.Result;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.UserType;
import com.pethome.config.JwtProperties;
import com.pethome.util.JwtUtil;
import com.pethome.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器
 *
 * <p>校验流程：请求头取 token → 校验签名与有效期 → 校验身份类型 → 校验 Redis 白名单是否一致。</p>
 *
 * <p>同一个类通过构造参数 {@link UserType} 复用，分别注册到 /api/user/** 与 /api/admin/**。</p>
 *
 * @author pet-home
 */
@Slf4j
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final JwtProperties jwtProperties;
    private final UserType userType;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 静态资源等非 Controller 请求直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = jwtUtil.resolveToken(request.getHeader(jwtProperties.getHeader()));
        if (StringUtils.isBlank(token)) {
            return unauthorized(response, "未登录，请先登录");
        }

        if (!jwtUtil.validateToken(token)) {
            return unauthorized(response, "token 无效或已过期，请重新登录");
        }

        if (jwtUtil.parseUserType(token) != userType) {
            return unauthorized(response, "token 身份与接口不匹配");
        }

        Long userId = jwtUtil.parseUserId(token);
        Object cachedToken = redisUtil.get(RedisKeyConstant.loginTokenKey(userType, userId));
        if (cachedToken == null || !token.equals(String.valueOf(cachedToken))) {
            return unauthorized(response, "登录状态已失效，请重新登录");
        }

        // 员工角色随 token 下发，供 RoleInterceptor 做权限校验
        UserContext.set(userId, userType, jwtUtil.parseRole(token));
        log.debug("鉴权通过: userType={}, userId={}, uri={}", userType, userId, request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理，避免线程复用导致用户信息串号
        UserContext.clear();
    }

    private boolean unauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(Result.error(msg)));
        return false;
    }

}
