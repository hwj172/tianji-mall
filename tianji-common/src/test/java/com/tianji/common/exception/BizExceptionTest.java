package com.tianji.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BizExceptionTest {

    @Test
    void shouldCreateWithCodeAndMessage() {
        BizException ex = new BizException(404, "资源不存在");

        assertThat(ex.getCode()).isEqualTo(404);
        assertThat(ex.getMessage()).isEqualTo("资源不存在");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldCreateWithMessageOnlyAndDefaultCode() {
        BizException ex = new BizException("业务错误");

        assertThat(ex.getCode()).isEqualTo(500);
        assertThat(ex.getMessage()).isEqualTo("业务错误");
    }

    @Test
    void shouldSupportCustomBusinessCode() {
        BizException ex = new BizException(1001, "库存不足");

        assertThat(ex.getCode()).isEqualTo(1001);
        assertThat(ex.getMessage()).isEqualTo("库存不足");
    }
}
