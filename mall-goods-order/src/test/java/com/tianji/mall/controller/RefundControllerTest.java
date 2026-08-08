package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.service.RefundService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RefundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundService refundService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private com.tianji.mall.service.OrderService orderService;

    @MockBean
    private com.tianji.mall.service.DashboardService dashboardService;

    @MockBean
    private com.tianji.mall.service.RecommendService recommendService;

    @MockBean
    private com.tianji.mall.service.LogisticsService logisticsService;

    @MockBean
    private com.tianji.mall.service.NotificationService notificationService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== GET /api/refund/{id} ====================

    @Test
    void shouldGetRefundDetail() throws Exception {
        Map<String, Object> detail = Map.of("id", 1L, "amount", BigDecimal.valueOf(6999),
                "status", "processing");
        when(refundService.getRefundDetail(eq(1L), eq(1L))).thenReturn(detail);

        mockMvc.perform(get("/api/refund/1").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("processing"));
    }

    // ==================== GET /api/refund/my ====================

    @Test
    void shouldGetMyRefunds() throws Exception {
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setAmount(BigDecimal.valueOf(6999));
        refund.setStatus("success");
        Page<Refund> page = new Page<>(1, 20);
        page.setRecords(List.of(refund));
        page.setTotal(1);
        when(refundService.getMyRefunds(eq(1L), eq(1), eq(20), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/refund/my")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("success"));
    }

    // ==================== PUT /api/refund/{id}/ship ====================

    @Test
    void shouldReturnShip() throws Exception {
        doNothing().when(refundService).returnShip(eq(1L), eq(1L),
                eq("SF123456"), eq("顺丰速运"));

        mockMvc.perform(put("/api/refund/1/ship")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNumber\":\"SF123456\",\"trackingCompany\":\"顺丰速运\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/refund/{id}/receive ====================

    @Test
    void shouldConfirmReceive() throws Exception {
        doNothing().when(refundService).confirmReceive(1L);

        mockMvc.perform(put("/api/refund/1/receive")
                        .header("X-User-Role", "seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectConfirmReceiveForNonSeller() throws Exception {
        // 买家/无角色 header 不可确认收货退款
        mockMvc.perform(put("/api/refund/1/receive")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== GET /api/refund/seller-pending ====================

    @Test
    void shouldGetSellerPendingRefunds() throws Exception {
        Refund refund = new Refund();
        refund.setId(1L);
        when(refundService.getSellerPendingRefunds(1L)).thenReturn(java.util.List.of(refund));

        mockMvc.perform(get("/api/refund/seller-pending")
                        .header("Authorization", "Bearer test-token")
                        .header("X-User-Role", "seller"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void shouldRejectSellerPendingForNonSeller() throws Exception {
        mockMvc.perform(get("/api/refund/seller-pending")
                        .header("Authorization", "Bearer test-token")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/refund/my"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }
}
