package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.result.R;
import com.tianji.mall.annotation.AuditLog;
import com.tianji.mall.annotation.RequireAdmin;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.DashboardResponse;
import com.tianji.mall.dto.GroupBuyActivityRequest;
import com.tianji.mall.dto.SeckillSetRequest;
import com.tianji.mall.dto.AdminAttributeRequest;
import com.tianji.mall.dto.AdminCategoryRequest;
import com.tianji.mall.dto.AdminProductRequest;
import com.tianji.mall.dto.BannerRequest;
import com.tianji.mall.dto.SkuRequest;
import com.tianji.mall.entity.Banner;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.entity.Promotion;
import com.tianji.mall.dto.CouponRequest;
import com.tianji.mall.dto.NotificationRequest;
import com.tianji.mall.dto.PromotionRequest;
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
    private final ShopService shopService;
    private final BannerService bannerService;
    private final PromotionService promotionService;
    private final NotificationService notificationService;
    private final UserFeignClient userFeignClient;

    // ==================== 店铺管理 ====================

    @GetMapping("/shop/list")
    public R<List<com.tianji.mall.entity.Shop>> shopList() {
        return R.ok(shopService.list());
    }

    @PutMapping("/shop/{id}/status")
    @AuditLog(action = "update_shop_status", targetType = "shop", targetArg = 0)
    public R<Void> updateShopStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        com.tianji.mall.entity.Shop shop = shopService.getById(id);
        if (shop == null) {
            return R.fail(BizErrorCode.SHOP_NOT_FOUND);
        }
        shop.setStatus(status);
        shopService.updateById(shop);
        return R.ok();
    }

    @DeleteMapping("/shop/{id}")
    @AuditLog(action = "delete_shop", targetType = "shop", targetArg = 0)
    public R<Void> deleteShop(@PathVariable("id") Long id) {
        com.tianji.mall.entity.Shop shop = shopService.getById(id);
        if (shop == null) {
            return R.fail(BizErrorCode.SHOP_NOT_FOUND);
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
    public R<Void> createCategory(@RequestBody @Valid AdminCategoryRequest body) {
        categoryService.createCategory(body.getName(),
                body.getParentId() != null ? body.getParentId() : 0L,
                body.getSort() != null ? body.getSort() : 0);
        return R.ok();
    }

    @PutMapping("/category/{id}")
    public R<Void> updateCategory(@PathVariable("id") Long id, @RequestBody @Valid AdminCategoryRequest body) {
        categoryService.updateCategory(id, body.getName(),
                body.getSort() != null ? body.getSort() : 0);
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
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) Integer status) {
        return R.ok(productService.getProductPageAdmin(page, size, categoryId, status));
    }

    @PostMapping("/product")
    @AuditLog(action = "create_product", targetType = "product")
    public R<Void> createProduct(@RequestBody @Valid AdminProductRequest body) {
        productService.createProduct(
                body.getName(),
                body.getDescription(),
                body.getPrice(),
                body.getStock(),
                body.getCategoryId(),
                body.getStatus(),
                body.getImages());
        return R.ok();
    }

    @PutMapping("/product/{id}")
    @AuditLog(action = "update_product", targetType = "product", targetArg = 0)
    public R<Void> updateProduct(@PathVariable("id") Long id, @RequestBody @Valid AdminProductRequest body) {
        productService.updateProduct(id,
                body.getName(),
                body.getDescription(),
                body.getPrice(),
                body.getStock(),
                body.getCategoryId(),
                body.getStatus(),
                body.getImages());
        return R.ok();
    }

    @DeleteMapping("/product/{id}")
    @AuditLog(action = "delete_product", targetType = "product", targetArg = 0)
    public R<Void> deleteProduct(@PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return R.ok();
    }

    /** 商品上架审核：通过（待审核 2 → 上架 1） */
    @PutMapping("/product/{id}/approve")
    @AuditLog(action = "approve_product", targetType = "product", targetArg = 0)
    public R<Void> approveProduct(@PathVariable("id") Long id) {
        productService.updateProductStatus(id, 1);
        return R.ok();
    }

    /** 商品上架审核：拒绝（待审核 2 → 下架 0） */
    @PutMapping("/product/{id}/reject")
    @AuditLog(action = "reject_product", targetType = "product", targetArg = 0)
    public R<Void> rejectProduct(@PathVariable("id") Long id) {
        productService.updateProductStatus(id, 0);
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
    @AuditLog(action = "create_coupon", targetType = "coupon")
    public R<Coupon> createCoupon(@Valid @RequestBody CouponRequest req) {
        return R.ok(couponService.create(req));
    }

    @PutMapping("/coupon/{id}")
    @AuditLog(action = "update_coupon", targetType = "coupon", targetArg = 0)
    public R<Void> updateCoupon(@PathVariable("id") Long id, @Valid @RequestBody CouponRequest req) {
        couponService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/coupon/{id}")
    @AuditLog(action = "delete_coupon", targetType = "coupon", targetArg = 0)
    public R<Void> deleteCoupon(@PathVariable("id") Long id) {
        couponService.disable(id);
        return R.ok();
    }

    // ==================== 满减活动管理 ====================

    @GetMapping("/promotion")
    public R<List<Promotion>> listPromotions(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return R.ok(promotionService.listByPage(page, size));
    }

    @PostMapping("/promotion")
    @AuditLog(action = "create_promotion", targetType = "promotion")
    public R<Promotion> createPromotion(@Valid @RequestBody PromotionRequest req) {
        return R.ok(promotionService.create(req));
    }

    @PutMapping("/promotion/{id}")
    @AuditLog(action = "update_promotion", targetType = "promotion", targetArg = 0)
    public R<Void> updatePromotion(@PathVariable("id") Long id, @Valid @RequestBody PromotionRequest req) {
        promotionService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/promotion/{id}")
    @AuditLog(action = "delete_promotion", targetType = "promotion", targetArg = 0)
    public R<Void> deletePromotion(@PathVariable("id") Long id) {
        promotionService.disable(id);
        return R.ok();
    }

    // ===== 系统公告（面向全量用户广播，userId=0） =====

    @PostMapping("/notification")
    @AuditLog(action = "create_announcement", targetType = "notification")
    public R<Void> createAnnouncement(@Valid @RequestBody NotificationRequest req) {
        notificationService.createNotification(0L, "SYSTEM_ANNOUNCEMENT", req.getTitle(), req.getContent(), null);
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
                                                @RequestBody @Valid AdminAttributeRequest body) {
        return R.ok(attributeService.create(productId,
                body.getName(), body.getValue(),
                body.getSort() != null ? body.getSort() : 0));
    }

    @PutMapping("/product/{productId}/attribute/{id}")
    public R<Void> updateAttribute(@PathVariable("productId") Long productId,
                                    @PathVariable("id") Long id,
                                    @RequestBody @Valid AdminAttributeRequest body) {
        attributeService.update(productId, id, body.getName(), body.getValue(), body.getSort());
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
    @AuditLog(action = "set_seckill", targetType = "product", targetArg = 0)
    public R<Void> setSeckill(@PathVariable("id") Long id, @Valid @RequestBody SeckillSetRequest req) {
        seckillService.setSeckill(id, req.getPrice(), req.getStock(), req.getStartTime(), req.getEndTime());
        return R.ok();
    }

    @DeleteMapping("/product/{id}/seckill")
    @AuditLog(action = "clear_seckill", targetType = "product", targetArg = 0)
    public R<Void> clearSeckill(@PathVariable("id") Long id) {
        seckillService.clearSeckill(id);
        return R.ok();
    }

    // ==================== 拼团管理 ====================

    @PostMapping("/group-buy")
    @AuditLog(action = "create_group_buy", targetType = "group_buy")
    public R<GroupBuy> createGroupBuy(@Valid @RequestBody GroupBuyActivityRequest req) {
        return R.ok(groupBuyService.createActivity(req));
    }

    @PutMapping("/group-buy/{id}")
    @AuditLog(action = "update_group_buy", targetType = "group_buy", targetArg = 0)
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
    @AuditLog(action = "update_user_status", targetType = "user", targetArg = 0)
    public R<Void> updateUserStatus(@PathVariable("id") Long id,
                                     @RequestParam("status") Integer status) {
        return userFeignClient.updateUserStatus(id, status);
    }

    @PutMapping("/user/{id}/role")
    @AuditLog(action = "update_user_role", targetType = "user", targetArg = 0)
    public R<Void> updateUserRole(@PathVariable("id") Long id,
                                   @RequestParam("role") String role) {
        return userFeignClient.updateUserRole(id, role);
    }

    @GetMapping("/user/pending-profiles")
    public R<List<Map<String, Object>>> pendingProfiles() {
        return userFeignClient.getPendingProfiles();
    }

    @PutMapping("/user/audit-profile/{id}")
    @AuditLog(action = "audit_user_profile", targetType = "user", targetArg = 0)
    public R<Void> auditProfile(@PathVariable("id") Long id,
                                 @RequestParam("approve") boolean approve) {
        return userFeignClient.auditProfile(id, approve);
    }

    // ==================== Banner 管理 ====================

    @GetMapping("/banner")
    public R<List<Banner>> listBanners() {
        return R.ok(bannerService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSort)));
    }

    @PostMapping("/banner")
    @AuditLog(action = "create_banner", targetType = "banner")
    public R<Void> createBanner(@RequestBody @Valid BannerRequest body) {
        Banner banner = new Banner();
        banner.setTitle(body.getTitle());
        banner.setImageUrl(body.getImageUrl());
        banner.setLinkUrl(body.getLinkUrl());
        banner.setSort(body.getSort() != null ? body.getSort() : 0);
        banner.setStatus(1);
        bannerService.save(banner);
        return R.ok();
    }

    @PutMapping("/banner/{id}")
    @AuditLog(action = "update_banner", targetType = "banner", targetArg = 0)
    public R<Void> updateBanner(@PathVariable("id") Long id, @RequestBody @Valid BannerRequest body) {
        Banner banner = bannerService.getById(id);
        if (banner == null) {
            return R.fail(500, "Banner 不存在");
        }
        if (body.getTitle() != null) banner.setTitle(body.getTitle());
        if (body.getImageUrl() != null) banner.setImageUrl(body.getImageUrl());
        if (body.getLinkUrl() != null) banner.setLinkUrl(body.getLinkUrl());
        if (body.getSort() != null) banner.setSort(body.getSort());
        if (body.getStatus() != null) banner.setStatus(body.getStatus());
        bannerService.updateById(banner);
        return R.ok();
    }

    @DeleteMapping("/banner/{id}")
    @AuditLog(action = "delete_banner", targetType = "banner", targetArg = 0)
    public R<Void> deleteBanner(@PathVariable("id") Long id) {
        bannerService.removeById(id);
        return R.ok();
    }
}
