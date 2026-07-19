package com.tianji.mall.controller;

import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.entity.Category;
import com.tianji.mall.service.CategoryService;
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
