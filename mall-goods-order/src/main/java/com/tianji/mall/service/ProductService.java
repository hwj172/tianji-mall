package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductAttribute;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.entity.SearchLog;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.feign.AiChatFeignClient;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ReviewMapper;
import com.tianji.mall.mapper.SearchLogMapper;
import com.tianji.mall.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService extends ServiceImpl<ProductMapper, Product> {

    private final AiChatFeignClient aiChatFeignClient;
    private final ProductSkuService skuService;
    private final ProductAttributeService attributeService;
    private final ReviewMapper reviewMapper;
    private final ShopMapper shopMapper;
    private final SearchLogMapper searchLogMapper;

    @Value("${search.use-fulltext:true}")
    private boolean useFulltext;

    // NOTE: 不用 @Cacheable — Page 对象无法通过 GenericJackson2JsonRedisSerializer 正确反序列化
    public Page<Product> getProductPage(Long categoryId, String keyword,
                                        BigDecimal minPrice, BigDecimal maxPrice,
                                        String sortBy, Long shopId, Integer status, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // status 可空：null 默认只查上架商品；卖家传 0 可查下架商品（实现上架恢复）
        wrapper.eq(Product::getStatus, status != null ? status : 1);
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
            // 异步记录搜索日志（best-effort，不阻塞搜索）
            try {
                SearchLog logEntry = new SearchLog();
                logEntry.setKeyword(keyword);
                searchLogMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("搜索日志记录失败: keyword={}", keyword, e);
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

    /** 该店在售商品涉及的分类（供店铺页分类筛选 tab） */
    public List<Map<String, Object>> getShopCategories(Long shopId) {
        return baseMapper.selectShopCategories(shopId);
    }

    /**
     * 搜索建议：商品名包含匹配（按销量倒序）优先，不足时用含关键词的近 7 天热词补足，返回去重建议词。
     */
    public List<String> suggest(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try {
            List<Product> matches = list(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, 1)
                    .like(Product::getName, keyword)
                    .orderByDesc(Product::getSales)
                    .last("LIMIT " + limit));
            for (Product p : matches) {
                if (p.getName() != null && !result.contains(p.getName())) {
                    result.add(p.getName());
                    if (result.size() >= limit) break;
                }
            }
        } catch (Exception e) {
            log.warn("搜索建议商品名查询失败: {}", e.getMessage());
        }
        // 商品名结果不足时，用含该关键词的热词补足
        if (result.size() < limit) {
            try {
                List<Map<String, Object>> hotRows = getHotKeywords();
                for (Map<String, Object> row : hotRows) {
                    String word = (String) row.get("keyword");
                    if (word != null && word.contains(keyword) && !result.contains(word)) {
                        result.add(word);
                        if (result.size() >= limit) break;
                    }
                }
            } catch (Exception e) {
                log.warn("搜索建议热词补足失败: {}", e.getMessage());
            }
        }
        return result;
    }

    // NOTE: 不用 @Cacheable — GenericJackson2JsonRedisSerializer 反序列化丢失类型（LinkedHashMap）
    // 导致调用方 ClassCastException（与 getProductPage 同源）。商品量小，直接查库。
    public Product getProductById(Long id) {
        Product product = getById(id);
        if (product == null || product.getStatus() == 0) {
            throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    public Map<String, Object> getProductDetail(Long id) {
        Product product = getProductById(id);
        List<ProductSku> skus = skuService.listByProductId(id);
        List<ProductAttribute> attrs = attributeService.listByProductId(id);

        // 评价统计
        Map<String, Object> reviewStats = new LinkedHashMap<>();
        try {
            Map<String, Object> stats = reviewMapper.selectStatsByProductId(id);
            long count = ((Number) stats.get("count")).longValue();
            double avgRating = ((Number) stats.get("avgRating")).doubleValue();
            long goodCount = ((Number) stats.get("goodCount")).longValue();
            reviewStats.put("count", count);
            reviewStats.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
            reviewStats.put("goodRate", count > 0 ? Math.round(goodCount * 100.0 / count) / 100.0 : 0.0);
        } catch (Exception e) {
            log.warn("查询评价统计失败: productId={}", id, e);
            reviewStats.put("count", 0);
            reviewStats.put("avgRating", 0.0);
            reviewStats.put("goodRate", 0.0);
        }

        // 店铺信息
        Map<String, Object> shop = null;
        if (product.getShopId() != null) {
            Shop s = shopMapper.selectById(product.getShopId());
            if (s != null) {
                shop = Map.of("id", s.getId(), "name", s.getName(), "logo", s.getLogo() != null ? s.getLogo() : "");
            }
        }

        // SKU 规格选择器数据
        Map<String, Object> specSelector = skuService.buildSpecSelectorData(skus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", product);
        result.put("attributes", attrs);
        result.put("reviewStats", reviewStats);
        result.put("shop", shop);
        if (specSelector != null) {
            result.put("specTree", specSelector.get("specTree"));
            result.put("skuMatrix", specSelector.get("skuMatrix"));
        } else {
            result.put("specTree", null);
            result.put("skuMatrix", null);
        }
        return result;
    }

    @CacheEvict(value = "product", key = "#productId")
    public void deductStock(Long productId, int quantity) {
        int rows = baseMapper.deductStock(productId, quantity);
        if (rows == 0) {
            throw new BizException(BizErrorCode.STOCK_INSUFFICIENT);
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

    public List<Map<String, Object>> getHotKeywords() {
        return searchLogMapper.selectHotKeywords();
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

    @Transactional
    public Product createProduct(String name, String description, BigDecimal price,
                                  Integer stock, Long categoryId, Integer status, String images) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategoryId(categoryId);
        product.setImages(images);
        product.setStatus(status != null ? status : 1);
        save(product);
        syncVector(product.getId(), product.getName(), product.getDescription());
        return product;
    }

    @Transactional
    @CacheEvict(value = "product", key = "#id")
    public void updateProduct(Long id, String name, String description, BigDecimal price,
                               Integer stock, Long categoryId, Integer status, String images) {
        Product product = getById(id);
        if (product == null) {
            throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND);
        }
        if (name != null) product.setName(name);
        if (description != null) product.setDescription(description);
        if (price != null) product.setPrice(price);
        if (stock != null) product.setStock(stock);
        if (categoryId != null) product.setCategoryId(categoryId);
        if (status != null) product.setStatus(status);
        if (images != null) product.setImages(images);
        updateById(product);
        syncVector(product.getId(), product.getName(), product.getDescription());
    }

    @CacheEvict(value = "product", key = "#id")
    public void deleteProduct(Long id) {
        Product product = getById(id);
        if (product == null) {
            throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND);
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

    // ==================== 以图搜图 ====================

    /**
     * 全量回填商品首图向量（best-effort，VL-Embedding，幂等可重复触发）
     */
    public Map<String, Integer> syncAllImageVectors() {
        List<Product> products = list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .isNotNull(Product::getImages));
        int success = 0;
        for (Product p : products) {
            if (syncImageVector(p)) {
                success++;
            }
        }
        log.info("商品图片向量回填完成: total={}, success={}", products.size(), success);
        return Map.of("total", products.size(),
                "success", success,
                "failed", products.size() - success);
    }

    /** 同步单个商品首图向量 */
    private boolean syncImageVector(Product p) {
        String firstImage = extractFirstImage(p.getImages());
        if (firstImage == null || firstImage.isBlank()) {
            return false;
        }
        try {
            aiChatFeignClient.upsertProductImage(Map.of("productId", p.getId(), "imageUrl", firstImage));
            return true;
        } catch (Exception e) {
            log.warn("商品图片向量同步失败: productId={}", p.getId(), e);
            return false;
        }
    }

    /**
     * 以图搜图：图片 URL → 相似商品（best-effort，失败返回空列表）
     */
    public List<Product> imageSearch(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> result = aiChatFeignClient.imageSearch(Map.of("imageUrl", imageUrl));
            Object data = result != null ? result.get("data") : null;
            if (data == null) {
                return List.of();
            }
            List<Long> ids = ((List<?>) data).stream()
                    .map(o -> ((Number) o).longValue())
                    .toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            return listByIds(ids);
        } catch (Exception e) {
            log.warn("以图搜图失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 从 images JSON 数组提取首图 URL（非 JSON 视为单 URL） */
    private String extractFirstImage(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        try {
            var arr = new com.fasterxml.jackson.databind.ObjectMapper().readTree(images);
            if (arr.isArray() && !arr.isEmpty()) {
                return arr.get(0).asText();
            }
            if (arr.isTextual()) {
                return arr.asText();
            }
        } catch (Exception ignored) {
            // 非 JSON 直接视为单图 URL
        }
        return images;
    }
}
