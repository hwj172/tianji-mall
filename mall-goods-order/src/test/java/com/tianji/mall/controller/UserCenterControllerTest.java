package com.tianji.mall.controller;

import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.*;
import com.tianji.mall.service.*;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserFeignClient userFeignClient;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private UserCouponMapper userCouponMapper;

    @MockBean
    private FavoriteMapper favoriteMapper;

    @MockBean
    private CartItemMapper cartItemMapper;

    @MockBean
    private BrowsingHistoryMapper browsingHistoryMapper;

    @MockBean
    private ShopFollowMapper shopFollowMapper;

    @MockBean
    private AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private SeckillService seckillService;

    @MockBean
    private GroupBuyService groupBuyService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ShopService shopService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    private String authHeader() {
        return "Bearer " + jwtUtil.generateToken(1L, "testuser", "user");
    }

    @Test
    void shouldReturnUserCenterData() throws Exception {
        when(userCouponMapper.selectCountByUserId(1L)).thenReturn(3L);
        when(favoriteMapper.selectCountByUserId(1L)).thenReturn(12L);
        when(cartItemMapper.selectCountByUserId(1L)).thenReturn(8L);
        when(browsingHistoryMapper.selectCountByUserId(1L)).thenReturn(50L);
        when(shopFollowMapper.selectCountByUserId(1L)).thenReturn(2L);
        when(orderMapper.selectOrderStats(1L)).thenReturn(List.of(
                Map.of("status", 1, "cnt", 5L),
                Map.of("status", 2, "cnt", 2L),
                Map.of("status", 3, "cnt", 3L),
                Map.of("status", 4, "cnt", 1L)
        ));

        Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("id", 1L);
        userData.put("username", "testuser");
        userData.put("avatar", "https://example.com/avatar.jpg");
        userData.put("role", "user");
        when(userFeignClient.getUserById(1L)).thenReturn(R.ok(userData));

        mockMvc.perform(get("/api/user/center")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("testuser"))
                .andExpect(jsonPath("$.data.orderStats.pendingPayment").value(5))
                .andExpect(jsonPath("$.data.orderStats.pendingShip").value(2))
                .andExpect(jsonPath("$.data.orderStats.pendingReceive").value(3))
                .andExpect(jsonPath("$.data.orderStats.pendingReview").value(1))
                .andExpect(jsonPath("$.data.couponCount").value(3))
                .andExpect(jsonPath("$.data.favoriteCount").value(12))
                .andExpect(jsonPath("$.data.cartCount").value(8))
                .andExpect(jsonPath("$.data.historyCount").value(50))
                .andExpect(jsonPath("$.data.followShopCount").value(2));
    }

    @Test
    void shouldDegradeWhenUserServiceFails() throws Exception {
        when(userCouponMapper.selectCountByUserId(1L)).thenReturn(0L);
        when(favoriteMapper.selectCountByUserId(1L)).thenReturn(0L);
        when(cartItemMapper.selectCountByUserId(1L)).thenReturn(0L);
        when(browsingHistoryMapper.selectCountByUserId(1L)).thenReturn(0L);
        when(shopFollowMapper.selectCountByUserId(1L)).thenReturn(0L);
        when(orderMapper.selectOrderStats(1L)).thenReturn(List.of());
        when(userFeignClient.getUserById(1L)).thenThrow(new RuntimeException("user-service down"));

        mockMvc.perform(get("/api/user/center")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user").doesNotExist())
                .andExpect(jsonPath("$.data.couponCount").value(0));
    }
}
