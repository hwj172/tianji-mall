package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.ShopFollow;
import com.tianji.mall.mapper.ShopFollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopFollowService extends ServiceImpl<ShopFollowMapper, ShopFollow> {

    @Transactional
    public Map<String, Object> toggle(Long userId, Long shopId) {
        ShopFollow existing = baseMapper.selectOne(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getShopId, shopId));
        if (existing != null) {
            removeById(existing.getId());
            return Map.of("followed", false, "shopId", shopId,
                    "followerCount", countFollowers(shopId));
        }
        ShopFollow follow = new ShopFollow();
        follow.setUserId(userId);
        follow.setShopId(shopId);
        save(follow);
        return Map.of("followed", true, "shopId", shopId,
                "followerCount", countFollowers(shopId));
    }

    public boolean isFollowing(Long userId, Long shopId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getShopId, shopId)) > 0;
    }

    public long countFollowers(Long shopId) {
        return baseMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getShopId, shopId));
    }

    public List<ShopFollow> listFollowing(Long userId) {
        return list(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .orderByDesc(ShopFollow::getCreateTime));
    }
}
