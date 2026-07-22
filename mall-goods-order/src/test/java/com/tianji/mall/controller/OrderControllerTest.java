package com.tianji.mall.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.dto.OrderItemResponse;
import com.tianji.mall.entity.Address;
import com.tianji.mall.entity.Order;
import com.tianji.mall.service.OrderService;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private com.tianji.mall.service.RefundService refundService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== POST /api/order/create ====================

    @Test
    void shouldCreateOrder() throws Exception {
        Order order = buildOrder(1L, "202407160001", BigDecimal.valueOf(6999));
        when(orderService.createOrder(eq(1L), any(OrderCreateRequest.class))).thenReturn(order);

        mockMvc.perform(post("/api/order/create")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":10,\"cartItemIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderNo").value("202407160001"))
                .andExpect(jsonPath("$.data.totalAmount").value(6999));
    }

    @Test
    void shouldRejectOrderWithoutCartItems() throws Exception {
        // cartItemIds 为空数组，触发 @NotEmpty
        mockMvc.perform(post("/api/order/create")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":10,\"cartItemIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== GET /api/order/list ====================

    @Test
    void shouldGetOrderList() throws Exception {
        Order order = buildOrder(1L, "202407160001", BigDecimal.valueOf(6999));
        when(orderService.getOrderList(1L)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/order/list")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].orderNo").value("202407160001"));
    }

    // ==================== GET /api/order/{id} ====================

    @Test
    void shouldGetOrderDetail() throws Exception {
        Order order = buildOrder(1L, "202407160001", BigDecimal.valueOf(6999));
        OrderItemResponse item = new OrderItemResponse(100L, "iPhone", BigDecimal.valueOf(6999), 1);
        Address address = buildAddress(10L, "张三", "13800000001");
        OrderDetailResponse detail = new OrderDetailResponse(order, List.of(item), address);
        when(orderService.getOrderDetail(1L, 1L)).thenReturn(detail);

        mockMvc.perform(get("/api/order/1")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.order.orderNo").value("202407160001"))
                .andExpect(jsonPath("$.data.items[0].productName").value("iPhone"))
                .andExpect(jsonPath("$.data.address.receiverName").value("张三"));
    }

    // ==================== PUT /api/order/{id}/cancel ====================

    @Test
    void shouldCancelOrder() throws Exception {
        doNothing().when(orderService).cancelOrder(1L, 1L);

        mockMvc.perform(put("/api/order/1/cancel")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/order/{id}/receive ====================

    @Test
    void shouldReceiveOrder() throws Exception {
        doNothing().when(orderService).confirmReceive(1L, 1L);

        mockMvc.perform(put("/api/order/1/receive")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== POST /api/order/{id}/refund ====================

    @Test
    void shouldRequestRefund() throws Exception {
        when(refundService.requestRefund(eq(1L), eq(1L), eq("不想要了")))
                .thenReturn(new com.tianji.mall.entity.Refund());

        mockMvc.perform(post("/api/order/1/refund")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不想要了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== internal endpoints ====================

    @Test
    void shouldGetOrderInternal() throws Exception {
        Order order = buildOrder(1L, "202407160001", BigDecimal.valueOf(6999));
        when(orderService.getById(1L)).thenReturn(order);

        mockMvc.perform(get("/api/order/internal/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("202407160001"));
    }

    @Test
    void shouldListOrdersInternal() throws Exception {
        Order order = buildOrder(1L, "202407160001", BigDecimal.valueOf(6999));
        when(orderService.getOrderList(1L)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/order/internal/list/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderNo").value("202407160001"));
    }

    @Test
    void shouldPayOrderInternal() throws Exception {
        doNothing().when(orderService).payOrder(1L, 1L);

        mockMvc.perform(put("/api/order/internal/1/pay").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/order/list"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ==================== helpers ====================

    private Order buildOrder(Long id, String orderNo, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(1L);
        order.setTotalAmount(totalAmount);
        order.setStatus(1);
        order.setAddressId(10L);
        return order;
    }

    private Address buildAddress(Long id, String name, String phone) {
        Address addr = new Address();
        addr.setId(id);
        addr.setUserId(1L);
        addr.setReceiverName(name);
        addr.setPhone(phone);
        return addr;
    }
}
