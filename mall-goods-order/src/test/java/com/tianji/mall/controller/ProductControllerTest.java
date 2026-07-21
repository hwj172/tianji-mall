package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.DashboardService;
import com.tianji.mall.service.ProductService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private DashboardService dashboardService;

    // ==================== GET /api/product/list ====================

    @Test
    void shouldListProducts() throws Exception {
        Page<Product> page = new Page<>(1, 20);
        page.setTotal(0);
        when(productService.getProductPage(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
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
        when(productService.getProductPage(isNull(), eq("手机"), isNull(), isNull(), isNull(), eq(1), eq(20)))
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

        mockMvc.perform(post("/api/product/internal/sync-vectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.success").value(12))
                .andExpect(jsonPath("$.data.failed").value(0));
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
