package com.tianji.common.exception;

import com.tianji.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleBizExceptionWithCustomCode() {
        BizException ex = new BizException(404, "商品不存在");

        R<Void> result = handler.handleBizException(ex);

        assertThat(result.getCode()).isEqualTo(404);
        assertThat(result.getMessage()).isEqualTo("商品不存在");
        assertThat(result.getData()).isNull();
    }

    @Test
    void shouldHandleBizExceptionWithDefaultCode() {
        BizException ex = new BizException("库存不足");

        R<Void> result = handler.handleBizException(ex);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("库存不足");
    }

    @Test
    void shouldHandleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("user", "username", "用户名不能为空");
        FieldError fieldError2 = new FieldError("user", "password", "密码长度不能小于6位");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        R<Void> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("username: 用户名不能为空");
        assertThat(result.getMessage()).contains("password: 密码长度不能小于6位");
    }

    @Test
    void shouldHandleValidationExceptionWithSingleError() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("product", "name", "商品名称不能为空");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        R<Void> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("name: 商品名称不能为空");
    }

    @Test
    void shouldHandleValidationExceptionWithNoErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        R<Void> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("参数校验失败");
    }

    @Test
    void shouldHandleGenericException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/user/register");

        Exception ex = new RuntimeException("数据库连接失败");

        R<Void> result = handler.handleException(ex, request);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("RuntimeException: 数据库连接失败");
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/product/1");

        Exception ex = new NullPointerException();

        R<Void> result = handler.handleException(ex, request);

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("NullPointerException");
    }
}
