package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.mapper.CouponMapper;
import com.tianji.mall.mapper.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CouponServiceIntegrationTest {

    @Autowired
    private CouponService couponService;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    @MockBean
    private com.tianji.mall.feign.AiChatFeignClient aiChatFeignClient;

    @MockBean
    private com.tianji.mall.feign.PayFeignClient payFeignClient;

    @MockBean
    private com.tianji.mall.feign.UserFeignClient userFeignClient;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private ShopService shopService;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private UserCouponMapper userCouponMapper;

    private final Long userId = 1L;
    private Long couponId;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        userCouponMapper.delete(new LambdaQueryWrapper<>());
        couponMapper.delete(new LambdaQueryWrapper<>());
        now = LocalDateTime.now();
        couponId = insertCoupon("满100减10", "FIXED", BigDecimal.valueOf(10),
                BigDecimal.valueOf(100), 10, 0, 1,
                now.minusDays(1), now.plusDays(7));
    }

    // ==================== claimCoupon ====================

    @Test
    void shouldClaimCouponSuccessfully() {
        couponService.claimCoupon(userId, couponId);

        // user_coupon 记录已创建
        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId));
        assertThat(userCoupons).hasSize(1);
        assertThat(userCoupons.get(0).getCouponId()).isEqualTo(couponId);
        assertThat(userCoupons.get(0).getStatus()).isEqualTo("UNUSED");

        // 库存已扣减
        Coupon coupon = couponMapper.selectById(couponId);
        assertThat(coupon.getUsedQuantity()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenCouponNotFound() {
        assertThatThrownBy(() -> couponService.claimCoupon(userId, 99999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("优惠券");

        Long disabledId = insertCoupon("已停用", "FIXED", BigDecimal.valueOf(10),
                BigDecimal.valueOf(100), 10, 0, 0,
                now.minusDays(1), now.plusDays(7));
        assertThatThrownBy(() -> couponService.claimCoupon(userId, disabledId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("优惠券");
    }

    @Test
    void shouldThrowWhenDuplicateClaim() {
        couponService.claimCoupon(userId, couponId);

        assertThatThrownBy(() -> couponService.claimCoupon(userId, couponId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已领取");
    }

    @Test
    void shouldThrowWhenCouponExhausted() {
        // 创建库存为 1 的优惠券（totalQuantity=1），先被领完
        Long limitedId = insertCoupon("限量优惠券", "FIXED", BigDecimal.valueOf(5),
                BigDecimal.valueOf(50), 1, 1, 1, // usedQuantity=1，等于 totalQuantity
                now.minusDays(1), now.plusDays(7));
        // 直接更新 used_quantity 让库存耗尽
        Coupon c = couponMapper.selectById(limitedId);
        c.setUsedQuantity(1);
        couponMapper.updateById(c);

        assertThatThrownBy(() -> couponService.claimCoupon(userId, limitedId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已领完");
    }

    @Test
    void shouldThrowWhenCouponExpired() {
        Long expiredId = insertCoupon("已过期", "FIXED", BigDecimal.valueOf(10),
                BigDecimal.valueOf(100), 10, 0, 1,
                now.minusDays(10), now.minusDays(1)); // 已过期

        assertThatThrownBy(() -> couponService.claimCoupon(userId, expiredId))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在");
    }

    // ==================== getCouponCenter ====================

    @Test
    void shouldGetCouponCenterWithClaimedStatus() {
        // 先领取 couponId
        couponService.claimCoupon(userId, couponId);

        // 再创建一个未领取的优惠券
        insertCoupon("全场8折", "PERCENT", BigDecimal.valueOf(8),
                BigDecimal.valueOf(0), 10, 0, 1,
                now.minusDays(1), now.plusDays(7));

        Map<String, Object> center = couponService.getCouponCenter(userId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> coupons = (List<Map<String, Object>>) center.get("coupons");
        assertThat(coupons).hasSize(2);
        assertThat(center.get("total")).isEqualTo(2);
        assertThat(center.get("unclaimedCount")).isEqualTo(1L);

        // 第一个是已领取的
        Map<String, Object> claimed = coupons.stream()
                .filter(c -> (boolean) c.get("claimed")).findFirst().orElseThrow();
        assertThat(claimed.get("name")).isEqualTo("满100减10");

        // 第二个是未领取的
        Map<String, Object> unclaimed = coupons.stream()
                .filter(c -> !(boolean) c.get("claimed")).findFirst().orElseThrow();
        assertThat(unclaimed.get("name")).isEqualTo("全场8折");
    }

    // ==================== getUserCoupons ====================

    @Test
    void shouldGetUserCouponsWithDetails() {
        couponService.claimCoupon(userId, couponId);

        List<Map<String, Object>> result = couponService.getUserCoupons(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKeys("userCouponId", "status", "name",
                "discountType", "discountValue", "minOrderAmount", "endTime");
        assertThat(result.get(0).get("name")).isEqualTo("满100减10");
        assertThat(result.get(0).get("discountType")).isEqualTo("FIXED");
        assertThat((BigDecimal) result.get(0).get("discountValue")).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void shouldGetEmptyCouponsForNewUser() {
        List<Map<String, Object>> result = couponService.getUserCoupons(99999L);
        assertThat(result).isEmpty();
    }

    // ==================== applyCoupon ====================

    @Test
    void shouldApplyFixedCouponSuccessfully() {
        couponService.claimCoupon(userId, couponId);
        Long userCouponId = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId)).get(0).getId();

        BigDecimal discount = couponService.applyCoupon(userId, userCouponId, BigDecimal.valueOf(200));

        // 固定减 10
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(10));

        // 优惠券状态变为 USED
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo("USED");
    }

    @Test
    void shouldApplyPercentCouponSuccessfully() {
        Long percentId = insertCoupon("全场8折", "PERCENT", BigDecimal.valueOf(8),
                BigDecimal.valueOf(0), 10, 0, 1,
                now.minusDays(1), now.plusDays(7));
        couponService.claimCoupon(userId, percentId);
        Long userCouponId = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId)).get(0).getId();

        // 8 折=8，即 discount = amount * 8 / 10 = 200 * 0.8 = 160... wait
        // Actually: discountValue=8 means 8折, discount = orderAmount * 8 / 10 = 200 * 0.8 = 160
        // But that's the discounted AMOUNT, not the DISCOUNT. Let me re-read...
        // In CouponService.applyCoupon:
        // discount = orderAmount.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(10), ...)
        // So discount = 200 * 8 / 10 = 160. That's WRONG for a discount.
        // Actually wait: "全场8折" means 80% of original. So discount should be 20% off = 200 * 0.2 = 40.
        // But the code does: orderAmount * discountValue / 10 = 200 * 8 / 10 = 160.
        // 160 is the discount amount? That seems like 80% OFF, not 20% OFF.
        // Hmm, wait. Let me re-read:
        // discount = orderAmount.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(10), 2, HALF_UP);
        // If discountValue=8 and orderAmount=200: discount = 200 * 8 / 10 = 160.
        // "8折" in Chinese means 80% of the original price. So the discount AMOUNT should be 200 - 160 = 40.
        // But the code treats discountValue as the discount AMOUNT directly: 160?
        // Actually, looking at it: "全场8折" is the coupon NAME. The discountValue=8 for PERCENT type means 8/10 of the order amount.
        // So 200 * 8 / 10 = 160. This IS the discount amount that gets subtracted from the order.
        // Wait, that doesn't make sense either. 8折 coupon gives 160 discount? That would make the order = 200 - 160 = 40. That's 2折, not 8折.
        //
        // I think the naming is confusing. Let me just test what the code does:
        // For PERCENT: discount = orderAmount * discountValue / 10
        // If I test with 200 order amount and discountValue=8, the result is 160.
        // That's the actual behavior. Let me just verify that.
        BigDecimal discount = couponService.applyCoupon(userId, userCouponId, BigDecimal.valueOf(200));

        // discountValue=8（>1 时归一化为 0.8 = 8折）：discount = 200 × (1 - 0.8) = 40
        assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(40));

        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo("USED");
    }

    @Test
    void shouldThrowWhenMinOrderAmountNotMet() {
        // 最低消费 100，但订单只有 50
        couponService.claimCoupon(userId, couponId);
        Long userCouponId = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId)).get(0).getId();

        assertThatThrownBy(() -> couponService.applyCoupon(userId, userCouponId, BigDecimal.valueOf(50)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最低消费");

        // 优惠券未被使用
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo("UNUSED");
    }

    // ==================== restoreCoupon ====================

    @Test
    void shouldRestoreCouponAfterOrderCancellation() {
        couponService.claimCoupon(userId, couponId);
        Long userCouponId = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId)).get(0).getId();

        // 使用优惠券
        couponService.applyCoupon(userId, userCouponId, BigDecimal.valueOf(200));
        // 绑定订单
        couponService.bindOrderId(userCouponId, 100L);

        // 取消订单恢复优惠券
        couponService.restoreCoupon(100L);

        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        assertThat(uc.getStatus()).isEqualTo("UNUSED");
    }

    // ==================== helpers ====================

    private Long insertCoupon(String name, String discountType, BigDecimal discountValue,
                               BigDecimal minOrderAmount, int totalQuantity, int usedQuantity,
                               int status, LocalDateTime startTime, LocalDateTime endTime) {
        Coupon c = new Coupon();
        c.setName(name);
        c.setDiscountType(discountType);
        c.setDiscountValue(discountValue);
        c.setMinOrderAmount(minOrderAmount);
        c.setTotalQuantity(totalQuantity);
        c.setUsedQuantity(usedQuantity);
        c.setStatus(status);
        c.setStartTime(startTime);
        c.setEndTime(endTime);
        couponMapper.insert(c);
        return c.getId();
    }
}
