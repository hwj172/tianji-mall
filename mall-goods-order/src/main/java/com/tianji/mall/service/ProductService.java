package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    private final AiChatFeignClient aiChatFeignClient;
    private final ProductSkuService skuService;
    private final ProductAttributeService attributeService;

    @Value("${search.use-fulltext:true}")
    private boolean useFulltext;

    @Cacheable(value = "productPage",
               key = "'c' + #categoryId + '_k' + #keyword + '_min' + #minPrice + '_max' + #maxPrice + '_sort' + #sortBy + '_shop' + #shopId + '_p' + #page + '_sz' + #size")
    public Page<Product> getProductPage(Long categoryId, String keyword,
                                        BigDecimal minPrice, BigDecimal maxPrice,
                                        String sortBy, Long shopId, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (shopId != null) {
            wrapper.eq(Product::getShopId, shopId);
        }
        if (minPrice != null) {
            wrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Product::getPrice, maxPrice);
        }
        if (StringUtils.hasText(keyword)) {
            if (useFulltext) {
                wrapper.apply("MATCH(name, description) AGAINST({0} IN BOOLEAN MODE)", keyword);
            } else {
                wrapper.and(w -> w.like(Product::getName, keyword)
                        .or().like(Product::getDescription, keyword));
            }
        }
        // 排序
        if ("price_asc".equals(sortBy)) {
            wrapper.orderByAsc(Product::getPrice);
        } else if ("price_desc".equals(sortBy)) {
            wrapper.orderByDesc(Product::getPrice);
        } else if ("sales".equals(sortBy)) {
            wrapper.orderByDesc(Product::getSales);
        } else {
            wrapper.orderByDesc(Product::getCreateTime);
        }
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

    public Map<String, Object> getProductDetail(Long id) {
        Product product = getProductById(id);
        List<ProductSku> skus = skuService.listByProductId(id);
        List<ProductAttribute> attrs = attributeService.listByProductId(id);
        return Map.of("product", product, "skus", skus, "attributes", attrs);
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

    @CacheEvict(value = "product", key = "#productId")
    public void incrementSales(Long productId, int quantity) {
        baseMapper.incrementSales(productId, quantity);
    }

    /**
     * 统计指定分类下的商品数量（供分类删除校验使用）
     */
    public long countByCategoryId(Long categoryId) {
        return count(new LambdaQueryWrapper<Product>()
                .eq(Product::getCategoryId, categoryId)
                .eq(Product::getStatus, 1));
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
     *
     * @return 是否同步成功
     */
    public boolean syncVector(Long productId, String name, String description) {
        try {
            Map<String, Object> body = Map.of(
                    "productId", productId,
                    "name", name != null ? name : "",
                    "description", description != null ? description : ""
            );
            aiChatFeignClient.upsertProductVector(body);
            return true;
        } catch (Exception e) {
            log.warn("商品向量同步失败（不影响主流程）: productId={}", productId, e);
            return false;
        }
    }

    // ==================== 后台管理方法 ====================

    /**
     * 后台商品分页查询（含已下架商品，可选分类过滤）
     */
    public Page<Product> getProductPageAdmin(int page, int size, Long categoryId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public Product createProduct(String name, String description, BigDecimal price,
                                  Integer stock, Long categoryId, String images) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategoryId(categoryId);
        product.setImages(images);
        product.setStatus(1);
        save(product);
        return product;
    }

    public void updateProduct(Long id, String name, String description, BigDecimal price,
                               Integer stock, Long categoryId, Integer status, String images) {
        Product product = getById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        if (name != null) product.setName(name);
        if (description != null) product.setDescription(description);
        if (price != null) product.setPrice(price);
        if (stock != null) product.setStock(stock);
        if (categoryId != null) product.setCategoryId(categoryId);
        if (status != null) product.setStatus(status);
        if (images != null) product.setImages(images);
        updateById(product);
    }

    public void deleteProduct(Long id) {
        Product product = getById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        product.setStatus(0);
        updateById(product);
    }

    /**
     * 全量回填：把所有上架商品向量同步到 Milvus（Milvus upsert 幂等，可重复触发）
     */
    public Map<String, Integer> syncAllVectors() {
        List<Product> products = list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        int success = 0;
        for (Product p : products) {
            if (syncVector(p.getId(), p.getName(), p.getDescription())) {
                success++;
            }
        }
        log.info("商品向量回填完成: total={}, success={}", products.size(), success);
        return Map.of("total", products.size(),
                      "success", success,
                      "failed", products.size() - success);
    }
}
