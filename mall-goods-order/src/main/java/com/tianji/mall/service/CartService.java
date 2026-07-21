package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductSku;
import com.tianji.mall.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService extends ServiceImpl<CartItemMapper, CartItem> {

    private final ProductService productService;
    private final ProductSkuService skuService;

    public List<CartItem> getCartList(Long userId) {
        return list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
    }

    @Transactional
    public void addItem(Long userId, CartAddRequest req) {
        Product product = productService.getProductById(req.getProductId());

        // SKU 商品：校验 SKU 存在 + 库存
        if (req.getSkuId() != null) {
            ProductSku sku = skuService.getById(req.getSkuId());
            if (sku == null || !sku.getProductId().equals(req.getProductId())) {
                throw new BizException("SKU不存在");
            }
            if (sku.getStock() < req.getQuantity()) {
                throw new BizException("库存不足");
            }
        } else {
            // 无 SKU：使用商品级库存（向后兼容）
            if (product.getStock() < req.getQuantity()) {
                throw new BizException("库存不足");
            }
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
            existing.setQuantity(existing.getQuantity() + req.getQuantity());
            updateById(existing);
            return;
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
            throw new BizException("购物车项不存在");
        }
        item.setQuantity(quantity);
        updateById(item);
        log.info("购物车数量更新: userId={}, cartItemId={}, quantity={}", userId, cartItemId, quantity);
    }

    @Transactional
    public void deleteItem(Long userId, Long cartItemId) {
        CartItem item = getById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException("购物车项不存在");
        }
        removeById(cartItemId);
        log.info("购物车项删除: userId={}, cartItemId={}", userId, cartItemId);
    }

    @Transactional
    public void checkItem(Long userId, Long cartItemId, Integer checked) {
        CartItem item = getById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException("购物车项不存在");
        }
        item.setChecked(checked);
        updateById(item);
    }
}
