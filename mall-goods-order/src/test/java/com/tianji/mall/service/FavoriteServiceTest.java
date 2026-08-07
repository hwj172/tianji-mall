package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.entity.Favorite;
import com.tianji.mall.mapper.FavoriteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService();
        ReflectionTestUtils.setField(favoriteService, "baseMapper", favoriteMapper);
    }

    @Test
    void shouldAddFavoriteWhenNotExists() {
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(favoriteMapper.insert(any(Favorite.class))).thenReturn(1);

        Map<String, Object> result = favoriteService.toggle(100L, 1L);

        assertThat(result).containsEntry("favorited", true).containsEntry("productId", 1L);
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(100L);
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
    }

    @Test
    void shouldRemoveFavoriteWhenExists() {
        Favorite existing = new Favorite();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setProductId(1L);
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(favoriteMapper.deleteById(10L)).thenReturn(1);

        Map<String, Object> result = favoriteService.toggle(100L, 1L);

        assertThat(result).containsEntry("favorited", false).containsEntry("productId", 1L);
        verify(favoriteMapper).deleteById(10L);
    }

    @Test
    void shouldListFavoritesByUser() {
        Favorite f1 = new Favorite();
        f1.setId(1L);
        f1.setUserId(100L);
        f1.setProductId(10L);
        when(favoriteMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Favorite> p = inv.getArgument(0);
            p.setRecords(List.of(f1));
            p.setTotal(1);
            return p;
        });

        Page<Favorite> result = favoriteService.listByUser(100L, 1, 20);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getProductId()).isEqualTo(10L);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyListWhenNoFavorites() {
        when(favoriteMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Favorite> p = inv.getArgument(0);
            p.setRecords(List.of());
            p.setTotal(0);
            return p;
        });

        Page<Favorite> result = favoriteService.listByUser(999L, 1, 20);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    void shouldReturnFavoritedTrueWhenFavoriteExists() {
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThat(favoriteService.isFavorited(100L, 1L)).isTrue();
        verify(favoriteMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnFavoritedFalseWhenNotFavorite() {
        when(favoriteMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThat(favoriteService.isFavorited(100L, 1L)).isFalse();
        verify(favoriteMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldToggleBetweenStates() {
        // First toggle: add
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(favoriteMapper.insert(any(Favorite.class))).thenReturn(1);
        Map<String, Object> result1 = favoriteService.toggle(100L, 1L);
        assertThat(result1).containsEntry("favorited", true);

        // Second toggle: remove
        Favorite existing = new Favorite();
        existing.setId(10L);
        when(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(favoriteMapper.deleteById(10L)).thenReturn(1);
        Map<String, Object> result2 = favoriteService.toggle(100L, 1L);
        assertThat(result2).containsEntry("favorited", false);
    }
}
