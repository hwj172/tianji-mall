package com.tianji.pay.controller;

import com.tianji.common.util.JwtUtil;
import com.tianji.pay.dto.PayResponse;
import com.tianji.pay.entity.Payment;
import com.tianji.pay.service.PayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayService payService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    // ==================== POST /api/pay/create ====================

    @Test
    void shouldCreatePayment() throws Exception {
        PayResponse resp = new PayResponse("<form>alipay form</form>", "PAY202407160001");
        when(payService.createPayment(1L, 10L)).thenReturn(resp);

        mockMvc.perform(post("/api/pay/create")
                        .param("orderId", "10")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.payForm").value("<form>alipay form</form>"))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY202407160001"));
    }

    // ==================== POST /api/pay/notify ====================

    @Test
    void shouldHandleNotify() throws Exception {
        mockMvc.perform(post("/api/pay/notify")
                        .param("trade_status", "TRADE_SUCCESS")
                        .param("out_trade_no", "PAY202407160001"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
    }

    @Test
    void shouldReturnFailWhenNotifyFails() throws Exception {
        doThrow(new RuntimeException("回调处理失败")).when(payService).handleNotify(anyMap());

        mockMvc.perform(post("/api/pay/notify")
                        .param("trade_status", "TRADE_SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(content().string("fail"));
    }

    // ==================== GET /api/pay/query/{orderId} ====================

    @Test
    void shouldQueryPayment() throws Exception {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo("PAY202407160001");
        payment.setOrderId(10L);
        payment.setUserId(1L);
        payment.setAmount(BigDecimal.valueOf(6999));
        payment.setStatus(1);
        when(payService.queryPayment(1L, 10L)).thenReturn(payment);

        mockMvc.perform(get("/api/pay/query/10")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.paymentNo").value("PAY202407160001"))
                .andExpect(jsonPath("$.data.orderId").value(10));
    }

    // ==================== POST /api/pay/internal/refund ====================

    @Test
    void shouldRefundOrderInternal() throws Exception {
        mockMvc.perform(post("/api/pay/internal/refund")
                        .param("orderId", "10")
                        .param("userId", "1")
                        .param("amount", "99.90")
                        .param("reason", "不想要了"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== exception scenarios ====================

    @Test
    void shouldReturnErrorWhenMissingAuthHeader() throws Exception {
        mockMvc.perform(get("/api/pay/query/10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }
}
