package com.pethome.interceptor;

import com.alibaba.fastjson2.JSON;
import com.pethome.common.Result;
import com.pethome.common.annotation.RequireRole;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.EmployeeRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 管理端角色校验拦截器。
 *
 * <p>在 {@link AuthInterceptor} 完成身份校验之后执行，根据 {@link RequireRole} 注解判断
 * 当前员工是否具备操作权限。角色随 JWT 下发，无需每次请求查库。</p>
 *
 * @author pet-home
 */
@Slf4j
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required == null) {
            return true;
        }

        EmployeeRole actual = EmployeeRole.fromCode(UserContext.getRole());
        if (!actual.name().equalsIgnoreCase(required.value())) {
            log.warn("管理端角色权限不足: required={}, actual={}, uri={}",
                    required.value(), actual, request.getRequestURI());
            return forbidden(response);
        }
        return true;
    }

    private boolean forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(Result.error("无权限执行该操作")));
        return false;
    }

}
