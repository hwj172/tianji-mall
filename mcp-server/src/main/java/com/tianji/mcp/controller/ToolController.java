package com.tianji.mcp.controller;

import com.tianji.mcp.dto.ToolRequest;
import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.tools.CartTools;
import com.tianji.mcp.tools.OrderTools;
import com.tianji.mcp.tools.ProductTools;
import com.tianji.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/tool")
public class ToolController {

    private final ProductTools productTools;
    private final OrderTools orderTools;
    private final CartTools cartTools;
    private final JwtUtil jwtUtil;

    public ToolController(ProductTools productTools, OrderTools orderTools,
                          CartTools cartTools, JwtUtil jwtUtil) {
        this.productTools = productTools;
        this.orderTools = orderTools;
        this.cartTools = cartTools;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/execute")
    public ToolResponse execute(@RequestBody ToolRequest req, HttpServletRequest request) {
        Long realUserId = extractUserId(request);
        if (realUserId == null) {
            return ToolResponse.fail("未授权");
        }

        log.info("执行工具: tool={}, realUserId={}, params={}", req.getTool(), realUserId, req.getParameters());

        try {
            return switch (req.getTool()) {
                case "search_products" -> productTools.searchProducts(req.getParameters());
                case "get_product" -> {
                    Long productId = toLong(req.getParameters().get("productId"));
                    yield productTools.getProductDetail(productId);
                }
                case "get_orders" -> orderTools.getOrders(realUserId);
                case "get_order_detail" -> {
                    Long orderId = toLong(req.getParameters().get("orderId"));
                    yield orderTools.getOrderDetail(orderId);
                }
                case "get_cart" -> cartTools.getCart(realUserId);
                case "add_to_cart" -> cartTools.addToCart(realUserId, req.getParameters());
                case "create_order" -> orderTools.createOrder(realUserId, req.getParameters());
                case "pay_order" -> orderTools.payOrder(realUserId, req.getParameters());
                default -> ToolResponse.fail("未知工具: " + req.getTool());
            };
        } catch (feign.FeignException e) {
            log.error("工具执行 Feign 调用失败: tool={}, status={}", req.getTool(), e.status(), e);
            return ToolResponse.fail("下游服务暂不可用");
        } catch (Exception e) {
            log.error("工具执行未知异常: tool={}", req.getTool(), e);
            return ToolResponse.fail("工具执行异常");
        }
    }

    /**
     * 从 Authorization 头提取 JWT 并解析真实 userId。
     * 不信任请求体中的 userId，防止身份伪造。
     */
    private Long extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少有效的 Authorization 头");
            return null;
        }
        try {
            return jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        } catch (Exception e) {
            log.warn("JWT 解析失败", e);
            return null;
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
