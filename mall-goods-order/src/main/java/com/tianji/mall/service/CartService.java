package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CartAddRequest;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Product;
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

    public List<CartItem> getCartList(Long userId) {
        return list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
    }

    @Transactional
    public void addItem(Long userId, CartAddRequest req) {
        // 检查商品是否存在且上架
        Product product = productService.getProductById(req.getProductId());
        if (product.getStock() < req.getQuantity()) {
            throw new BizException("库存不足");
        }

        // 同一商品已存在则增加数量
        CartItem existing = getOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, req.getProductId()));
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + req.getQuantity());
            updateById(existing);
            return;
        }

        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(req.getProductId());
        item.setQuantity(req.getQuantity());
        item.setChecked(1);
        save(item);
        log.info("加购成功: userId={}, productId={}, quantity={}", userId, req.getProductId(), req.getQuantity());
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
