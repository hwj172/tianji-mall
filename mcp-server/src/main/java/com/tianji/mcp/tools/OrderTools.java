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

    public ToolResponse getOrderDetail(Long userId, Long orderId) {
        try {
            // 用 JWT 真实 userId 调带所有权校验的内部端点，防 IDOR
            Map<String, Object> result = mallFeignClient.getOrderDetailOwned(orderId, userId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("订单详情 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("订单服务暂不可用");
        } catch (Exception e) {
            log.error("订单详情查询未知异常", e);
            return ToolResponse.fail("订单详情查询失败");
        }
    }

    public ToolResponse createOrder(Long userId, Map<String, Object> params) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("addressId", toLong(params.get("addressId")));
            body.put("cartItemIds", params.get("cartItemIds"));
            if (params.containsKey("couponId")) {
                body.put("couponId", toLong(params.get("couponId")));
            }
            Map<String, Object> result = mallFeignClient.createOrder(userId, body);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("创建订单 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("订单服务暂不可用");
        } catch (Exception e) {
            log.error("创建订单未知异常", e);
            return ToolResponse.fail("创建订单失败");
        }
    }

    public ToolResponse payOrder(Long userId, Map<String, Object> params) {
        try {
            Long orderId = toLong(params.get("orderId"));
            Map<String, Object> result = mallFeignClient.payOrder(orderId, userId);
            return ToolResponse.ok(result.get("data"));
        } catch (FeignException e) {
            log.error("支付订单 Feign 调用失败: status={}", e.status(), e);
            return ToolResponse.fail("订单服务暂不可用");
        } catch (Exception e) {
            log.error("支付订单未知异常", e);
            return ToolResponse.fail("支付订单失败");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        return Long.valueOf(value.toString());
    }
}
