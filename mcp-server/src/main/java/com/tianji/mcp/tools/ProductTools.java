package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse searchProducts(Map<String, Object> params) {
        try {
            Map<String, Object> result = mallFeignClient.searchProducts(params);
            return ToolResponse.ok(result.get("data"));
        } catch (Exception e) {
            return ToolResponse.fail("商品搜索失败: " + e.getMessage());
        }
    }

    public ToolResponse getProductDetail(Long productId) {
        try {
            Map<String, Object> result = mallFeignClient.getProduct(productId);
            return ToolResponse.ok(result.get("data"));
        } catch (Exception e) {
            return ToolResponse.fail("商品查询失败: " + e.getMessage());
        }
    }
}
