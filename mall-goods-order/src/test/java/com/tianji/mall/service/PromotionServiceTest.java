package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.PromotionRequest;
import com.tianji.mall.entity.Promotion;
import com.tianji.mall.mapper.PromotionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionMapper promotionMapper;

    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService();
        ReflectionTestUtils.setField(promotionService, "baseMapper", promotionMapper);
    }

    // ============ Admin CRUD ============

    @Test
    void shouldCreatePromotion() {
        PromotionRequest req = buildRequest("满300减50", 300, 50);

        Promotion result = promotionService.create(req);

        assertThat(result.getName()).isEqualTo("满300减50");
        assertThat(result.getThreshold()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(result.getStatus()).isEqualTo(1);
        verify(promotionMapper).insert(any(Promotion.class));
    }

    @Test
    void shouldUpdatePromotion() {
        PromotionRequest req = buildRequest("满200减30", 200, 30);
        Promotion existing = buildPromotion(1L, "满300减50", 300, 50);
        existing.setStatus(1);
        when(promotionMapper.selectById(1L)).thenReturn(existing);
        when(promotionMapper.updateById(any(Promotion.class))).thenReturn(1);

        promotionService.update(1L, req);

        verify(promotionMapper).updateById(any(Promotion.class));
    }

    @Test
    void shouldThrowWhenUpdateNonExistent() {
        when(promotionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> promotionService.update(99L, buildRequest("x", 100, 10)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("满减活动不存在");
    }

    @Test
    void shouldDisablePromotion() {
        Promotion existing = buildPromotion(1L, "满300减50", 300, 50);
        existing.setStatus(1);
        when(promotionMapper.selectById(1L)).thenReturn(existing);
        when(promotionMapper.updateById(any(Promotion.class))).thenReturn(1);

        promotionService.disable(1L);

        verify(promotionMapper).updateById(any(Promotion.class));
    }

    @Test
    void shouldThrowWhenDisableNonExistent() {
        when(promotionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> promotionService.disable(99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("满减活动不存在");
    }

    @Test
    void shouldListByPage() {
        when(promotionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Promotion>().setRecords(List.of(buildPromotion(1L, "满300减50", 300, 50))));

        List<Promotion> result = promotionService.listByPage(1, 10);

        assertThat(result).hasSize(1);
    }

    // ============ Calculate discount ============

    @Test
    void shouldReturnZeroWhenNoActivePromotion() {
        when(promotionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        BigDecimal discount = promotionService.calculateDiscount(BigDecimal.valueOf(500));

        assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldApplyWhenReachesThreshold() {
        when(promotionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildActivePromotion(1L, "满300减50", 300, 50)));

        BigDecimal discount = promotionService.calculateDiscount(BigDecimal.valueOf(350));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void shouldReturnZeroWhenBelowThreshold() {
        when(promotionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildActivePromotion(1L, "满300减50", 300, 50)));

        BigDecimal discount = promotionService.calculateDiscount(BigDecimal.valueOf(100));

        assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldPickLargestDiscountAmongMultiple() {
        when(promotionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        buildActivePromotion(1L, "满100减10", 100, 10),
                        buildActivePromotion(2L, "满300减50", 300, 50)));

        // 金额 350：两个活动都满足，取减免最大者 50
        BigDecimal discount = promotionService.calculateDiscount(BigDecimal.valueOf(350));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void shouldCapDiscountAtOrderAmount() {
        when(promotionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(buildActivePromotion(1L, "满100减200", 100, 200)));

        BigDecimal discount = promotionService.calculateDiscount(BigDecimal.valueOf(150));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(150));
    }

    @Test
    void shouldReturnZeroForNullOrNonPositiveAmount() {
        assertThat(promotionService.calculateDiscount(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(promotionService.calculateDiscount(BigDecimal.ZERO)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(promotionService.calculateDiscount(BigDecimal.valueOf(-10))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ============ helpers ============

    private PromotionRequest buildRequest(String name, int threshold, int discount) {
        PromotionRequest req = new PromotionRequest();
        req.setName(name);
        req.setThreshold(BigDecimal.valueOf(threshold));
        req.setDiscount(BigDecimal.valueOf(discount));
        req.setStartTime(LocalDateTime.now());
        req.setEndTime(LocalDateTime.now().plusDays(30));
        return req;
    }

    private Promotion buildPromotion(Long id, String name, int threshold, int discount) {
        Promotion p = new Promotion();
        p.setId(id);
        p.setName(name);
        p.setThreshold(BigDecimal.valueOf(threshold));
        p.setDiscount(BigDecimal.valueOf(discount));
        p.setStartTime(LocalDateTime.now().minusDays(1));
        p.setEndTime(LocalDateTime.now().plusDays(30));
        p.setStatus(1);
        return p;
    }

    /** 当前时间窗内生效的活动 */
    private Promotion buildActivePromotion(Long id, String name, int threshold, int discount) {
        return buildPromotion(id, name, threshold, discount);
    }
}
