package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Favorite;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.FavoriteService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private FavoriteService favoriteService;

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
    void shouldToggleFavoriteAdd() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        when(favoriteService.toggle(100L, 1L)).thenReturn(Map.of("favorited", true, "productId", 1L));

        mockMvc.perform(post("/api/favorite/toggle")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.productId").value(1));
    }

    @Test
    void shouldToggleFavoriteRemove() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        when(favoriteService.toggle(100L, 1L)).thenReturn(Map.of("favorited", false, "productId", 1L));

        mockMvc.perform(post("/api/favorite/toggle")
                        .header("Authorization", "Bearer token123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    @Test
    void shouldListFavorites() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        Favorite f = new Favorite();
        f.setId(1L);
        f.setUserId(100L);
        f.setProductId(10L);
        Page<Favorite> page = new Page<>(1, 20);
        page.setRecords(List.of(f));
        page.setTotal(1);
        when(favoriteService.listByUser(100L, 1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/favorite/list")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].productId").value(10))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldReturnEmptyList() throws Exception {
        when(jwtUtil.getUserId("token123")).thenReturn(100L);
        Page<Favorite> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(favoriteService.listByUser(100L, 1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/favorite/list")
                        .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
