package com.tianji.mcp.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "mall-goods-order")
public interface MallFeignClient {

    @GetMapping("/api/product/list")
    Map<String, Object> searchProducts(@RequestParam Map<String, Object> params);

    @GetMapping("/api/product/{id}")
    Map<String, Object> getProduct(@PathVariable("id") Long id);

    @GetMapping("/api/order/internal/list/{userId}")
    Map<String, Object> getOrderList(@PathVariable("userId") Long userId);

    @GetMapping("/api/order/internal/{id}")
    Map<String, Object> getOrder(@PathVariable("id") Long id);

    @GetMapping("/api/cart/internal/list")
    Map<String, Object> getCartList(@RequestParam("userId") Long userId);

    @PostMapping("/api/cart/internal/add")
    Map<String, Object> addToCart(@RequestBody Map<String, Object> body);

    @PostMapping("/api/order/internal/create/{userId}")
    Map<String, Object> createOrder(@PathVariable("userId") Long userId,
                                     @RequestBody Map<String, Object> body);

    @PostMapping("/api/order/internal/pay/{id}")
    Map<String, Object> payOrder(@PathVariable("id") Long id,
                                  @RequestParam("userId") Long userId);
}
