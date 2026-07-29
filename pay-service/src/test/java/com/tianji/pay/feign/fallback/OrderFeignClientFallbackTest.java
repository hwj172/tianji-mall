package com.tianji.pay.feign.fallback;

import com.tianji.common.result.R;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnGetOrderFailure() {
        OrderFeignClientFallback fallback = new OrderFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        R<?> result = client.getOrder(1L);
        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("暂不可用"));
    }

    @Test
    void shouldReturnErrorOnPayOrderFailure() {
        OrderFeignClientFallback fallback = new OrderFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        R<?> result = client.payOrder(1L, 1L);
        assertEquals(500, result.getCode());
    }
}
