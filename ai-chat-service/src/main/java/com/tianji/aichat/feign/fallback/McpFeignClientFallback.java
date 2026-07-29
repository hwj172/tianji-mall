package com.tianji.aichat.feign.fallback;

import com.tianji.aichat.feign.McpFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class McpFeignClientFallback implements FallbackFactory<McpFeignClient> {

    @Override
    public McpFeignClient create(Throwable cause) {
        log.error("McpFeignClient 调用失败，触发降级", cause);
        return request -> {
            log.warn("工具调用降级: tool={}", request.get("tool"));
            return Map.of("error", "工具服务暂不可用，请稍后重试");
        };
    }
}
