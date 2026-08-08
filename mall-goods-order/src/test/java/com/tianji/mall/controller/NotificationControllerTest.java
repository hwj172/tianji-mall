package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Notification;
import com.tianji.mall.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private RefundService refundService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private LogisticsService logisticsService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CartService cartService;

    @MockBean
    private AddressService addressService;

    @MockBean
    private ProductService productService;

    @MockBean
    private CouponService couponService;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private SeckillService seckillService;

    @MockBean
    private GroupBuyService groupBuyService;

    @MockBean
    private ProductSkuService productSkuService;

    @MockBean
    private ProductAttributeService productAttributeService;

    @MockBean
    private com.tianji.mall.feign.PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @BeforeEach
    void setUp() {
        when(jwtUtil.getUserId(anyString())).thenReturn(1L);
    }

    @Test
    void shouldGetNotificationList() throws Exception {
        Page<Notification> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(List.of());
        when(notificationService.getList(eq(1L), eq(1), eq(20), eq("all"))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/notification/list")
                        .header("Authorization", "Bearer test-token")
                        .param("page", "1")
                        .param("size", "20")
                        .param("type", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldGetNotificationListWithTypeFilter() throws Exception {
        Page<Notification> emptyPage = new Page<>(1, 20);
        emptyPage.setRecords(List.of());
        when(notificationService.getList(eq(1L), eq(1), eq(20), eq("order"))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/notification/list")
                        .header("Authorization", "Bearer test-token")
                        .param("type", "order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldGetUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/notification/unread-count")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void shouldMarkSingleRead() throws Exception {
        doNothing().when(notificationService).markRead(eq(1L), eq(10L));

        mockMvc.perform(put("/api/notification/10/read")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldMarkAllRead() throws Exception {
        doNothing().when(notificationService).markAllRead(1L);

        mockMvc.perform(put("/api/notification/read-all")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
