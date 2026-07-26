package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.mall.annotation.RequireAdmin;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.dto.SeckillSetRequest;
import com.tianji.mall.dto.SkuRequest;
import com.tianji.mall.entity.Banner;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.dto.CouponRequest;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireAdmin
public class AdminController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final ProductSkuService skuService;
    private final ProductAttributeService attributeService;
    private final DashboardService dashboardService;
    private final SeckillService seckillService;
    private final GroupBuyService groupBuyService;
    private final LogisticsService logisticsService;
    private final ShopService shopService;
    private final BannerService bannerService;
    private final UserFeignClient userFeignClient;

    // ==================== 店铺管理 ====================

    @GetMapping("/shop/list")
    public R<List<com.tianji.mall.entity.Shop>> shopList() {
        return R.ok(shopService.list());
    }

    @PutMapping("/shop/{id}/status")
    public R<Void> updateShopStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        com.tianji.mall.entity.Shop shop = shopService.getById(id);
        if (shop == null) {
            return R.fail(500, "店铺不存在");
        }
        shop.setStatus(status);
        shopService.updateById(shop);
        return R.ok();
    }

    @DeleteMapping("/shop/{id}")
    public R<Void> deleteShop(@PathVariable("id") Long id) {
        com.tianji.mall.entity.Shop shop = shopService.getById(id);
        if (shop == null) {
            return R.fail(500, "店铺不存在");
        }
        shopService.removeById(id);
        return R.ok();
    }

    // ==================== 分类管理 ====================

    @GetMapping("/category")
    public R<List<CategoryTreeResponse>> getCategoryTree() {
        return R.ok(categoryService.getCategoryTree());
    }

    @PostMapping("/category")
    public R<Void> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : 0L;
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0;
        categoryService.createCategory(name, parentId, sort);
        return R.ok();
    }

    @PutMapping("/category/{id}")
    public R<Void> updateCategory(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        categoryService.updateCategory(id, (String) body.get("name"),
                body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0);
        return R.ok();
    }

    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable("id") Long id) {
        categoryService.deleteCategory(id);
        return R.ok();
    }

    // ==================== 商品管理 ====================

    @GetMapping("/product")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Product>> listProducts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return R.ok(productService.getProductPageAdmin(page, size, categoryId));
    }

    @PostMapping("/product")
    public R<Void> createProduct(@RequestBody Map<String, Object> body) {
        productService.createProduct(
                (String) body.get("name"),
                (String) body.get("description"),
                body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null,
                body.get("stock") != null ? ((Number) body.get("stock")).intValue() : null,
                body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null,
                (String) body.get("images"));
        return R.ok();
    }

    @PutMapping("/product/{id}")
    public R<Void> updateProduct(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        productService.updateProduct(id,
                (String) body.get("name"),
                (String) body.get("description"),
                body.get("price") != null ? new BigDecimal(body.get("price").toString()) : null,
                body.get("stock") != null ? ((Number) body.get("stock")).intValue() : null,
                body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null,
                body.get("status") != null ? ((Number) body.get("status")).intValue() : null,
                (String) body.get("images"));
        return R.ok();
    }

    @DeleteMapping("/product/{id}")
    public R<Void> deleteProduct(@PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return R.ok();
    }

    // ==================== 订单管理 ====================

    @GetMapping("/order")
    public R<Page<Order>> listOrders(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(orderService.getOrderListAdmin(page, size, status));
    }

    @PutMapping("/order/{id}/ship")
    public R<Void> shipOrder(@PathVariable("id") Long id,
                              @RequestBody Map<String, String> body) {
        orderService.shipOrder(id, body.get("logisticsCompany"), body.get("trackingNumber"));
        logisticsService.generateTracks(id);
        return R.ok();
    }

    @PutMapping("/order/{id}/complete")
    public R<Void> completeOrder(@PathVariable("id") Long id) {
        orderService.completeOrder(id);
        return R.ok();
    }

    // ==================== 优惠券管理 ====================

    @GetMapping("/coupon")
    public R<List<Coupon>> listCoupons(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return R.ok(couponService.listByPage(page, size));
    }

    @PostMapping("/coupon")
    public R<Coupon> createCoupon(@Valid @RequestBody CouponRequest req) {
        return R.ok(couponService.create(req));
    }

    @PutMapping("/coupon/{id}")
    public R<Void> updateCoupon(@PathVariable("id") Long id, @Valid @RequestBody CouponRequest req) {
        couponService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/coupon/{id}")
    public R<Void> deleteCoupon(@PathVariable("id") Long id) {
        couponService.disable(id);
        return R.ok();
    }

    // ==================== SKU 管理 ====================

    @GetMapping("/product/{productId}/sku")
    public R<List<ProductSku>> listSkus(@PathVariable("productId") Long productId) {
        return R.ok(skuService.listByProductId(productId));
    }

    @PostMapping("/product/{productId}/sku")
    public R<ProductSku> createSku(@PathVariable("productId") Long productId,
                                    @Valid @RequestBody SkuRequest req) {
        return R.ok(skuService.create(productId, req.getSpecs(), req.getSkuCode(), req.getPrice(), req.getStock()));
    }

    @PutMapping("/product/{productId}/sku/{id}")
    public R<Void> updateSku(@PathVariable("productId") Long productId,
                              @PathVariable("id") Long id,
                              @Valid @RequestBody SkuRequest req) {
        skuService.update(productId, id, req.getSpecs(), req.getSkuCode(), req.getPrice(), req.getStock());
        return R.ok();
    }

    @DeleteMapping("/product/{productId}/sku/{id}")
    public R<Void> deleteSku(@PathVariable("productId") Long productId,
                              @PathVariable("id") Long id) {
        skuService.delete(productId, id);
        return R.ok();
    }

    // ==================== 属性管理 ====================

    @GetMapping("/product/{productId}/attribute")
    public R<List<ProductAttribute>> listAttributes(@PathVariable("productId") Long productId) {
        return R.ok(attributeService.listByProductId(productId));
    }

    @PostMapping("/product/{productId}/attribute")
    public R<ProductAttribute> createAttribute(@PathVariable("productId") Long productId,
                                                @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String value = (String) body.get("value");
        int sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0;
        return R.ok(attributeService.create(productId, name, value, sort));
    }

    @PutMapping("/product/{productId}/attribute/{id}")
    public R<Void> updateAttribute(@PathVariable("productId") Long productId,
                                    @PathVariable("id") Long id,
                                    @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String value = (String) body.get("value");
        Integer sort = body.get("sort") != null ? ((Number) body.get("sort")).intValue() : null;
        attributeService.update(productId, id, name, value, sort);
        return R.ok();
    }

    @DeleteMapping("/product/{productId}/attribute/{id}")
    public R<Void> deleteAttribute(@PathVariable("productId") Long productId,
                                    @PathVariable("id") Long id) {
        attributeService.delete(productId, id);
        return R.ok();
    }

    // ==================== 数据看板 ====================

    @GetMapping("/dashboard")
    public R<DashboardResponse> getDashboard() {
        return R.ok(dashboardService.getDashboard());
    }

    // ==================== 秒杀管理 ====================

    @PostMapping("/product/{id}/seckill")
    public R<Void> setSeckill(@PathVariable("id") Long id, @Valid @RequestBody SeckillSetRequest req) {
        seckillService.setSeckill(id, req.getPrice(), req.getStock(), req.getStartTime(), req.getEndTime());
        return R.ok();
    }

    @DeleteMapping("/product/{id}/seckill")
    public R<Void> clearSeckill(@PathVariable("id") Long id) {
        seckillService.clearSeckill(id);
        return R.ok();
    }

    // ==================== 拼团管理 ====================

    @PostMapping("/group-buy")
    public R<GroupBuy> createGroupBuy(@Valid @RequestBody GroupBuyActivityRequest req) {
        return R.ok(groupBuyService.createActivity(req));
    }

    @PutMapping("/group-buy/{id}")
    public R<Void> updateGroupBuy(@PathVariable("id") Long id, @Valid @RequestBody GroupBuyActivityRequest req) {
        groupBuyService.updateActivity(id, req);
        return R.ok();
    }

    // ==================== 用户管理 ====================

    @GetMapping("/user/list")
    public R<Map> listUsers(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String role,
                            @RequestParam(required = false) Integer status) {
        return userFeignClient.listUsers(page, size, keyword, role, status);
    }

    @PutMapping("/user/{id}/status")
    public R<Void> updateUserStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        return userFeignClient.updateUserStatus(id, status);
    }

    @PutMapping("/user/{id}/role")
    public R<Void> updateUserRole(@PathVariable("id") Long id,
                                   @RequestParam("role") String role) {
        return userFeignClient.updateUserRole(id, role);
    }

    // ==================== Banner 管理 ====================

    @GetMapping("/banner")
    public R<List<Banner>> listBanners() {
        return R.ok(bannerService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSort)));
    }

    @PostMapping("/banner")
    public R<Void> createBanner(@RequestBody Map<String, Object> body) {
        Banner banner = new Banner();
        banner.setTitle((String) body.get("title"));
        banner.setImageUrl((String) body.get("imageUrl"));
        banner.setLinkUrl((String) body.get("linkUrl"));
        banner.setSort(body.get("sort") != null ? ((Number) body.get("sort")).intValue() : 0);
        banner.setStatus(1);
        bannerService.save(banner);
        return R.ok();
    }

    @PutMapping("/banner/{id}")
    public R<Void> updateBanner(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Banner banner = bannerService.getById(id);
        if (banner == null) {
            return R.fail(500, "Banner 不存在");
        }
        if (body.containsKey("title")) banner.setTitle((String) body.get("title"));
        if (body.containsKey("imageUrl")) banner.setImageUrl((String) body.get("imageUrl"));
        if (body.containsKey("linkUrl")) banner.setLinkUrl((String) body.get("linkUrl"));
        if (body.containsKey("sort")) banner.setSort(((Number) body.get("sort")).intValue());
        if (body.containsKey("status")) banner.setStatus(((Number) body.get("status")).intValue());
        bannerService.updateById(banner);
        return R.ok();
    }

    @DeleteMapping("/banner/{id}")
    public R<Void> deleteBanner(@PathVariable("id") Long id) {
        bannerService.removeById(id);
        return R.ok();
    }
}
