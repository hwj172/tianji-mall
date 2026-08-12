package com.tianji.mcp.feign.fallback;

import com.tianji.mcp.feign.MallFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class MallFeignClientFallback implements FallbackFactory<MallFeignClient> {

    @Override
    public MallFeignClient create(Throwable cause) {
        log.error("MallFeignClient 调用失败，触发降级", cause);
        return new MallFeignClient() {
            @Override
            public Map<String, Object> searchProducts(Map<String, Object> params) {
                return Map.of("records", Collections.emptyList(), "total", 0);
            }

            @Override
            public Map<String, Object> getProduct(Long id) {
                return Map.of("error", "商品服务暂不可用");
            }

            @Override
            public Map<String, Object> getOrderList(Long userId) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> getOrderDetailOwned(Long id, Long userId) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> getCartList(Long userId) {
                return Map.of("error", "购物车服务暂不可用");
            }

            @Override
            public Map<String, Object> addToCart(Map<String, Object> body) {
                return Map.of("error", "购物车服务暂不可用");
            }

            @Override
            public Map<String, Object> createOrder(Long userId, Map<String, Object> body) {
                return Map.of("error", "订单服务暂不可用");
            }

            @Override
            public Map<String, Object> payOrder(Long id, Long userId) {
                return Map.of("error", "订单服务暂不可用");
            }
        };
    }
}
