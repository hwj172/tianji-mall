package com.tianji.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.result.R;
import com.tianji.common.util.JwtUtil;
import com.tianji.mall.dto.CategoryTreeResponse;
import com.tianji.mall.dto.HomeResponse;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.Banner;
import com.tianji.mall.entity.Product;
import com.tianji.mall.service.BannerService;
import com.tianji.mall.service.CategoryService;
import com.tianji.mall.service.ProductService;
import com.tianji.mall.service.RecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final BannerService bannerService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final RecommendService recommendService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public R<HomeResponse> home(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 有效 Banner
        List<Banner> banners = bannerService.list(
                new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getStatus, 1)
                        .orderByAsc(Banner::getSort));

        // 分类树
        List<CategoryTreeResponse> categories = categoryService.getCategoryTree();

        // 热销 Top 8
        List<Product> hotProducts = productService.getProductPage(
                null, null, null, null, "sales", null, 1, 8).getRecords();

        // 推荐（JWT 可选）
        Long userId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userId = jwtUtil.getUserId(authHeader.substring(7));
        }
        RecommendResponse recommend = recommendService.recommend(userId, 10);

        return R.ok(new HomeResponse(banners, categories, hotProducts, recommend));
    }
}
