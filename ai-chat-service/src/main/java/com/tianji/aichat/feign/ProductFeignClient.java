package com.tianji.aichat.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mall-goods-order")
public interface ProductFeignClient {

    @PostMapping("/api/product/batch")
    Map<String, Object> getProductBatch(@RequestBody List<Long> ids);
}
