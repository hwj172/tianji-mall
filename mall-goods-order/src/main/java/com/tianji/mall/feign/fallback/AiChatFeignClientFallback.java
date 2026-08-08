package com.tianji.mall.feign.fallback;

import com.tianji.mall.feign.AiChatFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiChatFeignClientFallback implements FallbackFactory<AiChatFeignClient> {

    @Override
    public AiChatFeignClient create(Throwable cause) {
        log.error("AiChatFeignClient 调用失败，触发降级", cause);
        return new AiChatFeignClient() {
            @Override
            public void upsertProductVector(Map<String, Object> body) {
                log.warn("向量同步降级跳过");
            }

            @Override
            public void upsertProductImage(Map<String, Object> body) {
                log.warn("图片向量同步降级跳过");
            }

            @Override
            public Map<String, Object> imageSearch(Map<String, Object> body) {
                log.warn("以图搜图降级，返回空结果");
                return Map.of("data", List.of());
            }
        };
    }
}
