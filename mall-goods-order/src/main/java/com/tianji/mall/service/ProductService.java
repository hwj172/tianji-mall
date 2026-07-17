package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    private final AiChatFeignClient aiChatFeignClient;

    @Cacheable(value = "productPage",
               key = "'c' + #categoryId + '_k' + #keyword + '_p' + #page + '_s' + #size")
    public Page<Product> getProductPage(Long categoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Cacheable(value = "product", key = "#id")
    public Product getProductById(Long id) {
        Product product = getById(id);
        if (product == null || product.getStatus() == 0) {
            throw new BizException("商品不存在或已下架");
        }
        return product;
    }

    @CacheEvict(value = "product", key = "#productId")
    public void deductStock(Long productId, int quantity) {
        int rows = baseMapper.deductStock(productId, quantity);
        if (rows == 0) {
            throw new BizException("库存不足");
        }
    }

    @CacheEvict(value = "product", key = "#productId")
    public void restoreStock(Long productId, int quantity) {
        int rows = baseMapper.restoreStock(productId, quantity);
        if (rows == 0) {
            log.warn("恢复库存失败（商品可能被删除）: productId={}", productId);
        }
    }

    /**
     * 根据 ID 列表批量查询在售商品（供 AI RAG 管道使用）
     */
    public List<Product> getProductBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Product>()
                .in(Product::getId, ids)
                .eq(Product::getStatus, 1));
    }

    /**
     * 同步商品向量到 Milvus（best-effort，失败不影响主流程）
     */
    public void syncVector(Long productId, String name, String description) {
        try {
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "name", name != null ? name : "",
                    "description", description != null ? description : ""
            );
            aiChatFeignClient.upsertProductVector(body);
        } catch (Exception e) {
            log.warn("商品向量同步失败（不影响主流程）: productId={}", productId, e);
        }
    }
}
