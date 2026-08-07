package com.tianji.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.mall.dto.MemberInfoResponse;
import com.tianji.mall.dto.SignInResponse;
import com.tianji.mall.entity.PointsLog;
import com.tianji.mall.entity.SignIn;
import com.tianji.mall.entity.UserMember;
import com.tianji.mall.mapper.PointsLogMapper;
import com.tianji.mall.mapper.SignInMapper;
import com.tianji.mall.mapper.UserMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserMemberMapper userMemberMapper;
    @Mock
    private PointsLogMapper pointsLogMapper;
    @Mock
    private SignInMapper signInMapper;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(userMemberMapper, pointsLogMapper, signInMapper);
    }

    // ==================== getMemberInfo ====================

    @Test
    void shouldReturnDefaultInfoWhenNoMember() {
        when(userMemberMapper.selectOne(any())).thenReturn(null);

        MemberInfoResponse info = memberService.getMemberInfo(1L);

        assertThat(info.getLevel()).isEqualTo(1);
        assertThat(info.getLevelName()).isEqualTo("青铜");
        assertThat(info.getPoints()).isEqualTo(0);
        assertThat(info.getTotalPoints()).isEqualTo(0);
        assertThat(info.getNextLevel()).isEqualTo(2);
        assertThat(info.getProgress()).isEqualTo(0);
    }

    @Test
    void shouldReturnLevelOneForZeroPoints() {
        when(userMemberMapper.selectOne(any())).thenReturn(buildMember(1L, 0, 0));

        MemberInfoResponse info = memberService.getMemberInfo(1L);

        assertThat(info.getLevel()).isEqualTo(1);
        assertThat(info.getLevelName()).isEqualTo("青铜");
        assertThat(info.getNextLevel()).isEqualTo(2);
        assertThat(info.getNextLevelName()).isEqualTo("白银");
        assertThat(info.getProgress()).isEqualTo(0);
    }

    @Test
    void shouldComputeProgressBetweenThresholds() {
        // 300 分：介于白银(100)与黄金(500)之间 → 进度 50%
        when(userMemberMapper.selectOne(any())).thenReturn(buildMember(1L, 300, 300));

        MemberInfoResponse info = memberService.getMemberInfo(1L);

        assertThat(info.getLevel()).isEqualTo(2);
        assertThat(info.getLevelName()).isEqualTo("白银");
        assertThat(info.getProgress()).isEqualTo(50);
        assertThat(info.getNextLevel()).isEqualTo(3);
    }

    @Test
    void shouldReturnPlatinumAtThreshold() {
        when(userMemberMapper.selectOne(any())).thenReturn(buildMember(1L, 2000, 2000));

        MemberInfoResponse info = memberService.getMemberInfo(1L);

        assertThat(info.getLevel()).isEqualTo(4);
        assertThat(info.getLevelName()).isEqualTo("铂金");
        assertThat(info.getProgress()).isEqualTo(0);
    }

    @Test
    void shouldReturnMaxLevelWithNoNext() {
        when(userMemberMapper.selectOne(any())).thenReturn(buildMember(1L, 6000, 6000));

        MemberInfoResponse info = memberService.getMemberInfo(1L);

        assertThat(info.getLevel()).isEqualTo(5);
        assertThat(info.getLevelName()).isEqualTo("钻石");
        assertThat(info.getNextLevel()).isNull();
        assertThat(info.getProgress()).isEqualTo(100);
    }

    // ==================== signIn ====================

    @Test
    void shouldReturnAlreadySignedWhenSignedToday() {
        when(signInMapper.selectCount(any())).thenReturn(1L);

        SignInResponse res = memberService.signIn(1L);

        assertThat(res.getSigned()).isFalse();
        assertThat(res.getTodaySigned()).isTrue();
        assertThat(res.getPoints()).isEqualTo(0);
        verify(signInMapper, never()).insert(any(SignIn.class));
        verify(pointsLogMapper, never()).insert(any(PointsLog.class));
    }

    @Test
    void shouldSignInAndAwardPoints() {
        when(signInMapper.selectCount(any())).thenReturn(0L);
        UserMember member = buildMember(1L, 10, 10);
        when(userMemberMapper.selectOne(any())).thenReturn(member);
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        when(userMemberMapper.updateById(any(UserMember.class))).thenReturn(1);
        when(pointsLogMapper.insert(any(PointsLog.class))).thenReturn(1);

        SignInResponse res = memberService.signIn(1L);

        assertThat(res.getSigned()).isTrue();
        assertThat(res.getTodaySigned()).isTrue();
        assertThat(res.getPoints()).isEqualTo(5);
        assertThat(member.getPoints()).isEqualTo(15);
        assertThat(member.getTotalPoints()).isEqualTo(15);

        ArgumentCaptor<PointsLog> captor = ArgumentCaptor.forClass(PointsLog.class);
        verify(pointsLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo("sign_in");
        assertThat(captor.getValue().getPoints()).isEqualTo(5);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldCreateMemberOnFirstSignIn() {
        when(signInMapper.selectCount(any())).thenReturn(0L);
        when(userMemberMapper.selectOne(any())).thenReturn(null);
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        when(userMemberMapper.insert(any(UserMember.class))).thenReturn(1);
        when(userMemberMapper.updateById(any(UserMember.class))).thenReturn(1);
        when(pointsLogMapper.insert(any(PointsLog.class))).thenReturn(1);

        SignInResponse res = memberService.signIn(1L);

        assertThat(res.getSigned()).isTrue();
        assertThat(res.getPoints()).isEqualTo(5);

        // 首次签到创建会员行
        verify(userMemberMapper).insert(any(UserMember.class));
        // 发放 5 积分并落到会员行（updateById 参数为发放后状态）
        ArgumentCaptor<UserMember> updCaptor = ArgumentCaptor.forClass(UserMember.class);
        verify(userMemberMapper).updateById(updCaptor.capture());
        assertThat(updCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(updCaptor.getValue().getPoints()).isEqualTo(5);
        assertThat(updCaptor.getValue().getTotalPoints()).isEqualTo(5);
    }

    @Test
    void shouldReturnAlreadySignedWhenInsertConflicts() {
        when(signInMapper.selectCount(any())).thenReturn(0L);
        when(signInMapper.insert(any(SignIn.class)))
                .thenThrow(new DuplicateKeyException("uk_user_date 唯一索引冲突"));

        SignInResponse res = memberService.signIn(1L);

        assertThat(res.getSigned()).isFalse();
        assertThat(res.getTodaySigned()).isTrue();
        assertThat(res.getPoints()).isEqualTo(0);
    }

    // ==================== getPointsLog ====================

    @Test
    void shouldReturnPagedPointsLog() {
        when(pointsLogMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        Page<PointsLog> result = memberService.getPointsLog(1L, 1, 20);

        assertThat(result).isNotNull();
        verify(pointsLogMapper).selectPage(any(), any());
    }

    // ==================== addPointsForOrder ====================

    @Test
    void shouldSkipWhenAmountNullOrZero() {
        memberService.addPointsForOrder(1L, null);
        memberService.addPointsForOrder(1L, BigDecimal.ZERO);

        verify(userMemberMapper, never()).insert(any(UserMember.class));
        verify(userMemberMapper, never()).updateById(any(UserMember.class));
        verify(pointsLogMapper, never()).insert(any(PointsLog.class));
    }

    @Test
    void shouldAwardPointsByOrderAmount() {
        UserMember member = buildMember(1L, 0, 0);
        when(userMemberMapper.selectOne(any())).thenReturn(member);
        when(userMemberMapper.updateById(any(UserMember.class))).thenReturn(1);
        when(pointsLogMapper.insert(any(PointsLog.class))).thenReturn(1);

        memberService.addPointsForOrder(1L, BigDecimal.valueOf(123.45));

        assertThat(member.getPoints()).isEqualTo(123);
        assertThat(member.getTotalPoints()).isEqualTo(123);
        assertThat(member.getLevel()).isEqualTo(2); // 123 >= 100 → 白银

        ArgumentCaptor<PointsLog> captor = ArgumentCaptor.forClass(PointsLog.class);
        verify(pointsLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo("order_paid");
        assertThat(captor.getValue().getPoints()).isEqualTo(123);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void shouldCreateMemberWhenAwardingOrderPoints() {
        when(userMemberMapper.selectOne(any())).thenReturn(null);
        when(userMemberMapper.insert(any(UserMember.class))).thenReturn(1);
        when(userMemberMapper.updateById(any(UserMember.class))).thenReturn(1);
        when(pointsLogMapper.insert(any(PointsLog.class))).thenReturn(1);

        memberService.addPointsForOrder(1L, BigDecimal.valueOf(100));

        // 首次发放订单积分创建会员行
        verify(userMemberMapper).insert(any(UserMember.class));
        // 按订单金额发放 100 积分（updateById 参数为发放后状态）
        ArgumentCaptor<UserMember> updCaptor = ArgumentCaptor.forClass(UserMember.class);
        verify(userMemberMapper).updateById(updCaptor.capture());
        assertThat(updCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(updCaptor.getValue().getPoints()).isEqualTo(100);
        assertThat(updCaptor.getValue().getTotalPoints()).isEqualTo(100);
        assertThat(updCaptor.getValue().getLevel()).isEqualTo(2); // 100 分 → 白银
    }

    private UserMember buildMember(Long userId, int points, int totalPoints) {
        UserMember member = new UserMember();
        member.setId(1L);
        member.setUserId(userId);
        member.setLevel(1);
        member.setPoints(points);
        member.setTotalPoints(totalPoints);
        return member;
    }
}
