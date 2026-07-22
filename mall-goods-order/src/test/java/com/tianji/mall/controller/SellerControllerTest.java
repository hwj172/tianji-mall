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
class SellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopService shopService;

    @MockBean
    private ProductService productService;

    @MockBean
    private OrderService orderService;

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

    private Shop buildShop() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("测试店铺");
        shop.setSellerId(2L);
        shop.setStatus(1);
        return shop;
    }

    @Test
    void shouldGetMyShop() throws Exception {
        when(jwtUtil.getUserId("token")).thenReturn(2L);
        when(shopService.getBySellerId(2L)).thenReturn(buildShop());

        mockMvc.perform(get("/api/seller/shop")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试店铺"));
    }

    @Test
    void shouldUpdateShop() throws Exception {
        when(jwtUtil.getUserId("token")).thenReturn(2L);

        mockMvc.perform(put("/api/seller/shop")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新店名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldGetMyProducts() throws Exception {
        when(jwtUtil.getUserId("token")).thenReturn(2L);
        when(shopService.getBySellerId(2L)).thenReturn(buildShop());

        Page<Product> page = new Page<>(1, 20);
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1L), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/seller/products")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldCreateProduct() throws Exception {
        when(jwtUtil.getUserId("token")).thenReturn(2L);
        when(shopService.getBySellerId(2L)).thenReturn(buildShop());
        when(productService.save(any(Product.class))).thenReturn(true);

        mockMvc.perform(post("/api/seller/product")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"商品\",\"price\":99,\"stock\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldGetDashboard() throws Exception {
        when(jwtUtil.getUserId("token")).thenReturn(2L);
        when(shopService.getBySellerId(2L)).thenReturn(buildShop());
        when(productService.count(any())).thenReturn(5L);

        mockMvc.perform(get("/api/seller/dashboard")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.productCount").value(5));
    }
}
