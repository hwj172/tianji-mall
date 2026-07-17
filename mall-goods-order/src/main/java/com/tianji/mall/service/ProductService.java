package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService extends ServiceImpl<ProductMapper, Product> {

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
}
