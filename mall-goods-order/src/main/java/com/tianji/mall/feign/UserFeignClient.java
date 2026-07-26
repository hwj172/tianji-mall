package com.tianji.mall.feign;

import com.tianji.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/api/user/internal/count")
    R<Long> countUsers();

    @PutMapping("/api/user/internal/promote")
    R<Void> promoteToSeller(@RequestParam("userId") Long userId);

    @GetMapping("/api/user/internal/list")
    R<Map> listUsers(@RequestParam("page") int page,
                     @RequestParam("size") int size,
                     @RequestParam(value = "keyword", required = false) String keyword,
                     @RequestParam(value = "role", required = false) String role,
                     @RequestParam(value = "status", required = false) Integer status);

    @PutMapping("/api/user/internal/{id}/status")
    R<Void> updateUserStatus(@PathVariable("id") Long id,
                              @RequestParam("status") Integer status);

    @PutMapping("/api/user/internal/{id}/role")
    R<Void> updateUserRole(@PathVariable("id") Long id,
                            @RequestParam("role") String role);
}
