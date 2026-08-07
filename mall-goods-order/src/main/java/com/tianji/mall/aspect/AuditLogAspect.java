package com.tianji.mall.aspect;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.annotation.AuditLog;
import com.tianji.mall.entity.OperationLog;
import com.tianji.mall.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作审计日志切面。
 * 拦截标注了 {@link AuditLog} 的 Controller 写方法，方法成功返回后写入 operation_log。
 * best-effort：解析 JWT / 写库任何一步失败都仅记录日志，不抛出异常影响主流程。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final OperationLogService operationLogService;
    private final JwtUtil jwtUtil;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            writeAuditLog(joinPoint, auditLog);
        } catch (Exception e) {
            log.error("写入操作审计日志失败: action={}, targetType={}, error={}",
                    auditLog.action(), auditLog.targetType(), e.getMessage(), e);
        }
        return result;
    }

    private void writeAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();

        OperationLog opLog = new OperationLog();
        resolveOperator(request, opLog);
        opLog.setAction(auditLog.action());
        opLog.setTargetType(auditLog.targetType());
        opLog.setTargetId(resolveTargetId(joinPoint, auditLog));
        opLog.setDetail(buildDetail(joinPoint));
        opLog.setIp(request.getRemoteAddr());

        operationLogService.save(opLog);
    }

    /** 优先从 Authorization JWT 解析，失败则回退到 Gateway 注入的 X-User-Id / X-User-Role 请求头 */
    private void resolveOperator(HttpServletRequest request, OperationLog opLog) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                opLog.setOperatorId(jwtUtil.getUserId(token));
                opLog.setOperatorRole(jwtUtil.getRole(token));
                return;
            } catch (Exception e) {
                log.warn("解析操作人 JWT 失败: {}", e.getMessage());
            }
        }
        String xUserId = request.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                opLog.setOperatorId(Long.parseLong(xUserId));
            } catch (NumberFormatException ignored) {
            }
        }
        opLog.setOperatorRole(request.getHeader("X-User-Role"));
    }

    private Long resolveTargetId(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        if (auditLog.targetArg() < 0) {
            return null;
        }
        Object[] args = joinPoint.getArgs();
        if (auditLog.targetArg() >= args.length) {
            return null;
        }
        Object arg = args[auditLog.targetArg()];
        if (arg instanceof Number) {
            return ((Number) arg).longValue();
        }
        if (arg instanceof String) {
            try {
                return Long.parseLong((String) arg);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** 方法名 + 参数摘要，截断至 512 字符 */
    private String buildDetail(ProceedingJoinPoint joinPoint) {
        String method = joinPoint.getSignature().getName();
        StringBuilder sb = new StringBuilder(method).append('(');
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (args[i] == null) {
                sb.append("null");
            } else {
                String s = String.valueOf(args[i]);
                if (s.length() > 80) {
                    s = s.substring(0, 80) + "...";
                }
                sb.append(s);
            }
        }
        sb.append(')');
        return sb.length() > 512 ? sb.substring(0, 512) : sb.toString();
    }
}
