package com.tianji.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.ShopRegisterRequest;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.service.ProductService;
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
    private final JwtUtil jwtUtil;

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Shop shop = shopService.getById(id);
        if (shop == null || shop.getStatus() == 0) {
            return R.fail(500, "店铺不存在或已关闭");
        }
        Page<Product> products = productService.getProductPage(
                null, null, null, null, null, id, page, size);
        return R.ok(Map.of("shop", shop, "products", products));
    }

    @PostMapping("/register")
    public R<Shop> register(@RequestHeader("Authorization") String authHeader,
                            @Valid @RequestBody ShopRegisterRequest req) {
        Long userId = jwtUtil.getUserId(authHeader.replace("Bearer ", ""));
        return R.ok(shopService.register(userId, req.getName(), req.getLogo(), req.getDescription()));
    }
}
