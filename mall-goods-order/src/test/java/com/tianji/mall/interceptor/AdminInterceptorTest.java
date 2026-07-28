package com.tianji.mall.interceptor;

import com.tianji.common.exception.BizException;
import com.tianji.mall.annotation.RequireAdmin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInterceptor 单元测试")
class AdminInterceptorTest {

    private AdminInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() {
        interceptor = new AdminInterceptor();
    }

    @Test
    @DisplayName("非 HandlerMethod 直接放行（如静态资源请求）")
    void shouldPassThroughNonHandlerMethod() {
        boolean result = interceptor.preHandle(request, response, new Object());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("无 @RequireAdmin 注解的普通方法直接放行")
    void shouldPassThroughWithoutAnnotation() throws NoSuchMethodException {
        // Arrange：一个普通方法，没有 @RequireAdmin
        when(handlerMethod.getMethodAnnotation(RequireAdmin.class)).thenReturn(null);
        doReturn((Class) TestController.class).when(handlerMethod).getBeanType();

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("admin 角色访问 @RequireAdmin 方法放行")
    void shouldAllowAdminRole() throws NoSuchMethodException {
        when(handlerMethod.getMethodAnnotation(RequireAdmin.class))
                .thenReturn(TestController.class.getMethod("adminMethod").getAnnotation(RequireAdmin.class));
        when(request.getHeader("X-User-Role")).thenReturn("admin");

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("普通 user 角色访问 @RequireAdmin 方法抛 BizException(403)")
    void shouldRejectUserRole() throws NoSuchMethodException {
        when(handlerMethod.getMethodAnnotation(RequireAdmin.class))
                .thenReturn(TestController.class.getMethod("adminMethod").getAnnotation(RequireAdmin.class));
        when(request.getHeader("X-User-Role")).thenReturn("user");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(12001);
    }

    @Test
    @DisplayName("缺少 X-User-Role 请求头抛 BizException(403)")
    void shouldRejectMissingRole() throws NoSuchMethodException {
        when(handlerMethod.getMethodAnnotation(RequireAdmin.class))
                .thenReturn(TestController.class.getMethod("adminMethod").getAnnotation(RequireAdmin.class));
        when(request.getHeader("X-User-Role")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(12001);
    }

    @Test
    @DisplayName("@RequireAdmin 在类上时，类中方法也需校验")
    void shouldCheckClassLevelAnnotation() throws NoSuchMethodException {
        // 类级别有 @RequireAdmin，方法级别没有
        when(handlerMethod.getMethodAnnotation(RequireAdmin.class)).thenReturn(null);
        doReturn((Class) AdminTestController.class).when(handlerMethod).getBeanType();
        when(request.getHeader("X-User-Role")).thenReturn("user");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(12001);
    }

    // ==================== 测试用 Controller ====================

    static class TestController {
        @RequireAdmin
        public void adminMethod() {
        }

        public void normalMethod() {
        }
    }

    @RequireAdmin
    static class AdminTestController {
        public void anyMethod() {
        }
    }
}
