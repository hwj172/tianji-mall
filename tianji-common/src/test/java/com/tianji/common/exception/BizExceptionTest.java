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

    @Test
    void shouldCreateFromErrorCode() {
        BizException ex = new BizException(BizErrorCode.PRODUCT_NOT_FOUND);

        assertThat(ex.getCode()).isEqualTo(20001);
        assertThat(ex.getMessage()).isEqualTo("商品不存在或已下架");
    }

    @Test
    void shouldCreateFromErrorCodeWithDetail() {
        BizException ex = new BizException(BizErrorCode.ORDER_NOT_FOUND, "orderId=999");

        assertThat(ex.getCode()).isEqualTo(30001);
        assertThat(ex.getMessage()).isEqualTo("订单不存在：orderId=999");
    }
}
