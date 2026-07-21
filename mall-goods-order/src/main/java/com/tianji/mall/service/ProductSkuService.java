package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSkuService extends ServiceImpl<ProductSkuMapper, ProductSku> {

    private final ProductSkuMapper productSkuMapper;

    public List<ProductSku> listByProductId(Long productId) {
        return list(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .orderByAsc(ProductSku::getId));
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public ProductSku create(Long productId, String specs, String skuCode, BigDecimal price, int stock) {
        ProductSku sku = new ProductSku();
        sku.setProductId(productId);
        sku.setSpecs(specs);
        sku.setSkuCode(skuCode);
        sku.setPrice(price);
        sku.setStock(stock);
        sku.setStatus(1);
        save(sku);
        log.info("SKU创建成功: productId={}, specs={}, stock={}", productId, specs, stock);
        return sku;
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public void update(Long productId, Long skuId, String specs, String skuCode, BigDecimal price, Integer stock) {
        ProductSku sku = getById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BizException("SKU不存在");
        }
        if (specs != null) sku.setSpecs(specs);
        if (skuCode != null) sku.setSkuCode(skuCode);
        if (price != null) sku.setPrice(price);
        if (stock != null) sku.setStock(stock);
        updateById(sku);
    }

    @CacheEvict(value = "product", key = "#productId")
    @Transactional
    public void delete(Long productId, Long skuId) {
        ProductSku sku = getById(skuId);
        if (sku == null || !sku.getProductId().equals(productId)) {
            throw new BizException("SKU不存在");
        }
        if (sku.getStock() > 0) {
            throw new BizException("库存不为0，无法删除SKU");
        }
        removeById(skuId);
    }

    // ==================== 库存操作 ====================

    public void deductStock(Long productId, Long skuId, int qty) {
        int rows = productSkuMapper.deductStock(skuId, qty);
        if (rows == 0) {
            throw new BizException("SKU库存不足");
        }
    }

    public void restoreStock(Long productId, Long skuId, int qty) {
        int rows = productSkuMapper.restoreStock(skuId, qty);
        if (rows == 0) {
            log.warn("恢复SKU库存失败: skuId={}", skuId);
        }
    }
}
