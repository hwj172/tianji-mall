package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.dto.CartItemDTO;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService extends ServiceImpl<CartItemMapper, CartItem> {

    private final ProductService productService;
    private final ProductSkuService skuService;

    public List<CartItemDTO> getCartList(Long userId) {
        List<CartItem> items = list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> skuIds = items.stream()
                .map(CartItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuService.listByIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, s -> s));

        // 商品批量查询：无 SKU 的购物车项取商品默认价
        List<Long> productIds = items.stream()
                .map(CartItem::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                : productService.listByIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        return items.stream().map(item -> {
            CartItemDTO dto = new CartItemDTO();
            BeanUtils.copyProperties(item, dto);   // CartItemDTO 的 skuSpecs 不在 CartItem 中，复制后保持默认 null
            ProductSku sku = item.getSkuId() != null ? skuMap.get(item.getSkuId()) : null;
            dto.setSkuSpecs(sku != null ? sku.getSpecs() : null);
            if (sku != null) {
                dto.setPrice(sku.getPrice());
            } else {
                Product product = productMap.get(item.getProductId());
                if (product != null) {
                    dto.setPrice(product.getPrice());
                }
            }
            return dto;
        }).toList();
    }

    @Transactional
    public void addItem(Long userId, CartAddRequest req) {
        Product product = productService.getProductById(req.getProductId());

        // SKU 商品：校验 SKU 存在 + 库存
        int availableStock;
        if (req.getSkuId() != null) {
            ProductSku sku = skuService.getById(req.getSkuId());
            if (sku == null || !sku.getProductId().equals(req.getProductId())) {
                throw new BizException(BizErrorCode.SKU_NOT_FOUND);
            }
            availableStock = sku.getStock();
        } else {
            // 无 SKU：使用商品级库存（向后兼容）
            availableStock = product.getStock() == null ? 0 : product.getStock();
        }

        // 去重：productId + skuId 相同则合并数量
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, req.getProductId());
        if (req.getSkuId() != null) {
            wrapper.eq(CartItem::getSkuId, req.getSkuId());
        } else {
            wrapper.isNull(CartItem::getSkuId);
        }
        CartItem existing = getOne(wrapper);
        if (existing != null) {
            // 合并后总量不能超库存（每次加购少量绕过单次库存校验）
            int total = existing.getQuantity() + req.getQuantity();
            if (total > availableStock) {
                throw new BizException(BizErrorCode.STOCK_INSUFFICIENT);
            }
            existing.setQuantity(total);
            updateById(existing);
            return;
        }
        if (req.getQuantity() > availableStock) {
            throw new BizException(BizErrorCode.STOCK_INSUFFICIENT);
        }

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(req.getProductId());
        item.setSkuId(req.getSkuId());
        item.setQuantity(req.getQuantity());
        item.setChecked(1);
        save(item);
        log.info("加购成功: userId={}, productId={}, skuId={}, quantity={}",
                userId, req.getProductId(), req.getSkuId(), req.getQuantity());
    }

    @Transactional
    public void updateQuantity(Long userId, Long cartItemId, int quantity) {
        CartItem item = getById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
        }
        item.setQuantity(quantity);
        updateById(item);
        log.info("购物车数量更新: userId={}, cartItemId={}, quantity={}", userId, cartItemId, quantity);
    }

    @Transactional
    public void deleteItem(Long userId, Long cartItemId) {
        CartItem item = getById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
        }
        removeById(cartItemId);
        log.info("购物车项删除: userId={}, cartItemId={}", userId, cartItemId);
    }

    @Transactional
    public void checkItem(Long userId, Long cartItemId, Integer checked) {
        CartItem item = getById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
        }
        item.setChecked(checked);
        updateById(item);
    }
}
