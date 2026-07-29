package com.tianji.aichat.feign.fallback;

import com.tianji.aichat.feign.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class ProductFeignClientFallback implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("ProductFeignClient 调用失败，触发降级", cause);
        return ids -> {
            log.warn("商品批量查询降级: count={}", ids != null ? ids.size() : 0);
            return Collections.emptyMap();
        };
    }
}
