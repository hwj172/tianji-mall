package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.dto.RecommendResponse;
import com.tianji.mall.entity.Favorite;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Review;
import com.tianji.mall.mapper.FavoriteMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 热销榜缓存服务。
 * 独立成 Bean，使 {@link Cacheable} 通过 Spring 代理生效——若留在 RecommendService 内部，
 * getHotSales() 的 this 自调用会绕过代理导致缓存永不生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotSalesCacheService {

    private final ProductMapper productMapper;
    private final FavoriteMapper favoriteMapper;
    private final ReviewMapper reviewMapper;

    private static final int TOP_N = 50;

    @Cacheable(value = "recommend", key = "'hot_sales'")
    public List<RecommendResponse.RecommendItem> computeHotSales() {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1));
        // 加权评分：销量×0.5 + 收藏×0.3 + 评价×0.2，按真实加权分排序（此前误用 sales 排序）
        Map<Long, Double> scores = new HashMap<>();
        for (Product p : products) {
            scores.put(p.getId(), p.getSales() * 0.5
                    + countFavorites(p.getId()) * 0.3
                    + countReviews(p.getId()) * 0.2);
        }
        return products.stream()
                .sorted(Comparator.comparingDouble((Product p) -> scores.getOrDefault(p.getId(), 0d)).reversed())
                .limit(TOP_N)
                .map(p -> new RecommendResponse.RecommendItem(
                        p.getId(), p.getName(), p.getPrice(), (long) p.getSales(), "", p.getImages()))
                .collect(Collectors.toList());
    }

    private Long countFavorites(Long productId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getProductId, productId));
    }

    private Long countReviews(Long productId) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getProductId, productId));
    }
}
