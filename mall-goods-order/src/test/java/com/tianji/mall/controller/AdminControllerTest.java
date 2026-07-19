package com.tianji.mall.controller;

import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.entity.Category;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.CategoryService;
import com.tianji.mall.service.OrderService;
import com.tianji.mall.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ProductService productService;

    @MockBean
    private org.redisson.api.RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private OrderService orderService;

    // ==================== GET /api/admin/category ====================

    @Test
    void shouldGetCategoryTree() throws Exception {
        when(categoryService.getCategoryTree()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/category")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnCategoryTreeWithData() throws Exception {
        Category child = new Category();
        child.setId(2L);
        child.setName("手机");
        child.setParentId(1L);
        child.setSort(1);
        CategoryTreeResponse node = new CategoryTreeResponse(1L, "电子产品", 0L, 1, List.of(child));
        when(categoryService.getCategoryTree()).thenReturn(List.of(node));

        mockMvc.perform(get("/api/admin/category")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("电子产品"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("手机"));
    }

    // ==================== POST /api/admin/category ====================

    @Test
    void shouldCreateCategory() throws Exception {
        when(categoryService.createCategory(anyString(), anyLong(), anyInt()))
                .thenReturn(new Category());

        mockMvc.perform(post("/api/admin/category")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"电子产品\",\"parentId\":0,\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldCreateCategoryWithDefaults() throws Exception {
        when(categoryService.createCategory(anyString(), anyLong(), anyInt()))
                .thenReturn(new Category());

        mockMvc.perform(post("/api/admin/category")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试分类\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/admin/category/{id} ====================

    @Test
    void shouldUpdateCategory() throws Exception {
        when(categoryService.updateCategory(anyLong(), anyString(), anyInt()))
                .thenReturn(new Category());

        mockMvc.perform(put("/api/admin/category/1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"sort\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenUpdateNonExistentCategory() throws Exception {
        when(categoryService.updateCategory(eq(Long.valueOf(999)), anyString(), anyInt()))
                .thenThrow(new BizException("分类不存在"));

        mockMvc.perform(put("/api/admin/category/999")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\",\"sort\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分类不存在"));
    }

    // ==================== DELETE /api/admin/category/{id} ====================

    @Test
    void shouldDeleteCategory() throws Exception {
        doNothing().when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/admin/category/1")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenDeleteCategoryWithChildren() throws Exception {
        doThrow(new BizException("该分类下有子分类，无法删除"))
                .when(categoryService).deleteCategory(1L);

        mockMvc.perform(delete("/api/admin/category/1")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该分类下有子分类，无法删除"));
    }

    // ==================== GET /api/admin/product ====================

    @Test
    void shouldListProducts() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Product> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setTotal(0);
        when(productService.getProductPageAdmin(1, 10, null)).thenReturn(page);

        mockMvc.perform(get("/api/admin/product")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldListProductsWithCategoryFilter() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Product> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setTotal(3);
        when(productService.getProductPageAdmin(1, 10, 1L)).thenReturn(page);

        mockMvc.perform(get("/api/admin/product")
                        .header("X-User-Role", "admin")
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    // ==================== POST /api/admin/product ====================

    @Test
    void shouldCreateProduct() throws Exception {
        when(productService.createProduct(anyString(), anyString(), any(), anyInt(), anyLong(), anyString()))
                .thenReturn(new Product());

        mockMvc.perform(post("/api/admin/product")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Product\",\"description\":\"desc\",\"price\":99.9,\"stock\":100,\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== PUT /api/admin/product/{id} ====================

    @Test
    void shouldUpdateProduct() throws Exception {
        mockMvc.perform(put("/api/admin/product/1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\",\"price\":199.9,\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenUpdateNonExistentProduct() throws Exception {
        doThrow(new BizException("商品不存在"))
                .when(productService).updateProduct(eq(Long.valueOf(1)), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(put("/api/admin/product/1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    // ==================== DELETE /api/admin/product/{id} ====================

    @Test
    void shouldDeleteProduct() throws Exception {
        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(delete("/api/admin/product/1")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /api/admin/order ====================

    @Test
    void shouldListOrders() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setTotal(0);
        when(orderService.getOrderListAdmin(1, 10, null)).thenReturn(page);

        mockMvc.perform(get("/api/admin/order")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldListOrdersWithStatusFilter() throws Exception {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setTotal(5);
        when(orderService.getOrderListAdmin(1, 10, 2)).thenReturn(page);

        mockMvc.perform(get("/api/admin/order")
                        .header("X-User-Role", "admin")
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5));
    }

    // ==================== PUT /api/admin/order/{id}/ship ====================

    @Test
    void shouldShipOrder() throws Exception {
        mockMvc.perform(put("/api/admin/order/1/ship")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsCompany\":\"顺丰\",\"trackingNumber\":\"SF123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenShipNonExistentOrder() throws Exception {
        doThrow(new BizException("订单不存在"))
                .when(orderService).shipOrder(eq(Long.valueOf(999)), any(), any());

        mockMvc.perform(put("/api/admin/order/999/ship")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsCompany\":\"顺丰\",\"trackingNumber\":\"SF123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    // ==================== PUT /api/admin/order/{id}/complete ====================

    @Test
    void shouldCompleteOrder() throws Exception {
        mockMvc.perform(put("/api/admin/order/1/complete")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenCompleteNonExistentOrder() throws Exception {
        doThrow(new BizException("订单不存在"))
                .when(orderService).completeOrder(eq(Long.valueOf(999)));

        mockMvc.perform(put("/api/admin/order/999/complete")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    // ==================== auth: non-admin rejection ====================

    @Test
    void shouldRejectUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/category")
                        .header("X-User-Role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("需要管理员权限"));
    }

    @Test
    void shouldRejectMissingRoleHeader() throws Exception {
        mockMvc.perform(get("/api/admin/category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("需要管理员权限"));
    }

    @Test
    void shouldRejectUserRoleOnUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/category/1")
                        .header("X-User-Role", "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
