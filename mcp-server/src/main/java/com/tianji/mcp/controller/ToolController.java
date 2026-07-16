package com.tianji.mcp.controller;

import com.tianji.mcp.dto.ToolRequest;
import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.tools.CartTools;
import com.tianji.mcp.tools.OrderTools;
import com.tianji.mcp.tools.ProductTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/tool")
@RequiredArgsConstructor
public class ToolController {

    private final ProductTools productTools;
    private final OrderTools orderTools;
    private final CartTools cartTools;

    @PostMapping("/execute")
    public ToolResponse execute(@RequestBody ToolRequest req) {
        log.info("执行工具: tool={}, userId={}, params={}", req.getTool(), req.getUserId(), req.getParameters());
        try {
            return switch (req.getTool()) {
                case "search_products" -> productTools.searchProducts(req.getParameters());
                case "get_product" -> {
                    Long productId = toLong(req.getParameters().get("productId"));
                    yield productTools.getProductDetail(productId);
                }
                case "get_orders" -> orderTools.getOrders(req.getUserId());
                case "get_order_detail" -> {
                    Long orderId = toLong(req.getParameters().get("orderId"));
                    yield orderTools.getOrderDetail(orderId);
                }
                case "get_cart" -> cartTools.getCart(req.getUserId());
                case "add_to_cart" -> cartTools.addToCart(req.getUserId(), req.getParameters());
                default -> ToolResponse.fail("未知工具: " + req.getTool());
            };
        } catch (Exception e) {
            log.error("工具执行异常", e);
            return ToolResponse.fail("工具执行异常: " + e.getMessage());
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
