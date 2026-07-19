package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.RefundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundMapper refundMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private PayFeignClient payFeignClient;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(orderService, payFeignClient);
        ReflectionTestUtils.setField(refundService, "baseMapper", refundMapper);
    }

    @Test
    void shouldRequestRefund() {
        Order order = buildOrder(1L, 100L, 2); // PAID
        when(orderService.getById(1L)).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundMapper.insert(any(Refund.class))).thenReturn(1);

        Refund refund = refundService.requestRefund(100L, 1L, "不想要了");

        assertThat(refund.getOrderId()).isEqualTo(1L);
        assertThat(refund.getUserId()).isEqualTo(100L);
        assertThat(refund.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        assertThat(refund.getReason()).isEqualTo("不想要了");
        assertThat(refund.getStatus()).isEqualTo("processing");
        verify(refundMapper).insert(any(Refund.class));
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderService.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> refundService.requestRefund(100L, 999L, "reason"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderNotBelongsToUser() {
        Order order = buildOrder(1L, 999L, 2);
        when(orderService.getById(1L)).thenReturn(order);

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, "reason"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderNotPaid() {
        Order order = buildOrder(1L, 100L, 1); // PENDING
        when(orderService.getById(1L)).thenReturn(order);

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, "reason"))
                .isInstanceOf(BizException.class)
                .hasMessage("仅已付款订单可申请退款");
    }

    @Test
    void shouldThrowWhenRefundAlreadyExists() {
        Order order = buildOrder(1L, 100L, 2);
        when(orderService.getById(1L)).thenReturn(order);
        when(refundMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> refundService.requestRefund(100L, 1L, "reason"))
                .isInstanceOf(BizException.class)
                .hasMessage("退款申请已提交");
    }

    private Order buildOrder(Long id, Long userId, int status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("20260101000000" + String.format("%06d", id));
        order.setUserId(userId);
        order.setTotalAmount(BigDecimal.valueOf(2000));
        order.setStatus(status);
        order.setAddressId(1L);
        return order;
    }
}
