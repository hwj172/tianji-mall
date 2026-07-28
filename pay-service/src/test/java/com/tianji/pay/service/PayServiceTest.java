package com.tianji.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.pay.dto.OrderDTO;
import com.tianji.pay.entity.Payment;
import com.tianji.pay.feign.OrderFeignClient;
import com.tianji.pay.mapper.PaymentMapper;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayServiceTest {

    @Mock
    private AlipayClient alipayClient;

    @Mock
    private OrderFeignClient orderFeignClient;

    @Mock
    private PaymentMapper paymentMapper;

    private PayService payService;

    @BeforeEach
    void setUp() throws AlipayApiException {
        payService = new PayService(alipayClient, orderFeignClient, paymentMapper);
        ReflectionTestUtils.setField(payService, "baseMapper", paymentMapper);
        ReflectionTestUtils.setField(payService, "notifyUrl", "https://example.com/notify");
        ReflectionTestUtils.setField(payService, "alipayPublicKey", "test-public-key");

        // lenient: not all tests call Alipay
        AlipayTradePagePayResponse alipayResponse = new AlipayTradePagePayResponse();
        alipayResponse.setBody("<form>alipay form</form>");
        lenient().when(alipayClient.pageExecute(any(AlipayTradePagePayRequest.class))).thenReturn(alipayResponse);
    }

    // ==================== createPayment ====================

    @Test
    void shouldCreatePayment() {
        OrderDTO order = buildOrderDTO(1L, 10L, BigDecimal.valueOf(6999), 1);
        when(orderFeignClient.getOrder(10L)).thenReturn(R.ok(order));
        // savePaymentRecord: getOne checks existing, then save
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(paymentMapper.insert(any(Payment.class))).thenReturn(1);

        com.tianji.pay.dto.PayResponse result = payService.createPayment(1L, 10L);

        assertThat(result.getPaymentNo()).startsWith("PAY");
        assertThat(result.getPayForm()).isEqualTo("<form>alipay form</form>");
        verify(paymentMapper).insert(any(Payment.class));
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderFeignClient.getOrder(99L)).thenReturn(null);

        assertThatThrownBy(() -> payService.createPayment(1L, 99L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不存在");
    }

    @Test
    void shouldThrowWhenOrderNotBelongToUser() {
        OrderDTO order = buildOrderDTO(2L, 10L, BigDecimal.valueOf(6999), 1);
        when(orderFeignClient.getOrder(10L)).thenReturn(R.ok(order));

        assertThatThrownBy(() -> payService.createPayment(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单不属于当前用户");
    }

    @Test
    void shouldThrowWhenOrderStatusInvalid() {
        OrderDTO order = buildOrderDTO(1L, 10L, BigDecimal.valueOf(6999), 2); // PAID
        when(orderFeignClient.getOrder(10L)).thenReturn(R.ok(order));

        assertThatThrownBy(() -> payService.createPayment(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单状态不允许支付");
    }

    @Test
    void shouldReturnExistingPaymentNoWhenRepay() {
        OrderDTO order = buildOrderDTO(1L, 10L, BigDecimal.valueOf(6999), 1);
        when(orderFeignClient.getOrder(10L)).thenReturn(R.ok(order));

        Payment existing = new Payment();
        existing.setId(1L);
        existing.setPaymentNo("PAY202407160001");
        existing.setOrderId(10L);
        existing.setUserId(1L);
        existing.setAmount(BigDecimal.valueOf(6999));
        existing.setStatus(1);
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing);

        com.tianji.pay.dto.PayResponse result = payService.createPayment(1L, 10L);

        assertThat(result.getPaymentNo()).isEqualTo("PAY202407160001");
    }

    @Test
    void shouldThrowWhenPaidOrderTriesToRepay() {
        OrderDTO order = buildOrderDTO(1L, 10L, BigDecimal.valueOf(6999), 1);
        when(orderFeignClient.getOrder(10L)).thenReturn(R.ok(order));

        Payment existing = new Payment();
        existing.setId(1L);
        existing.setPaymentNo("PAY202407160001");
        existing.setOrderId(10L);
        existing.setStatus(2); // PAID
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing);

        assertThatThrownBy(() -> payService.createPayment(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("订单已支付");
    }

    // ==================== queryPayment ====================

    @Test
    void shouldQueryPayment() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentNo("PAY202407160001");
        payment.setOrderId(10L);
        payment.setUserId(1L);
        payment.setAmount(BigDecimal.valueOf(6999));
        payment.setStatus(1);
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

        Payment result = payService.queryPayment(1L, 10L);

        assertThat(result.getPaymentNo()).isEqualTo("PAY202407160001");
        assertThat(result.getOrderId()).isEqualTo(10L);
    }

    @Test
    void shouldThrowWhenPaymentNotFound() {
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> payService.queryPayment(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("支付记录不存在");
    }

    @Test
    void shouldThrowWhenPaymentNotBelongToUser() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setUserId(2L);
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

        assertThatThrownBy(() -> payService.queryPayment(1L, 10L))
                .isInstanceOf(BizException.class)
                .hasMessage("支付记录不存在");
    }

    // ==================== helpers ====================

    private OrderDTO buildOrderDTO(Long userId, Long orderId, BigDecimal amount, int status) {
        OrderDTO dto = new OrderDTO();
        dto.setUserId(userId);
        dto.setId(orderId);
        dto.setTotalAmount(amount);
        dto.setStatus(status);
        dto.setOrderNo("202407160001");
        return dto;
    }
}
