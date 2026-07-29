package com.tianji.mall.feign.fallback;

import com.tianji.mall.feign.AiChatFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiChatFeignClientFallback implements FallbackFactory<AiChatFeignClient> {

    @Override
    public AiChatFeignClient create(Throwable cause) {
        log.error("AiChatFeignClient 调用失败，触发降级", cause);
        return body -> log.warn("向量同步降级跳过");
    }
}
