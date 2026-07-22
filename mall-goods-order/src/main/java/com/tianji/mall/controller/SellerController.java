package com.tianji.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.ShopUpdateRequest;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.service.OrderService;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商家后台 — 网关 seller 角色鉴权（/api/seller/** → role=seller）
 */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ShopService shopService;
    private final ProductService productService;
    private final OrderService orderService;
    private final JwtUtil jwtUtil;

    // ===== 店铺管理 =====

    @GetMapping("/shop")
    public R<Shop> myShop(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(shopService.getBySellerId(userId));
    }

    @PutMapping("/shop")
    public R<Void> updateShop(@RequestHeader("Authorization") String authHeader,
                               @Valid @RequestBody ShopUpdateRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        shopService.updateShopInfo(userId, req.getName(), req.getLogo(), req.getDescription());
        return R.ok();
    }

    // ===== 商品管理 =====

    @GetMapping("/products")
    public R<Page<Product>> products(@RequestHeader("Authorization") String authHeader,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        Page<Product> result = productService.getProductPage(
                null, null, null, null, null, shop.getId(), page, size);
        // 过滤本店商品 — 后续 Product 加 shopId 后由 Service 层筛选
        return R.ok(result);
    }

    @PostMapping("/product")
    public R<Void> createProduct(@RequestHeader("Authorization") String authHeader,
                                  @RequestBody Product product) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        product.setShopId(shop.getId());
        productService.save(product);
        return R.ok();
    }

    @PutMapping("/product/{id}")
    public R<Void> updateProduct(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable("id") Long id,
                                  @RequestBody Product product) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        Product existing = productService.getById(id);
        if (existing == null || !existing.getShopId().equals(shop.getId())) {
            return R.fail(500, "商品不存在");
        }
        product.setId(id);
        product.setShopId(shop.getId());
        productService.updateById(product);
        return R.ok();
    }

    @DeleteMapping("/product/{id}")
    public R<Void> deleteProduct(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        Product existing = productService.getById(id);
        if (existing == null || !existing.getShopId().equals(shop.getId())) {
            return R.fail(500, "商品不存在");
        }
        existing.setStatus(0); // 软删除
        productService.updateById(existing);
        return R.ok();
    }

    // ===== 订单管理 =====

    @GetMapping("/orders")
    public R<Page<Order>> orders(@RequestHeader("Authorization") String authHeader,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        return R.ok(orderService.page(new Page<>(page, size)));
    }

    @PutMapping("/order/{id}/ship")
    public R<Void> shipOrder(@RequestHeader("Authorization") String authHeader,
                              @PathVariable("id") Long id) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        Order order = orderService.getById(id);
        if (order == null) {
            return R.fail(500, "订单不存在");
        }
        orderService.shipOrder(id, "", "");
        return R.ok();
    }

    // ===== 数据看板 =====

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        Shop shop = shopService.getBySellerId(userId);
        // 简化版看板：本店基本信息
        long productCount = productService.count(
                new LambdaQueryWrapper<Product>().eq(Product::getShopId, shop.getId()).eq(Product::getStatus, 1));
        return R.ok(Map.of("shop", shop, "productCount", productCount));
    }
}
