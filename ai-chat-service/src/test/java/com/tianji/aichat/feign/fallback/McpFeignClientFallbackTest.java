package com.tianji.aichat.feign.fallback;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpFeignClientFallbackTest {

    @Test
    void shouldReturnErrorMapOnFailure() {
        McpFeignClientFallback fallback = new McpFeignClientFallback();
        var client = fallback.create(new RuntimeException("timeout"));
        Map<String, Object> result = client.executeTool(Map.of("tool", "search_products"));
        assertTrue(result.containsKey("error"));
        assertTrue(result.get("error").toString().contains("暂不可用"));
    }
}
