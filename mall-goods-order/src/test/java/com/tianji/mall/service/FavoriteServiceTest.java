package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        when(favoriteMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(f1));

        List<Favorite> result = favoriteService.listByUser(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductId()).isEqualTo(10L);
    }

    @Test
    void shouldReturnEmptyListWhenNoFavorites() {
        when(favoriteMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<Favorite> result = favoriteService.listByUser(999L);

        assertThat(result).isEmpty();
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
