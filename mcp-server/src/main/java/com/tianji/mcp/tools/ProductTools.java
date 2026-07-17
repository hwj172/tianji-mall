package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse searchProducts(Map<String, Object> params) {
        try {
            Map<String, Object> result = mallFeignClient.searchProducts(params);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("商品搜索 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("商品搜索服务暂不可用");
        } catch (Exception e) {
            log.error("商品搜索未知异常", e);
            return ToolResponse.fail("商品搜索失败");
        }
    }

    public ToolResponse getProductDetail(Long productId) {
        try {
            Map<String, Object> result = mallFeignClient.getProduct(productId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("商品详情 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("商品查询服务暂不可用");
        } catch (Exception e) {
            log.error("商品查询未知异常", e);
            return ToolResponse.fail("商品查询失败");
        }
    }
}
