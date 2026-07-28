package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Shop;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ShopService extends ServiceImpl<ShopMapper, Shop> {

    @Autowired(required = false)
    private UserFeignClient userFeignClient;

    /**
     * 注册开店 — 创建店铺 + 将用户角色提升为 seller
     */
    @Transactional
    public Shop register(Long userId, String name, String logo, String description) {
        Shop existing = getOne(new LambdaQueryWrapper<Shop>().eq(Shop::getSellerId, userId));
        if (existing != null) {
            throw new BizException(BizErrorCode.ALREADY_HAS_SHOP);
        }

        Shop shop = new Shop();
        shop.setName(name);
        shop.setLogo(logo);
        shop.setDescription(description);
        shop.setSellerId(userId);
        shop.setStatus(1);
        save(shop);

        // 提升用户角色（best-effort）
        if (userFeignClient != null) {
            try {
                userFeignClient.promoteToSeller(userId);
            } catch (Exception e) {
                log.warn("Failed to promote user {} to seller: {}", userId, e.getMessage());
            }
        }

        return shop;
    }

    public Shop getBySellerId(Long userId) {
        Shop shop = getOne(new LambdaQueryWrapper<Shop>().eq(Shop::getSellerId, userId));
        if (shop == null) {
            throw new BizException(BizErrorCode.NO_SHOP);
        }
        return shop;
    }

    public void updateShopInfo(Long userId, String name, String logo, String description) {
        Shop shop = getBySellerId(userId);
        if (name != null && !name.isBlank()) {
            shop.setName(name);
        }
        if (logo != null) {
            shop.setLogo(logo);
        }
        if (description != null) {
            shop.setDescription(description);
        }
        updateById(shop);
    }
}
