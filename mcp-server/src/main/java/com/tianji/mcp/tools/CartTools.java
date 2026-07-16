package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CartTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse getCart(Long userId) {
        try {
            Map<String, Object> result = mallFeignClient.getCartList(userId);
            return ToolResponse.ok(result.get("data"));
        } catch (Exception e) {
            return ToolResponse.fail("购物车查询失败: " + e.getMessage());
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
        } catch (Exception e) {
            return ToolResponse.fail("添加到购物车失败: " + e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return (Long) value;
    }
}
