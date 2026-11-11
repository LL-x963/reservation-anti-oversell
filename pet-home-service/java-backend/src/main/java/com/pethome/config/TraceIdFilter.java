package com.pethome.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路追踪过滤器：为每个请求生成 traceId 写入 MDC 与响应头。
 *
 * <p>上游（Nginx / 网关）可透传 {@code X-Trace-Id}，本过滤器会校验格式后再使用，
 * 避免把任意字符串写进日志造成日志注入。</p>
 *
 * @author pet-home
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 允许的 traceId 格式：字母、数字、连字符，长度 8~64 */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private static final String HEADER = "X-Trace-Id";

    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 必须清理，避免线程池复用导致 traceId 串号
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(String incoming) {
        if (StringUtils.isNotBlank(incoming) && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

}
