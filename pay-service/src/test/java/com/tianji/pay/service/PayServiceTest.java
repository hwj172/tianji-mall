package com.tianji.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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

    // ==================== handleNotify ====================

    @Test
    void shouldHandleNotifySuccessfully() {
        try (MockedStatic<AlipaySignature> mockedSignature = mockStatic(AlipaySignature.class)) {
            mockedSignature.when(() -> AlipaySignature.rsaCheckV1(anyMap(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 1);
            when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);
            when(paymentMapper.markPaid("PAY202407160001", "20240716220010001")).thenReturn(1);
            when(orderFeignClient.payOrder(10L, 1L)).thenReturn(R.ok());

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "PAY202407160001");
            params.put("trade_no", "20240716220010001");
            params.put("trade_status", "TRADE_SUCCESS");

            payService.handleNotify(params);

            verify(orderFeignClient).payOrder(10L, 1L);
        }
    }

    @Test
    void shouldThrowWhenPaymentNotFoundInHandleNotify() {
        try (MockedStatic<AlipaySignature> mockedSignature = mockStatic(AlipaySignature.class)) {
            mockedSignature.when(() -> AlipaySignature.rsaCheckV1(anyMap(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "PAY_NOT_EXISTS");
            params.put("trade_no", "20240716220010001");
            params.put("trade_status", "TRADE_SUCCESS");

            assertThatThrownBy(() -> payService.handleNotify(params))
                    .isInstanceOf(BizException.class)
                    .hasMessage("支付记录不存在");
        }
    }

    @Test
    void shouldReturnWhenTradeStatusNotSuccessInHandleNotify() {
        try (MockedStatic<AlipaySignature> mockedSignature = mockStatic(AlipaySignature.class)) {
            mockedSignature.when(() -> AlipaySignature.rsaCheckV1(anyMap(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 1);
            when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

            // trade_status 非成功状态，doMarkPaid 返回 null
            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "PAY202407160001");
            params.put("trade_no", "20240716220010001");
            params.put("trade_status", "WAIT_BUYER_PAY");

            payService.handleNotify(params);

            // 未标记支付成功，不应通知订单服务
            verify(orderFeignClient, never()).payOrder(anyLong(), anyLong());
        }
    }

    @Test
    void shouldRetryNotifyOrderServiceWhenDuplicateMarkPaid() {
        try (MockedStatic<AlipaySignature> mockedSignature = mockStatic(AlipaySignature.class)) {
            mockedSignature.when(() -> AlipaySignature.rsaCheckV1(anyMap(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 1);
            when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);
            // markPaid 返回 0 表示重复回调（上次通知订单服务可能失败丢失）→ 幂等重试通知
            when(paymentMapper.markPaid("PAY202407160001", "20240716220010001")).thenReturn(0);
            when(orderFeignClient.payOrder(10L, 1L)).thenReturn(R.ok());

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "PAY202407160001");
            params.put("trade_no", "20240716220010001");
            params.put("trade_status", "TRADE_SUCCESS");

            payService.handleNotify(params);

            // 重复回调仍会通知订单服务（订单服务 payOrder 幂等），避免丢通知
            verify(orderFeignClient).payOrder(10L, 1L);
        }
    }

    @Test
    void shouldThrowWhenOrderServiceRejectsPay() {
        try (MockedStatic<AlipaySignature> mockedSignature = mockStatic(AlipaySignature.class)) {
            mockedSignature.when(() -> AlipaySignature.rsaCheckV1(anyMap(), anyString(), anyString(), anyString()))
                    .thenReturn(true);

            Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 1);
            when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);
            when(paymentMapper.markPaid("PAY202407160001", "20240716220010001")).thenReturn(1);
            // 订单服务返回业务失败（如订单已取消）→ 抛系统异常触发支付宝重试
            when(orderFeignClient.payOrder(10L, 1L)).thenReturn(R.fail("订单状态无效"));

            Map<String, String> params = new HashMap<>();
            params.put("out_trade_no", "PAY202407160001");
            params.put("trade_no", "20240716220010001");
            params.put("trade_status", "TRADE_SUCCESS");

            assertThatThrownBy(() -> payService.handleNotify(params))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ==================== refund ====================

    @Test
    void shouldRefundSuccessfully() throws AlipayApiException {
        Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 2); // status=2 已付款
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

        AlipayTradeRefundResponse refundResponse = new AlipayTradeRefundResponse();
        refundResponse.setCode("10000");
        refundResponse.setMsg("Success");
        doReturn(refundResponse).when(alipayClient).execute(any(AlipayTradeRefundRequest.class));

        payService.refund(10L, 1L, BigDecimal.valueOf(100), "不想要了");

        verify(alipayClient).execute(any(AlipayTradeRefundRequest.class));
    }

    @Test
    void shouldThrowWhenRefundPaymentNotFound() {
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> payService.refund(10L, 1L, BigDecimal.valueOf(100), "退款"))
                .isInstanceOf(BizException.class)
                .hasMessage("支付记录不存在");
    }

    @Test
    void shouldThrowWhenRefundPaymentNotPaid() {
        Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 1); // status=1 未付款
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

        assertThatThrownBy(() -> payService.refund(10L, 1L, BigDecimal.valueOf(100), "退款"))
                .isInstanceOf(BizException.class)
                .hasMessage("订单未支付，无法退款");
    }

    @Test
    void shouldThrowWhenAlipayRefundFails() throws AlipayApiException {
        Payment payment = buildPayment(1L, "PAY202407160001", 10L, 1L, 2);
        when(paymentMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(payment);

        // 模拟支付宝 API 调用异常（退款失败）
        doThrow(new AlipayApiException("商家余额不足")).when(alipayClient)
                .execute(any(AlipayTradeRefundRequest.class));

        assertThatThrownBy(() -> payService.refund(10L, 1L, BigDecimal.valueOf(100), "退款"))
                .isInstanceOf(BizException.class)
                .hasMessage("退款失败");
    }

    // ==================== helpers ====================

    private Payment buildPayment(Long id, String paymentNo, Long orderId, Long userId, int status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(BigDecimal.valueOf(100));
        payment.setStatus(status);
        return payment;
    }

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
