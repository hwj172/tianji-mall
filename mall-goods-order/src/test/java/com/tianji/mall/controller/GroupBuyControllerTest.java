package com.tianji.mall.controller;

import com.tianji.common.exception.BizException;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.GroupBuyDetailResponse;
import com.tianji.mall.dto.GroupBuyTier;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.GroupBuyService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GroupBuyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupBuyService groupBuyService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private com.tianji.mall.service.SeckillService seckillService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    // ==================== GET /api/group-buy/list ====================

    @Test
    void shouldListActiveGroupBuys() throws Exception {
        when(groupBuyService.getActiveActivities()).thenReturn(List.of());

        mockMvc.perform(get("/api/group-buy/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /api/group-buy/{id} ====================

    @Test
    void shouldGetGroupBuyDetail() throws Exception {
        GroupBuy activity = new GroupBuy();
        activity.setId(1L);
        activity.setProductId(1L);
        GroupBuyDetailResponse resp = new GroupBuyDetailResponse(
                activity, List.of(), List.of(new GroupBuyTier(3, BigDecimal.valueOf(0.9))));
        when(groupBuyService.getDetail(1L)).thenReturn(resp);

        mockMvc.perform(get("/api/group-buy/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activity.id").value(1));
    }

    @Test
    void shouldReturnErrorWhenGroupBuyNotFound() throws Exception {
        when(groupBuyService.getDetail(999L))
                .thenThrow(new BizException("拼团活动不存在"));

        mockMvc.perform(get("/api/group-buy/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("拼团活动不存在"));
    }

    // ==================== POST /api/group-buy/start ====================

    @Test
    void shouldStartGroup() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        GroupBuyOrder gbo = new GroupBuyOrder();
        gbo.setId(1L);
        gbo.setGroupId("abc12345");
        gbo.setStatus("OPEN");
        when(groupBuyService.startGroup(eq(1L), eq(1L), eq(3), any())).thenReturn(gbo);

        mockMvc.perform(post("/api/group-buy/start")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"targetCount\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groupId").value("abc12345"));
    }

    // ==================== POST /api/group-buy/join/{groupId} ====================

    @Test
    void shouldJoinGroup() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(2L);

        mockMvc.perform(post("/api/group-buy/join/abc12345")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /api/group-buy/my ====================

    @Test
    void shouldGetMyGroups() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        when(groupBuyService.getMyGroups(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/group-buy/my")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
