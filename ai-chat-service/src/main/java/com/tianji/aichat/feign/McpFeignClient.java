package com.tianji.aichat.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "mcp-server")
public interface McpFeignClient {

    @PostMapping("/api/tool/execute")
    Map<String, Object> executeTool(@RequestBody Map<String, Object> request);
}
