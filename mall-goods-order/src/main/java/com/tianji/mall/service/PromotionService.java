package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.mall.dto.PromotionRequest;
import com.tianji.mall.entity.Promotion;
import com.tianji.mall.mapper.PromotionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台满减活动（admin=平台级配置，不涉及 seller）。
 * 结算时自动应用：达到 threshold 门槛自动减免，多个有效活动取减免最大者。
 */
@Slf4j
@Service
public class PromotionService extends ServiceImpl<PromotionMapper, Promotion> {

    // ============ Admin CRUD ============

    public Promotion create(PromotionRequest req) {
        Promotion promotion = new Promotion();
        BeanUtils.copyProperties(req, promotion);
        promotion.setStatus(1);
        save(promotion);
        log.info("管理员创建满减活动: {} (满{}减{})", promotion.getName(), promotion.getThreshold(), promotion.getDiscount());
        return promotion;
    }

    public void update(Long id, PromotionRequest req) {
        Promotion promotion = getById(id);
        if (promotion == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "满减活动不存在");
        }
        BeanUtils.copyProperties(req, promotion);
        updateById(promotion);
        log.info("管理员更新满减活动: {}", id);
    }

    public void disable(Long id) {
        Promotion promotion = getById(id);
        if (promotion == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "满减活动不存在");
        }
        promotion.setStatus(0);
        updateById(promotion);
        log.info("管理员停用满减活动: {}", id);
    }

    public List<Promotion> listByPage(int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Promotion>().orderByDesc(Promotion::getCreateTime))
                .getRecords();
    }

    // ============ User-facing ============

    /** 当前生效的满减活动（启用 + 时间窗内），按减免金额降序 */
    public List<Promotion> getCurrentActive() {
        LocalDateTime now = LocalDateTime.now();
        return list(new LambdaQueryWrapper<Promotion>()
                .eq(Promotion::getStatus, 1)
                .lt(Promotion::getStartTime, now)
                .gt(Promotion::getEndTime, now)
                .orderByDesc(Promotion::getDiscount));
    }

    /**
     * 按商品总额计算满减折扣金额（优惠券前金额判断门槛）。
     * 多个有效活动取减免最大者；折扣不超过订单金额。
     */
    public BigDecimal calculateDiscount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal best = BigDecimal.ZERO;
        for (Promotion p : getCurrentActive()) {
            if (totalAmount.compareTo(p.getThreshold()) >= 0) {
                BigDecimal d = p.getDiscount().min(totalAmount);
                if (d.compareTo(best) > 0) {
                    best = d;
                }
            }
        }
        return best;
    }
}
