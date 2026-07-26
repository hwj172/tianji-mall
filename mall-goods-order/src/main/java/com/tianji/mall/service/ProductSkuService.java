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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // ==================== 规格选择器 ====================

    /**
     * 将扁平 SKU 列表转为前端规格选择器可直用的数据结构。
     * 返回 null 表示无 SKU。
     */
    public Map<String, Object> buildSpecSelectorData(List<ProductSku> skus) {
        if (skus == null || skus.isEmpty()) {
            return null;
        }

        // 按顺序收集规格名和去重值
        List<String> specNames = new ArrayList<>();
        List<Set<String>> valueSets = new ArrayList<>();

        for (ProductSku sku : skus) {
            if (sku.getSpecs() == null || sku.getSpecs().isEmpty()) {
                continue;
            }
            String[] pairs = sku.getSpecs().split(";");
            for (int i = 0; i < pairs.length; i++) {
                String[] kv = pairs[i].split(":", 2);
                if (kv.length < 2) continue;
                String name = kv[0].trim();
                String value = kv[1].trim();

                // 找或创建这个规格名的位置
                int idx = specNames.indexOf(name);
                if (idx < 0) {
                    specNames.add(name);
                    valueSets.add(new LinkedHashSet<>());
                    idx = specNames.size() - 1;
                }
                valueSets.get(idx).add(value);
            }
        }

        // 构建 specTree
        List<Map<String, Object>> specTree = new ArrayList<>();
        for (int i = 0; i < specNames.size(); i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", specNames.get(i));
            node.put("values", new ArrayList<>(valueSets.get(i)));
            specTree.add(node);
        }

        // 构建 skuMatrix：key = values 用分号拼接（与 specNames 顺序一致）
        Map<String, Map<String, Object>> skuMatrix = new LinkedHashMap<>();
        for (ProductSku sku : skus) {
            if (sku.getSpecs() == null || sku.getSpecs().isEmpty()) {
                continue;
            }
            String[] pairs = sku.getSpecs().split(";");
            List<String> orderedValues = new ArrayList<>();
            for (int i = 0; i < specNames.size(); i++) {
                orderedValues.add("");
            }
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length < 2) continue;
                int idx = specNames.indexOf(kv[0].trim());
                if (idx >= 0) {
                    orderedValues.set(idx, kv[1].trim());
                }
            }
            String key = String.join(";", orderedValues);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("skuId", sku.getId());
            entry.put("price", sku.getPrice() != null ? sku.getPrice() : null);
            entry.put("stock", sku.getStock());
            skuMatrix.put(key, entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("specTree", specTree);
        result.put("skuMatrix", skuMatrix);
        return result;
    }
}
