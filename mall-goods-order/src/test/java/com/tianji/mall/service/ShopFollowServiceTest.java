package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.mall.entity.ShopFollow;
import com.tianji.mall.mapper.ShopFollowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopFollowServiceTest {

    @Mock
    private ShopFollowMapper shopFollowMapper;

    private ShopFollowService shopFollowService;

    @BeforeEach
    void setUp() {
        shopFollowService = new ShopFollowService();
        ReflectionTestUtils.setField(shopFollowService, "baseMapper", shopFollowMapper);
    }

    @Test
    void shouldToggleToFollow() {
        when(shopFollowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(shopFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        Map<String, Object> result = shopFollowService.toggle(1L, 1L);

        assertThat(result.get("followed")).isEqualTo(true);
        assertThat(result.get("shopId")).isEqualTo(1L);
        assertThat(result.get("followerCount")).isEqualTo(1L);
        verify(shopFollowMapper).insert(any(ShopFollow.class));
    }

    @Test
    void shouldToggleToUnfollow() {
        ShopFollow existing = new ShopFollow();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setShopId(1L);
        when(shopFollowMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(shopFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> result = shopFollowService.toggle(1L, 1L);

        assertThat(result.get("followed")).isEqualTo(false);
        assertThat(result.get("followerCount")).isEqualTo(0L);
        verify(shopFollowMapper).deleteById(10L);
    }

    @Test
    void shouldCheckIsFollowingTrue() {
        when(shopFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        boolean result = shopFollowService.isFollowing(1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    void shouldCheckIsFollowingFalse() {
        when(shopFollowMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        boolean result = shopFollowService.isFollowing(1L, 1L);

        assertThat(result).isFalse();
    }

    @Test
    void shouldListFollowing() {
        ShopFollow follow = new ShopFollow();
        follow.setId(1L);
        follow.setUserId(1L);
        follow.setShopId(1L);
        when(shopFollowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(follow));

        List<ShopFollow> result = shopFollowService.listFollowing(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShopId()).isEqualTo(1L);
    }
}
