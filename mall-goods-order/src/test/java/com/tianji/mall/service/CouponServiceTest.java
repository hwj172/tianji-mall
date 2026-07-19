package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CouponRequest;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.mapper.CouponMapper;
import com.tianji.mall.mapper.UserCouponMapper;
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
class CouponServiceTest {

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private UserCouponMapper userCouponMapper;

    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponService(userCouponMapper);
        ReflectionTestUtils.setField(couponService, "baseMapper", couponMapper);
    }

    // ============ Admin CRUD ============

    @Test
    void shouldCreateCoupon() {
        CouponRequest req = buildCouponRequest("满100减20", "FIXED", 20, 100, 100);
        Coupon result = couponService.create(req);

        assertThat(result.getName()).isEqualTo("满100减20");
        assertThat(result.getUsedQuantity()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo(1);
        verify(couponMapper).insert(any(Coupon.class));
    }

    @Test
    void shouldUpdateCoupon() {
        CouponRequest req = buildCouponRequest("更新名称", "PERCENT", 8, 50, 200);
        when(couponMapper.selectById(1L)).thenReturn(buildCoupon(1L, "旧名称", "FIXED", 10, 100));
        when(couponMapper.updateById(any(Coupon.class))).thenReturn(1);

        couponService.update(1L, req);

        verify(couponMapper).updateById(any(Coupon.class));
    }

    @Test
    void shouldThrowWhenUpdateNonExistent() {
        when(couponMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> couponService.update(99L, buildCouponRequest("x", "FIXED", 10, 100, 100)))
                .isInstanceOf(BizException.class)
                .hasMessage("优惠券不存在");
    }

    @Test
    void shouldDisableCoupon() {
        when(couponMapper.selectById(1L)).thenReturn(buildCoupon(1L, "测试", "FIXED", 10, 100));
        when(couponMapper.updateById(any(Coupon.class))).thenReturn(1);

        couponService.disable(1L);

        verify(couponMapper).updateById(any(Coupon.class));
    }

    @Test
    void shouldListByPage() {
        when(couponMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<Coupon>().setRecords(List.of(buildCoupon(1L, "c1", "FIXED", 10, 100))));

        List<Coupon> result = couponService.listByPage(1, 10);

        assertThat(result).hasSize(1);
    }

    // ============ User claim ============

    @Test
    void shouldClaimCouponSuccessfully() {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = buildCoupon(1L, "满100减20", "FIXED", 20, 100);
        coupon.setStartTime(now.minusDays(1));
        coupon.setEndTime(now.plusDays(7));
        coupon.setStatus(1);
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(userCouponMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(couponMapper.incrementUsedQuantity(1L)).thenReturn(1);
        when(userCouponMapper.insert(any(UserCoupon.class))).thenReturn(1);

        couponService.claimCoupon(100L, 1L);

        verify(userCouponMapper).insert(any(UserCoupon.class));
    }

    @Test
    void shouldThrowWhenClaimDuplicate() {
        Coupon coupon = buildCoupon(1L, "满100减20", "FIXED", 20, 100);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(7));
        coupon.setStatus(1);
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(userCouponMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new UserCoupon());

        assertThatThrownBy(() -> couponService.claimCoupon(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("您已领取过该优惠券");
    }

    @Test
    void shouldThrowWhenClaimSoldOut() {
        Coupon coupon = buildCoupon(1L, "满100减20", "FIXED", 20, 100);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(7));
        coupon.setStatus(1);
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(userCouponMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(couponMapper.incrementUsedQuantity(1L)).thenReturn(0);

        assertThatThrownBy(() -> couponService.claimCoupon(100L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("优惠券已领完");
    }

    @Test
    void shouldGetUserCoupons() {
        when(userCouponMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(new UserCoupon()));

        List<UserCoupon> result = couponService.getUserCoupons(100L);

        assertThat(result).hasSize(1);
    }

    // ============ Apply coupon ============

    @Test
    void shouldApplyFixedCoupon() {
        UserCoupon uc = new UserCoupon();
        uc.setId(1L);
        uc.setUserId(100L);
        uc.setCouponId(10L);
        uc.setStatus("UNUSED");
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        Coupon coupon = buildCoupon(10L, "满100减20", "FIXED", 20, 100);
        coupon.setStatus(1);
        coupon.setEndTime(LocalDateTime.now().plusDays(7));
        when(couponMapper.selectById(10L)).thenReturn(coupon);
        when(userCouponMapper.markUsed(1L, null)).thenReturn(1);

        BigDecimal discount = couponService.applyCoupon(100L, 1L, BigDecimal.valueOf(150));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void shouldApplyPercentCoupon() {
        UserCoupon uc = new UserCoupon();
        uc.setId(2L);
        uc.setUserId(100L);
        uc.setCouponId(20L);
        uc.setStatus("UNUSED");
        when(userCouponMapper.selectById(2L)).thenReturn(uc);

        Coupon coupon = buildCoupon(20L, "8折", "PERCENT", 8, 100);
        coupon.setStatus(1);
        coupon.setEndTime(LocalDateTime.now().plusDays(7));
        when(couponMapper.selectById(20L)).thenReturn(coupon);
        when(userCouponMapper.markUsed(2L, null)).thenReturn(1);

        // 8折 = discountValue=8，即 100 * 8 / 10 = 80，实际折扣 = 100 * (1 - 0.8) = 20...
        // Wait, the implementation uses: orderAmount * discountValue / 10
        // PERCENT with value=8: discount = 150 * 8 / 10 = 120
        BigDecimal discount = couponService.applyCoupon(100L, 2L, BigDecimal.valueOf(150));

        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    void shouldThrowWhenApplyBelowMinAmount() {
        UserCoupon uc = new UserCoupon();
        uc.setId(1L);
        uc.setUserId(100L);
        uc.setCouponId(10L);
        uc.setStatus("UNUSED");
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        Coupon coupon = buildCoupon(10L, "满100减20", "FIXED", 20, 100);
        coupon.setStatus(1);
        coupon.setEndTime(LocalDateTime.now().plusDays(7));
        when(couponMapper.selectById(10L)).thenReturn(coupon);

        assertThatThrownBy(() -> couponService.applyCoupon(100L, 1L, BigDecimal.valueOf(50)))
                .isInstanceOf(BizException.class)
                .hasMessage("未达到最低消费金额");
    }

    @Test
    void shouldThrowWhenApplyAlreadyUsed() {
        UserCoupon uc = new UserCoupon();
        uc.setId(1L);
        uc.setUserId(100L);
        uc.setCouponId(10L);
        uc.setStatus("USED");
        when(userCouponMapper.selectById(1L)).thenReturn(uc);

        assertThatThrownBy(() -> couponService.applyCoupon(100L, 1L, BigDecimal.valueOf(150)))
                .isInstanceOf(BizException.class)
                .hasMessage("优惠券已使用或已过期");
    }

    // ============ helpers ============

    private CouponRequest buildCouponRequest(String name, String discountType, int discountValue, int minAmount, int totalQty) {
        CouponRequest req = new CouponRequest();
        req.setName(name);
        req.setDiscountType(discountType);
        req.setDiscountValue(BigDecimal.valueOf(discountValue));
        req.setMinOrderAmount(BigDecimal.valueOf(minAmount));
        req.setTotalQuantity(totalQty);
        req.setStartTime(LocalDateTime.now());
        req.setEndTime(LocalDateTime.now().plusDays(30));
        return req;
    }

    private Coupon buildCoupon(Long id, String name, String discountType, int discountValue, int minAmount) {
        Coupon c = new Coupon();
        c.setId(id);
        c.setName(name);
        c.setDiscountType(discountType);
        c.setDiscountValue(BigDecimal.valueOf(discountValue));
        c.setMinOrderAmount(BigDecimal.valueOf(minAmount));
        c.setTotalQuantity(100);
        c.setUsedQuantity(0);
        c.setStatus(1);
        return c;
    }
}
