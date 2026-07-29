package com.tianji.aichat.feign;

import com.tianji.aichat.feign.fallback.McpFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "mcp-server", fallbackFactory = McpFeignClientFallback.class)
public interface McpFeignClient {

    @PostMapping("/api/tool/execute")
    Map<String, Object> executeTool(@RequestBody Map<String, Object> request);
}
