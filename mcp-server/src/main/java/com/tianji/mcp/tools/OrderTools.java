package com.tianji.mcp.tools;

import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.feign.MallFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse getOrders(Long userId) {
        try {
            Map<String, Object> result = mallFeignClient.getOrderList(userId);
            return ToolResponse.ok(result.get("data"));
        } catch (Exception e) {
            return ToolResponse.fail("订单查询失败: " + e.getMessage());
        }
    }

    public ToolResponse getOrderDetail(Long orderId) {
        try {
            Map<String, Object> result = mallFeignClient.getOrder(orderId);
            return ToolResponse.ok(result.get("data"));
        } catch (Exception e) {
            return ToolResponse.fail("订单详情查询失败: " + e.getMessage());
        }
    }
}
