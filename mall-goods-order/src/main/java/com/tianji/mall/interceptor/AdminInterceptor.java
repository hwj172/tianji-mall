package com.tianji.mall.interceptor;

import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.annotation.RequireAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器。
 * 对于标注了 @RequireAdmin 的 Controller 方法，校验 X-User-Role 请求头是否为 "admin"。
 * Gateway 层已做第一道鉴权，这里做第二道保障。
 */
@Slf4j
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 检查方法或类上的 @RequireAdmin 注解
        RequireAdmin methodAnnotation = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        RequireAdmin classAnnotation = null;
        if (handlerMethod.getBeanType() != null) {
            classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        }

        if (methodAnnotation == null && classAnnotation == null) {
            return true;
        }

        String role = request.getHeader("X-User-Role");
        if (!"admin".equals(role)) {
            log.warn("非管理员请求 admin 接口: {} {}, role={}",
                    request.getMethod(), request.getRequestURI(), role);
            throw new BizException(BizErrorCode.ADMIN_REQUIRED);
        }

        return true;
    }
}
