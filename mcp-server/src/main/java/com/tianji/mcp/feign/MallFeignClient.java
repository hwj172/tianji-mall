package com.tianji.mcp.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "mall-goods-order")
public interface MallFeignClient {

    @GetMapping("/api/product/list")
    Map<String, Object> searchProducts(@RequestParam Map<String, Object> params);

    @GetMapping("/api/product/{id}")
    Map<String, Object> getProduct(@PathVariable Long id);

    @GetMapping("/api/order/internal/list/{userId}")
    Map<String, Object> getOrderList(@PathVariable Long userId);

    @GetMapping("/api/order/internal/{id}")
    Map<String, Object> getOrder(@PathVariable Long id);

    @GetMapping("/api/cart/internal/list")
    Map<String, Object> getCartList(@RequestParam Long userId);

    @PostMapping("/api/cart/internal/add")
    Map<String, Object> addToCart(@RequestBody Map<String, Object> body);
}
