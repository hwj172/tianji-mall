package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.entity.Product;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.SeckillService;
import com.tianji.mall.entity.BrowsingHistory;
import com.tianji.mall.service.BrowsingHistoryService;
import com.tianji.mall.service.NotificationService;
import com.tianji.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    private JwtUtil jwtUtil;

    @MockBean
    private SeckillService seckillService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private BrowsingHistoryService browsingHistoryService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    // ==================== GET /api/product/list ====================

    @Test
    void shouldListProducts() throws Exception {
        Page<Product> page = new Page<>(1, 20);
        page.setTotal(0);
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/product/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldSearchByKeyword() throws Exception {
        Page<Product> page = new Page<>(1, 20);
        page.setTotal(1);
        when(productService.getProductPage(isNull(), eq("手机"), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/product/list")
                        .param("keyword", "手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ==================== GET /api/product/{id} ====================

    @Test
    void shouldGetProductDetail() throws Exception {
        Product product = buildProduct(1L, "iPhone", 6999);
        when(productService.getProductDetail(1L))
                .thenReturn(Map.of("product", product, "skus", List.of(), "attributes", List.of()));

        mockMvc.perform(get("/api/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.product.name").value("iPhone"))
                .andExpect(jsonPath("$.data.product.price").value(6999));
    }

    @Test
    void shouldReturnErrorWhenProductNotFound() throws Exception {
        when(productService.getProductDetail(999L))
                .thenThrow(new BizException("商品不存在或已下架"));

        mockMvc.perform(get("/api/product/999"))
                .andExpect(status().isOk()) // BizException → GlobalExceptionHandler → 200
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("商品不存在或已下架"));
    }

    // ==================== POST /api/product/internal/sync-vectors ====================

    @Test
    void shouldTriggerVectorBackfill() throws Exception {
        when(productService.syncAllVectors())
                .thenReturn(Map.of("total", 12, "success", 12, "failed", 0));

        mockMvc.perform(post("/api/product/internal/sync-vectors")
                .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.success").value(12))
                .andExpect(jsonPath("$.data.failed").value(0));
    }

    // ==================== GET /api/product/recommend ====================

    @Test
    void shouldRecommendWithoutJwt() throws Exception {
        RecommendResponse resp = new RecommendResponse(
                List.of(),
                List.of(new RecommendResponse.RecommendItem(1L, "iPhone",
                        java.math.BigDecimal.valueOf(6999), 5000L, "")),
                List.of()
        );
        when(recommendService.recommend(isNull(), eq(10))).thenReturn(resp);

        mockMvc.perform(get("/api/product/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hotSales[0].name").value("iPhone"))
                .andExpect(jsonPath("$.data.guessYouLike").isEmpty());
    }

    @Test
    void shouldRecommendWithJwt() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);

        RecommendResponse resp = new RecommendResponse(
                List.of(new RecommendResponse.RecommendItem(2L, "保护壳",
                        java.math.BigDecimal.valueOf(49), 3000L, "")),
                List.of(),
                List.of(new RecommendResponse.RecommendItem(3L, "数据线",
                        java.math.BigDecimal.valueOf(29), 2000L, "和 iPhone 一起买"))
        );
        when(recommendService.recommend(eq(1L), eq(5))).thenReturn(resp);

        mockMvc.perform(get("/api/product/recommend")
                        .header("Authorization", "Bearer test-token")
                        .param("count", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.guessYouLike[0].name").value("保护壳"))
                .andExpect(jsonPath("$.data.buyAfterBuy[0].name").value("数据线"));
    }

    // ==================== GET /api/product/seckill/list ====================

    @Test
    void shouldListSeckillProducts() throws Exception {
        Page<Product> page = new Page<>(1, 20);
        page.setTotal(0);
        when(seckillService.getSeckillList(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/product/seckill/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ==================== GET/DELETE /api/product/history ====================

    @Test
    void shouldGetBrowsingHistory() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        when(browsingHistoryService.getHistory(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/product/history")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldClearBrowsingHistory() throws Exception {
        when(jwtUtil.getUserId("test-token")).thenReturn(1L);
        doNothing().when(browsingHistoryService).clearHistory(1L);

        mockMvc.perform(delete("/api/product/history")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /api/product/search/hot ====================

    @Test
    void shouldReturnHotKeywords() throws Exception {
        when(productService.getHotKeywords()).thenReturn(List.of(
                Map.of("keyword", "手机", "count", 100L),
                Map.of("keyword", "耳机", "count", 50L)
        ));

        mockMvc.perform(get("/api/product/search/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("手机"))
                .andExpect(jsonPath("$.data[1]").value("耳机"));
    }

    @Test
    void shouldReturnEmptyHotKeywords() throws Exception {
        when(productService.getHotKeywords()).thenReturn(List.of());

        mockMvc.perform(get("/api/product/search/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ==================== helpers ====================

    private Product buildProduct(Long id, String name, int price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(price));
        p.setStock(10);
        p.setStatus(1);
        return p;
    }
}
