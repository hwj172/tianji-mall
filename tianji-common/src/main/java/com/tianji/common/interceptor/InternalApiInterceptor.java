package com.tianji.common.interceptor;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * 内部 API 鉴权过滤器。
 * 用于业务服务侧校验 X-Internal-Token，防御 Feign 调用绕过 Gateway 直连服务实例。
 *
 * 实现 jakarta.servlet.Filter（而非 HandlerInterceptor），
 * 避免 tianji-common 依赖 spring-webmvc（与 Gateway WebFlux 冲突）。
 */
@Slf4j
public class InternalApiInterceptor implements Filter {

    private final String internalToken;

    public InternalApiInterceptor(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 未配置 internal.token 时拒绝所有内部请求（fail-closed）
        if (internalToken == null || internalToken.isEmpty()) {
            log.error("internal.token 未配置，拒绝内部请求: {}", request.getRequestURI());
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"msg\":\"内部服务配置错误\"}");
            return;
        }

        String token = request.getHeader("X-Internal-Token");
        if (!internalToken.equals(token)) {
            log.warn("内部接口 token 无效: {}", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未授权\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
