package com.tianji.mcp.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.mcp.dto.ToolRequest;
import com.tianji.mcp.dto.ToolResponse;
import com.tianji.mcp.tools.CartTools;
import com.tianji.mcp.tools.OrderTools;
import com.tianji.mcp.tools.ProductTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductTools productTools;

    @MockBean
    private OrderTools orderTools;

    @MockBean
    private CartTools cartTools;

    @MockBean
    private JwtUtil jwtUtil;

    // ==================== POST /api/tool/execute ====================

    @Test
    void shouldExecuteSearchProducts() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("products", new Object[0]));
        when(productTools.searchProducts(anyMap())).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"search_products\",\"parameters\":{\"keyword\":\"手机\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnUnauthorizedWhenNoAuthHeader() throws Exception {
        mockMvc.perform(post("/api/tool/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"search_products\",\"parameters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("未授权"));
    }

    @Test
    void shouldReturnFailForUnknownTool() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"unknown_tool\",\"parameters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("未知工具: unknown_tool"));
    }

    @Test
    void shouldExecuteGetCart() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("items", new Object[0]));
        when(cartTools.getCart(1L)).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"get_cart\",\"parameters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void shouldExecuteCreateOrder() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("id", 100, "orderNo", "TEST001"));
        when(orderTools.createOrder(eq(1L), anyMap())).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"create_order\",\"parameters\":{\"addressId\":1,\"cartItemIds\":[1,2]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldExecutePayOrder() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(null);
        when(orderTools.payOrder(eq(1L), anyMap())).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"pay_order\",\"parameters\":{\"orderId\":100}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== additional tool routes ====================

    @Test
    void shouldRouteToGetProduct() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("id", 1, "name", "测试商品"));
        when(productTools.getProductDetail(1L)).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"get_product\",\"parameters\":{\"productId\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRouteToGetOrders() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("records", new Object[0]));
        when(orderTools.getOrders(1L)).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"get_orders\",\"parameters\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRouteToGetOrderDetail() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("id", 100, "orderNo", "TEST001"));
        when(orderTools.getOrderDetail(100L)).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"get_order_detail\",\"parameters\":{\"orderId\":100}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRouteToAddToCart() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        ToolResponse mockResp = ToolResponse.ok(Map.of("id", 1));
        when(cartTools.addToCart(eq(1L), anyMap())).thenReturn(mockResp);

        mockMvc.perform(post("/api/tool/execute")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"add_to_cart\",\"parameters\":{\"productId\":100,\"quantity\":2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
