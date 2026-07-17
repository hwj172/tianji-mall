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
public class OrderTools {

    private final MallFeignClient mallFeignClient;

    public ToolResponse getOrders(Long userId) {
        try {
            Map<String, Object> result = mallFeignClient.getOrderList(userId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("订单查询 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("订单服务暂不可用");
        } catch (Exception e) {
            log.error("订单查询未知异常", e);
            return ToolResponse.fail("订单查询失败");
        }
    }

    public ToolResponse getOrderDetail(Long orderId) {
        try {
            Map<String, Object> result = mallFeignClient.getOrder(orderId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("订单详情 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("订单服务暂不可用");
        } catch (Exception e) {
            log.error("订单详情查询未知异常", e);
            return ToolResponse.fail("订单详情查询失败");
        }
    }
}
