package com.tianji.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.CouponService;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.NotificationService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tianji.common.exception.BizException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CouponService couponService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @MockBean
    private AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    @Test
    void shouldListAvailableCoupons() throws Exception {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("满100减20");
        when(couponService.listAvailable()).thenReturn(List.of(coupon));

        mockMvc.perform(get("/api/coupon/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("满100减20"));
    }

    @Test
    void shouldClaimCoupon() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);

        mockMvc.perform(post("/api/coupon/1/claim")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnErrorWhenClaimFailed() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        doThrow(new BizException("优惠券已领完")).when(couponService).claimCoupon(100L, 1L);

        mockMvc.perform(post("/api/coupon/1/claim")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("优惠券已领完"));
    }

    @Test
    void shouldGetMyCoupons() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        UserCoupon uc = new UserCoupon();
        uc.setId(1L);
        uc.setCouponId(10L);
        uc.setStatus("UNUSED");
        when(couponService.getUserCoupons(100L)).thenReturn(List.of(uc));

        mockMvc.perform(get("/api/coupon/my")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("UNUSED"));
    }

    @Test
    void shouldReturnEmptyListWhenNoCoupons() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        when(couponService.getUserCoupons(100L)).thenReturn(List.of());

        mockMvc.perform(get("/api/coupon/my")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
