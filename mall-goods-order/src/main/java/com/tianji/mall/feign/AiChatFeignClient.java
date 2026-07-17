package com.tianji.mall.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-chat-service")
public interface AiChatFeignClient {

    @PostMapping("/api/vector/upsert")
    void upsertProductVector(@RequestBody Map<String, Object> body);
}
