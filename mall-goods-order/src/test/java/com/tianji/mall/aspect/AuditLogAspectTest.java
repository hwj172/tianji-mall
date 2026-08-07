package com.tianji.mall.aspect;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.annotation.AuditLog;
import com.tianji.mall.entity.OperationLog;
import com.tianji.mall.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogAspectTest {

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditLogAspect(operationLogService, jwtUtil);
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setRequest(String authorization, String xUserId, String xUserRole) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        if (xUserId != null) {
            request.addHeader("X-User-Id", xUserId);
        }
        if (xUserRole != null) {
            request.addHeader("X-User-Role", xUserRole);
        }
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void mockProceed(String methodName, Object... args) throws Throwable {
        when(joinPoint.proceed()).thenReturn("OK");
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
    }

    private AuditLog auditLog(String action, String targetType, int targetArg) {
        return new AuditLog() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AuditLog.class;
            }

            @Override
            public String action() {
                return action;
            }

            @Override
            public String targetType() {
                return targetType;
            }

            @Override
            public int targetArg() {
                return targetArg;
            }
        };
    }

    @Test
    void shouldWriteAuditLogOnSuccess() throws Throwable {
        setRequest("Bearer token123", null, null);
        when(jwtUtil.getUserId("token123")).thenReturn(42L);
        when(jwtUtil.getRole("token123")).thenReturn("admin");
        mockProceed("deleteProduct", 100L);

        Object result = aspect.around(joinPoint, auditLog("delete_product", "product", 0));

        assertEquals("OK", result);
        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        OperationLog opLog = captor.getValue();
        assertEquals(42L, opLog.getOperatorId());
        assertEquals("admin", opLog.getOperatorRole());
        assertEquals("delete_product", opLog.getAction());
        assertEquals("product", opLog.getTargetType());
        assertEquals(100L, opLog.getTargetId());
        assertEquals("127.0.0.1", opLog.getIp());
        assertNotNull(opLog.getDetail());
    }

    @Test
    void shouldResolveTargetIdFromArgIndex() throws Throwable {
        setRequest("Bearer token123", null, null);
        when(jwtUtil.getUserId("token123")).thenReturn(1L);
        when(jwtUtil.getRole("token123")).thenReturn("admin");
        // Seller updateProduct 签名：authHeader, id, product → targetArg=1 指向 id
        mockProceed("updateProduct", "Bearer x", 88L, mock(Object.class));

        aspect.around(joinPoint, auditLog("update_product", "product", 1));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals(88L, captor.getValue().getTargetId());
    }

    @Test
    void shouldSkipTargetIdWhenArgIndexIsMinusOne() throws Throwable {
        setRequest("Bearer token123", null, null);
        when(jwtUtil.getUserId("token123")).thenReturn(1L);
        when(jwtUtil.getRole("token123")).thenReturn("admin");
        mockProceed("createProduct", "some body");

        aspect.around(joinPoint, auditLog("create_product", "product", -1));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals(null, captor.getValue().getTargetId());
    }

    @Test
    void shouldFallbackToGatewayHeadersWhenJwtFails() throws Throwable {
        setRequest("Bearer bad-token", "7", "seller");
        when(jwtUtil.getUserId("bad-token")).thenThrow(new RuntimeException("invalid token"));
        mockProceed("shipOrder", "Bearer bad-token", 88L, mock(Object.class));

        aspect.around(joinPoint, auditLog("ship_order", "order", 1));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).save(captor.capture());
        assertEquals(7L, captor.getValue().getOperatorId());
        assertEquals("seller", captor.getValue().getOperatorRole());
        assertEquals(88L, captor.getValue().getTargetId());
    }

    @Test
    void shouldNotThrowWhenSaveFails() throws Throwable {
        setRequest("Bearer token123", null, null);
        when(jwtUtil.getUserId("token123")).thenReturn(1L);
        when(jwtUtil.getRole("token123")).thenReturn("admin");
        mockProceed("createProduct");
        doThrow(new RuntimeException("db down")).when(operationLogService).save(any(OperationLog.class));

        Object result = aspect.around(joinPoint, auditLog("create_product", "product", -1));

        assertEquals("OK", result);
        verify(operationLogService).save(any(OperationLog.class));
    }

    @Test
    void shouldSkipAuditWhenNoRequestContext() throws Throwable {
        // 不设置 RequestContext，切面只 proceed 不写库
        when(joinPoint.proceed()).thenReturn("OK");

        Object result = aspect.around(joinPoint, auditLog("delete_product", "product", 0));

        assertEquals("OK", result);
        verify(operationLogService, never()).save(any());
    }
}
