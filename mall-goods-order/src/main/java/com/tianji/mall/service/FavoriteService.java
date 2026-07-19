package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.mall.entity.Favorite;
import com.tianji.mall.mapper.FavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService extends ServiceImpl<FavoriteMapper, Favorite> {

    @Transactional
    public Map<String, Object> toggle(Long userId, Long productId) {
        Favorite existing = baseMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (existing != null) {
            removeById(existing.getId());
            return Map.of("favorited", false, "productId", productId);
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        save(fav);
        return Map.of("favorited", true, "productId", productId);
    }

    public List<Favorite> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));
    }
}
