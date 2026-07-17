package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse getCart(Long userId) {
        try {
            Map<String, Object> result = mallFeignClient.getCartList(userId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("购物车查询 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("购物车服务暂不可用");
        } catch (Exception e) {
            log.error("购物车查询未知异常", e);
            return ToolResponse.fail("购物车查询失败");
        }
    }

    public ToolResponse addToCart(Long userId, Map<String, Object> params) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("productId", toLong(params.get("productId")));
            body.put("quantity", params.getOrDefault("quantity", 1));
            Map<String, Object> result = mallFeignClient.addToCart(body);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("添加购物车 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("购物车服务暂不可用");
        } catch (Exception e) {
            log.error("添加购物车未知异常", e);
            return ToolResponse.fail("添加到购物车失败");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return (Long) value;
    }
}
