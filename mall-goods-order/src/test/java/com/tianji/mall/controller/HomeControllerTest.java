package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.Banner;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.BannerService;
import com.tianji.mall.service.CategoryService;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BannerService bannerService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ProductService productService;

    @MockBean
    private RecommendService recommendService;

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
    private com.tianji.mall.service.RefundService refundService;

    @MockBean
    private com.tianji.mall.service.DashboardService dashboardService;

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

    // ==================== GET /api/home ====================

    @Test
    void shouldReturnHomePageWithoutAuth() throws Exception {
        Banner banner = new Banner();
        banner.setId(1L);
        banner.setTitle("双11大促");
        banner.setImageUrl("https://example.com/banner.jpg");
        banner.setLinkUrl("/promo/11");
        banner.setSort(1);
        when(bannerService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(List.of(banner));

        CategoryTreeResponse cat = new CategoryTreeResponse(1L, "手机数码", 0L, 1, List.of());
        when(categoryService.getCategoryTree()).thenReturn(List.of(cat));

        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 15");
        product.setPrice(BigDecimal.valueOf(6999));
        product.setSales(1000);
        Page<Product> productPage = new Page<>(1, 8);
        productPage.setRecords(List.of(product));
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(),
                eq("sales"), isNull(), eq(1), eq(8))).thenReturn(productPage);

        RecommendResponse recommend = new RecommendResponse(List.of(), List.of(), List.of());
        when(recommendService.recommend(isNull(), eq(10))).thenReturn(recommend);

        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.banners[0].title").value("双11大促"))
                .andExpect(jsonPath("$.data.categories[0].name").value("手机数码"))
                .andExpect(jsonPath("$.data.hotProducts[0].name").value("iPhone 15"));
    }

    @Test
    void shouldReturnHomePageWithAuth() throws Exception {
        when(bannerService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        when(categoryService.getCategoryTree()).thenReturn(List.of());

        Page<Product> emptyPage = new Page<>(1, 8);
        emptyPage.setRecords(List.of());
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(),
                eq("sales"), isNull(), eq(1), eq(8))).thenReturn(emptyPage);

        RecommendResponse recommend = new RecommendResponse(List.of(), List.of(), List.of());
        when(recommendService.recommend(eq(1L), eq(10))).thenReturn(recommend);

        mockMvc.perform(get("/api/home")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.banners").isArray())
                .andExpect(jsonPath("$.data.categories").isArray())
                .andExpect(jsonPath("$.data.hotProducts").isArray());
    }
}
