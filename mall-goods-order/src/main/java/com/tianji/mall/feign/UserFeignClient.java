package com.tianji.mall.feign;

import com.tianji.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/api/user/internal/count")
    R<Long> countUsers();

    @PutMapping("/api/user/internal/promote")
    R<Void> promoteToSeller(@RequestParam("userId") Long userId);
}
