package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShopControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopService shopService;

    @MockBean
    private ProductService productService;

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
    private SeckillService seckillService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private BrowsingHistoryService browsingHistoryService;

    @Test
    void shouldGetShopDetail() throws Exception {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("测试店铺");
        shop.setStatus(1);

        Page<Product> page = new Page<>(1, 20);
        page.setTotal(0);

        when(shopService.getById(1L)).thenReturn(shop);
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1L), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/shop/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.shop.name").value("测试店铺"));
    }

    @Test
    void shouldReturnErrorForClosedShop() throws Exception {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setStatus(0);
        when(shopService.getById(1L)).thenReturn(shop);

        mockMvc.perform(get("/api/shop/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("店铺不存在或已关闭"));
    }

    @Test
    void shouldRegisterShop() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);

        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("新店铺");
        when(shopService.register(eq(1L), eq("新店铺"), isNull(), isNull())).thenReturn(shop);

        mockMvc.perform(post("/api/shop/register")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新店铺\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新店铺"));
    }
}
