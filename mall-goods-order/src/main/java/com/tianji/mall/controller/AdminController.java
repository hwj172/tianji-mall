package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.mall.annotation.RequireAdmin;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.SkuRequest;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.dto.CouponRequest;
import com.tianji.mall.entity.Coupon;
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
}
