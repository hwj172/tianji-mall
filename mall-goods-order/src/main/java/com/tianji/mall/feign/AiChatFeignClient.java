package com.tianji.mall.feign;

import com.tianji.mall.feign.fallback.AiChatFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-chat-service", fallbackFactory = AiChatFeignClientFallback.class)
public interface AiChatFeignClient {

    @PostMapping("/api/vector/upsert")
    void upsertProductVector(@RequestBody Map<String, Object> body);

    /** 商品图片向量回填 */
    @PostMapping("/api/vector/image-upsert")
    void upsertProductImage(@RequestBody Map<String, Object> body);

    /** 以图搜图：返回相似商品 ID 列表 */
    @PostMapping("/api/vector/image-search")
    Map<String, Object> imageSearch(@RequestBody Map<String, Object> body);
}
