package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.dto.MemberInfoResponse;
import com.tianji.mall.dto.SignInResponse;
import com.tianji.mall.entity.PointsLog;
import com.tianji.mall.entity.SignIn;
import com.tianji.mall.entity.UserMember;
import com.tianji.mall.mapper.PointsLogMapper;
import com.tianji.mall.mapper.SignInMapper;
import com.tianji.mall.mapper.UserMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserMemberMapper userMemberMapper;
    private final PointsLogMapper pointsLogMapper;
    private final SignInMapper signInMapper;

    /** 等级积分阈值（按累计积分，升序） */
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 500, 2000, 5000};
    private static final String[] LEVEL_NAMES = {"青铜", "白银", "黄金", "铂金", "钻石"};
    private static final int SIGN_IN_POINTS = 5;

    public MemberInfoResponse getMemberInfo(Long userId) {
        UserMember member = userMemberMapper.selectOne(
                new LambdaQueryWrapper<UserMember>().eq(UserMember::getUserId, userId));
        boolean todaySigned = signInMapper.selectCount(
                new LambdaQueryWrapper<SignIn>()
                        .eq(SignIn::getUserId, userId)
                        .eq(SignIn::getSignDate, LocalDate.now())) > 0;
        if (member == null) {
            return new MemberInfoResponse(1, LEVEL_NAMES[0], 0, 0, 2, LEVEL_NAMES[1], 0, todaySigned);
        }
        return buildInfo(member, todaySigned);
    }

    @Transactional
    public SignInResponse signIn(Long userId) {
        LocalDate today = LocalDate.now();
        boolean already = signInMapper.selectCount(
                new LambdaQueryWrapper<SignIn>()
                        .eq(SignIn::getUserId, userId)
                        .eq(SignIn::getSignDate, today)) > 0;
        if (already) {
            return new SignInResponse(false, 0, true);
        }
        try {
            SignIn signIn = new SignIn();
            signIn.setUserId(userId);
            signIn.setSignDate(today);
            signIn.setCreateTime(LocalDateTime.now());
            signInMapper.insert(signIn);
        } catch (Exception e) {
            // 并发重复签到：uk_user_date 唯一索引兜底，视为已签到
            log.warn("签到记录插入冲突，视为已签到: userId={}", userId, e);
            return new SignInResponse(false, 0, true);
        }
        addPoints(userId, SIGN_IN_POINTS, "sign_in", "每日签到");
        return new SignInResponse(true, SIGN_IN_POINTS, true);
    }

    public Page<PointsLog> getPointsLog(Long userId, int page, int size) {
        return pointsLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getUserId, userId)
                        .orderByDesc(PointsLog::getCreateTime));
    }

    /**
     * 订单支付成功后发放积分：1 元 = 1 积分，按订单实付金额向下取整。
     * 使用 REQUIRES_NEW 独立事务，发放失败不影响支付主流程（调用方 best-effort catch）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addPointsForOrder(Long userId, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.intValue() <= 0) {
            return;
        }
        addPoints(userId, orderAmount.intValue(), "order_paid", "订单支付奖励");
    }

    // ==================== 私有方法 ====================

    private MemberInfoResponse buildInfo(UserMember member, boolean todaySigned) {
        int totalPoints = member.getTotalPoints() == null ? 0 : member.getTotalPoints();
        int level = calcLevel(totalPoints);
        boolean maxed = level >= LEVEL_THRESHOLDS.length;
        int progress;
        if (maxed) {
            progress = 100;
        } else {
            int curThreshold = LEVEL_THRESHOLDS[level - 1];
            int nextThreshold = LEVEL_THRESHOLDS[level];
            progress = (int) Math.floor((totalPoints - curThreshold) * 100.0 / (nextThreshold - curThreshold));
            progress = Math.max(0, Math.min(100, progress));
        }
        return new MemberInfoResponse(
                level,
                LEVEL_NAMES[level - 1],
                member.getPoints() == null ? 0 : member.getPoints(),
                totalPoints,
                maxed ? null : level + 1,
                maxed ? null : LEVEL_NAMES[level],
                progress,
                todaySigned);
    }

    /** 按累计积分计算等级（1-青铜 … 5-钻石，超过 5000 封顶钻石） */
    private int calcLevel(int totalPoints) {
        int level = 1;
        for (int i = 1; i < LEVEL_THRESHOLDS.length; i++) {
            if (totalPoints >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    private void addPoints(Long userId, int points, String changeType, String remark) {
        UserMember member = userMemberMapper.selectOne(
                new LambdaQueryWrapper<UserMember>().eq(UserMember::getUserId, userId));
        if (member == null) {
            member = new UserMember();
            member.setUserId(userId);
            member.setLevel(1);
            member.setPoints(0);
            member.setTotalPoints(0);
            member.setCreateTime(LocalDateTime.now());
            member.setUpdateTime(LocalDateTime.now());
            userMemberMapper.insert(member);
        }
        member.setPoints((member.getPoints() == null ? 0 : member.getPoints()) + points);
        member.setTotalPoints((member.getTotalPoints() == null ? 0 : member.getTotalPoints()) + points);
        member.setLevel(calcLevel(member.getTotalPoints()));
        member.setUpdateTime(LocalDateTime.now());
        userMemberMapper.updateById(member);

        PointsLog logEntry = new PointsLog();
        logEntry.setUserId(userId);
        logEntry.setChangeType(changeType);
        logEntry.setPoints(points);
        logEntry.setRemark(remark);
        logEntry.setCreateTime(LocalDateTime.now());
        pointsLogMapper.insert(logEntry);
        log.info("积分发放: userId={}, changeType={}, points={}", userId, changeType, points);
    }
}
