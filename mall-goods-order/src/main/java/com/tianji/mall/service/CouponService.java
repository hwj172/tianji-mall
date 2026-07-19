package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.CouponRequest;
import com.tianji.mall.entity.Coupon;
import com.tianji.mall.entity.UserCoupon;
import com.tianji.mall.mapper.CouponMapper;
import com.tianji.mall.mapper.UserCouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CouponService extends ServiceImpl<CouponMapper, Coupon> {

    private final UserCouponMapper userCouponMapper;

    public CouponService(UserCouponMapper userCouponMapper) {
        this.userCouponMapper = userCouponMapper;
    }

    // ============ Admin CRUD ============

    public Coupon create(CouponRequest req) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(req, coupon);
        coupon.setUsedQuantity(0);
        coupon.setStatus(1);
        save(coupon);
        log.info("管理员创建优惠券: {}", coupon.getName());
        return coupon;
    }

    public void update(Long id, CouponRequest req) {
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizException("优惠券不存在");
        }
        BeanUtils.copyProperties(req, coupon);
        updateById(coupon);
        log.info("管理员更新优惠券: {}", id);
    }

    public void disable(Long id) {
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BizException("优惠券不存在");
        }
        coupon.setStatus(0);
        updateById(coupon);
        log.info("管理员停用优惠券: {}", id);
    }

    public List<Coupon> listByPage(int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Coupon>().orderByDesc(Coupon::getCreateTime))
                .getRecords();
    }

    // ============ User-facing ============

    /** 用户可领取的优惠券列表 */
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return list(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .lt(Coupon::getStartTime, now)
                .gt(Coupon::getEndTime, now)
                .apply("used_quantity < total_quantity"));
    }

    @Transactional
    public void claimCoupon(Long userId, Long couponId) {
        Coupon coupon = getById(couponId);
        if (coupon == null || coupon.getStatus() != 1) {
            throw new BizException("优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BizException("不在优惠券有效期内");
        }

        // 检查重复领取
        UserCoupon existing = userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (existing != null) {
            throw new BizException("您已领取过该优惠券");
        }

        // 原子扣减库存
        int rows = baseMapper.incrementUsedQuantity(couponId);
        if (rows == 0) {
            throw new BizException("优惠券已领完");
        }

        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus("UNUSED");
        userCouponMapper.insert(uc);
        log.info("用户 {} 领取优惠券 {} ({})", userId, couponId, coupon.getName());
    }

    /** 我的优惠券 */
    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getCreateTime));
    }

    // ============ Order integration ============

    /**
     * 下单时使用优惠券，返回折扣金额。
     * 原子操作：标记 UserCoupon 为 USED，防并发重复使用。
     */
    @Transactional
    public BigDecimal applyCoupon(Long userId, Long userCouponId, BigDecimal orderAmount) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BizException("优惠券不存在");
        }
        if (!"UNUSED".equals(uc.getStatus())) {
            throw new BizException("优惠券已使用或已过期");
        }

        Coupon coupon = getById(uc.getCouponId());
        if (coupon == null || coupon.getStatus() != 1) {
            throw new BizException("优惠券不存在或已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(coupon.getEndTime())) {
            throw new BizException("优惠券已过期");
        }

        // 检查最低消费
        if (orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new BizException("未达到最低消费金额");
        }

        // 计算折扣
        BigDecimal discount;
        if ("FIXED".equals(coupon.getDiscountType())) {
            discount = coupon.getDiscountValue();
        } else {
            discount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_UP);
        }
        // 折扣不能超过订单金额
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        // 原子标记已使用（不传 orderId 因为订单还没创建）
        userCouponMapper.markUsed(userCouponId, null);
        log.info("订单使用优惠券: userCouponId={}, discount={}", userCouponId, discount);
        return discount;
    }

    /** 下订单后绑定订单ID */
    public void bindOrderId(Long userCouponId, Long orderId) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc != null) {
            uc.setUsedOrderId(orderId);
            userCouponMapper.updateById(uc);
        }
    }

    /** 订单取消时恢复优惠券（Phase 2d 使用） */
    @Transactional
    public void restoreCoupon(Long orderId) {
        List<UserCoupon> list = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUsedOrderId, orderId));
        for (UserCoupon uc : list) {
            userCouponMapper.restoreUnused(uc.getId());
            log.info("订单 {} 取消，恢复优惠券: {}", orderId, uc.getId());
        }
    }
}
