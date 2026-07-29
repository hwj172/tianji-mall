package com.tianji.mall.feign.fallback;

import com.tianji.common.result.R;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PayFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnRefundFailure() {
        PayFeignClientFallback fallback = new PayFeignClientFallback();
        var client = fallback.create(new RuntimeException("connection refused"));
        R<Void> result = client.refundOrder(1L, 1L, BigDecimal.TEN, "test");
        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("暂不可用"));
    }
}
