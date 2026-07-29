package com.tianji.mcp.feign.fallback;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MallFeignClientFallbackTest {

    @Test
    void shouldReturnErrorOnGetProductFailure() {
        MallFeignClientFallback fallback = new MallFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.getProduct(1L);
        assertTrue(result.containsKey("error"));
    }

    @Test
    void shouldReturnEmptyRecordsOnSearchFailure() {
        MallFeignClientFallback fallback = new MallFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.searchProducts(Map.of("keyword", "test"));
        assertEquals(0, result.get("total"));
        assertEquals(Collections.emptyList(), result.get("records"));
    }
}
