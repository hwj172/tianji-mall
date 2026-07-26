package com.tianji.mall.controller;

import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.entity.Category;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.service.*;
import com.tianji.mall.service.RecommendService;
import com.tianji.mall.service.LogisticsService;
import com.tianji.mall.service.NotificationService;
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
import static org.mockito.ArgumentMatchers.isNull;
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

    @MockBean
    private CouponService couponService;

    @MockBean
    private com.tianji.mall.feign.PayFeignClient payFeignClient;

    @MockBean
    private ProductSkuService skuService;

    @MockBean
    private ProductAttributeService attributeService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private SeckillService seckillService;

    @MockBean
    private GroupBuyService groupBuyService;

    @MockBean
    private LogisticsService logisticsService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private com.tianji.mall.service.ShopService shopService;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

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

    // ==================== GET /api/admin/coupon ====================

    @Test
    void shouldListCoupons() throws Exception {
        when(couponService.listByPage(1, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/coupon")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== POST /api/admin/coupon ====================

    @Test
    void shouldCreateCoupon() throws Exception {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("满100减20");
        when(couponService.create(any())).thenReturn(coupon);

        mockMvc.perform(post("/api/admin/coupon")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满100减20\",\"discountType\":\"FIXED\",\"discountValue\":20,\"minOrderAmount\":100,\"totalQuantity\":100,\"startTime\":\"2026-07-19T10:00:00\",\"endTime\":\"2026-08-19T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("满100减20"));
    }

    @Test
    void shouldReturn400OnMissingCouponFields() throws Exception {
        mockMvc.perform(post("/api/admin/coupon")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== PUT /api/admin/coupon/{id} ====================

    @Test
    void shouldUpdateCoupon() throws Exception {
        mockMvc.perform(put("/api/admin/coupon/1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满200减50\",\"discountType\":\"FIXED\",\"discountValue\":50,\"minOrderAmount\":200,\"totalQuantity\":50,\"startTime\":\"2026-07-19T10:00:00\",\"endTime\":\"2026-08-19T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturnErrorWhenUpdateNonExistentCoupon() throws Exception {
        doThrow(new BizException("优惠券不存在"))
                .when(couponService).update(eq(Long.valueOf(999)), any());

        mockMvc.perform(put("/api/admin/coupon/999")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"xxx\",\"discountType\":\"FIXED\",\"discountValue\":10,\"minOrderAmount\":50,\"totalQuantity\":10,\"startTime\":\"2026-07-19T10:00:00\",\"endTime\":\"2026-08-19T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("优惠券不存在"));
    }

    // ==================== DELETE /api/admin/coupon/{id} ====================

    @Test
    void shouldDisableCoupon() throws Exception {
        mockMvc.perform(delete("/api/admin/coupon/1")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== SKU 管理端点 ====================

    @Test
    void shouldListSkus() throws Exception {
        when(skuService.listByProductId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/product/1/sku")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldCreateSku() throws Exception {
        ProductSku sku = new ProductSku();
        sku.setId(1L);
        sku.setSpecs("颜色:红;尺寸:XL");
        when(skuService.create(eq(1L), eq("颜色:红;尺寸:XL"), eq("SKU001"), any(), eq(100)))
                .thenReturn(sku);

        mockMvc.perform(post("/api/admin/product/1/sku")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specs\":\"颜色:红;尺寸:XL\",\"skuCode\":\"SKU001\",\"price\":199.9,\"stock\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.specs").value("颜色:红;尺寸:XL"));
    }

    @Test
    void shouldReturn400OnMissingSkuSpecs() throws Exception {
        mockMvc.perform(post("/api/admin/product/1/sku")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":199.9,\"stock\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateSku() throws Exception {
        mockMvc.perform(put("/api/admin/product/1/sku/10")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specs\":\"颜色:蓝\",\"stock\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldDeleteSku() throws Exception {
        mockMvc.perform(delete("/api/admin/product/1/sku/10")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 属性管理端点 ====================

    @Test
    void shouldListAttributes() throws Exception {
        when(attributeService.listByProductId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/product/1/attribute")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldCreateAttribute() throws Exception {
        ProductAttribute attr = new ProductAttribute();
        attr.setId(1L);
        attr.setName("屏幕尺寸");
        attr.setValue("6.1英寸");
        when(attributeService.create(eq(1L), eq("屏幕尺寸"), eq("6.1英寸"), eq(0)))
                .thenReturn(attr);

        mockMvc.perform(post("/api/admin/product/1/attribute")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"屏幕尺寸\",\"value\":\"6.1英寸\",\"sort\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("屏幕尺寸"));
    }

    @Test
    void shouldDeleteAttribute() throws Exception {
        mockMvc.perform(delete("/api/admin/product/1/attribute/5")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== GET /api/admin/dashboard ====================

    @Test
    void shouldGetDashboard() throws Exception {
        DashboardResponse resp = new DashboardResponse(
                java.math.BigDecimal.valueOf(100000), 100L, 200L,
                new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(5000), 10L),
                new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(30000), 50L),
                new DashboardResponse.TimeStats(java.math.BigDecimal.valueOf(80000), 80L),
                List.of(new DashboardResponse.TopProduct(1L, "iPhone", 50L, java.math.BigDecimal.valueOf(499900))),
                List.of(new DashboardResponse.OrderStatusDist(2, "已付款", 30L)),
                List.of(new DashboardResponse.CategorySales(1L, "手机数码", java.math.BigDecimal.valueOf(50000)))
        );
        when(dashboardService.getDashboard()).thenReturn(resp);

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalGmv").value(100000))
                .andExpect(jsonPath("$.data.totalOrders").value(100))
                .andExpect(jsonPath("$.data.totalUsers").value(200))
                .andExpect(jsonPath("$.data.today.orders").value(10))
                .andExpect(jsonPath("$.data.topProducts[0].name").value("iPhone"));
    }

    // ==================== 秒杀管理 ====================

    @Test
    void shouldSetSeckill() throws Exception {
        mockMvc.perform(post("/api/admin/product/1/seckill")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1999,\"stock\":10,\"startTime\":\"2026-07-22T10:00:00\",\"endTime\":\"2026-07-22T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldReturn400OnMissingSeckillFields() throws Exception {
        mockMvc.perform(post("/api/admin/product/1/seckill")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldClearSeckill() throws Exception {
        mockMvc.perform(delete("/api/admin/product/1/seckill")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================== 拼团管理 ====================

    @Test
    void shouldCreateGroupBuy() throws Exception {
        com.tianji.mall.entity.GroupBuy gb = new com.tianji.mall.entity.GroupBuy();
        gb.setId(1L);
        when(groupBuyService.createActivity(any())).thenReturn(gb);

        mockMvc.perform(post("/api/admin/group-buy")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"tiers\":[{\"count\":2,\"discount\":0.9},{\"count\":5,\"discount\":0.8}],\"startTime\":\"2026-07-22T10:00:00\",\"endTime\":\"2026-07-29T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void shouldReturn400OnMissingGroupBuyFields() throws Exception {
        mockMvc.perform(post("/api/admin/group-buy")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateGroupBuy() throws Exception {
        mockMvc.perform(put("/api/admin/group-buy/1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"tiers\":[{\"count\":3,\"discount\":0.85}],\"startTime\":\"2026-07-22T10:00:00\",\"endTime\":\"2026-07-30T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
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

    // ==================== 用户管理 ====================

    @Test
    void shouldListUsers() throws Exception {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("records", List.of());
        data.put("total", 0);
        data.put("page", 1);
        data.put("size", 20);
        when(userFeignClient.listUsers(eq(1), eq(20), isNull(), isNull(), isNull()))
                .thenReturn(R.ok(data));

        mockMvc.perform(get("/api/admin/user/list")
                        .header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldUpdateUserStatus() throws Exception {
        when(userFeignClient.updateUserStatus(eq(1L), eq(0)))
                .thenReturn(R.ok());

        mockMvc.perform(put("/api/admin/user/1/status")
                        .header("X-User-Role", "admin")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldUpdateUserRole() throws Exception {
        when(userFeignClient.updateUserRole(eq(1L), eq("admin")))
                .thenReturn(R.ok());

        mockMvc.perform(put("/api/admin/user/1/role")
                        .header("X-User-Role", "admin")
                        .param("role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
