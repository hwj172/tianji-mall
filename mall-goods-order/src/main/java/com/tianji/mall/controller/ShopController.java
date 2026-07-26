package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.ShopRegisterRequest;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.ShopFollowService;
import com.tianji.mall.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ProductService productService;
    private final ShopFollowService shopFollowService;
    private final JwtUtil jwtUtil;

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Shop shop = shopService.getById(id);
        if (shop == null || shop.getStatus() == 0) {
            return R.fail(500, "店铺不存在或已关闭");
        }
        Page<Product> products = productService.getProductPage(
                null, null, null, null, null, id, page, size);
        long followerCount = shopFollowService.countFollowers(id);
        boolean isFollowing = false;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
            isFollowing = shopFollowService.isFollowing(userId, id);
        }
        return R.ok(Map.of("shop", shop, "products", products,
                "followerCount", followerCount, "isFollowing", isFollowing));
    }

    @PostMapping("/register")
    public R<Shop> register(@RequestHeader("Authorization") String authHeader,
                            @Valid @RequestBody ShopRegisterRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(shopService.register(userId, req.getName(), req.getLogo(), req.getDescription()));
    }

    @PostMapping("/{id}/follow")
    public R<Map<String, Object>> follow(@RequestHeader("Authorization") String authHeader,
                                          @PathVariable("id") Long shopId) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(shopFollowService.toggle(userId, shopId));
    }

    @GetMapping("/following")
    public R<java.util.List<com.tianji.mall.entity.ShopFollow>> following(
            @RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(shopFollowService.listFollowing(userId));
    }
}
