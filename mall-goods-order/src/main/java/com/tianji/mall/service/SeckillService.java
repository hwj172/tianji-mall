package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SeckillService {

    private final ProductMapper productMapper;

    public boolean isSeckillActive(Product product) {
        if (product.getSeckillPrice() == null || product.getSeckillStock() == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return product.getSeckillStartTime() != null && !now.isBefore(product.getSeckillStartTime())
                && product.getSeckillEndTime() != null && !now.isAfter(product.getSeckillEndTime())
                && product.getSeckillStock() > 0;
    }

    public Page<Product> getSeckillList(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        return productMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .isNotNull(Product::getSeckillPrice)
                        .gt(Product::getSeckillStock, 0)
                        .le(Product::getSeckillStartTime, now)
                        .ge(Product::getSeckillEndTime, now)
                        .eq(Product::getStatus, 1));
    }

    public void setSeckill(Long productId, BigDecimal price, int stock,
                           LocalDateTime startTime, LocalDateTime endTime) {
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BizException("商品不存在");
        if (stock > product.getStock()) throw new BizException("秒杀库存不能超过商品库存");

        product.setSeckillPrice(price);
        product.setSeckillStock(stock);
        product.setSeckillStartTime(startTime);
        product.setSeckillEndTime(endTime);
        productMapper.updateById(product);
    }

    public void clearSeckill(Long productId) {
        productMapper.clearSeckill(productId);
    }
}
