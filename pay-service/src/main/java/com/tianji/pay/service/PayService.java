package com.tianji.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.pay.dto.OrderDTO;
import com.tianji.pay.dto.PayResponse;
import com.tianji.pay.entity.Payment;
import com.tianji.pay.feign.OrderFeignClient;
import com.tianji.pay.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService extends ServiceImpl<PaymentMapper, Payment> {

    private final AlipayClient alipayClient;
    private final OrderFeignClient orderFeignClient;
    private final PaymentMapper paymentMapper;

    @Value("${alipay.notify-url:}")
    private String notifyUrl;

    @Value("${alipay.alipay-public-key:}")
    private String alipayPublicKey;

    public PayResponse createPayment(Long userId, Long orderId) {
        return createPayment(userId, orderId, null);
    }

    public PayResponse createPayment(Long userId, Long orderId, String returnUrl) {
        // 1. 通过 Feign 获取订单信息（事务外）
        R<OrderDTO> orderResult = orderFeignClient.getOrder(orderId);
        if (orderResult == null || orderResult.getCode() != 200 || orderResult.getData() == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        OrderDTO order = orderResult.getData();

        if (!java.util.Objects.equals(userId, order.getUserId())) {
            throw new BizException(BizErrorCode.ORDER_NOT_OWNER);
        }
        if (order.getStatus() != 1) {
            throw new BizException(BizErrorCode.ORDER_STATUS_INVALID);
        }

        // 2. 写支付记录（独立事务）
        String paymentNo = savePaymentRecord(userId, orderId, order);

        // 3. 调用支付宝生成支付页面（事务外，防止连接泄露）
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        // 同步跳转：支付成功后浏览器自动跳回前端（可选，前端传 origin + 目标路由）
        if (returnUrl != null && !returnUrl.isBlank()) {
            request.setReturnUrl(returnUrl);
        }
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + paymentNo + "\"," +
                "\"total_amount\":" + order.getTotalAmount() + "," +
                "\"subject\":\"天机商城订单-" + order.getOrderNo() + "\"," +
                "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"" +
                "}");
        try {
            String payForm = alipayClient.pageExecute(request).getBody();
            return new PayResponse(payForm, paymentNo);
        } catch (AlipayApiException e) {
            log.error("支付宝支付创建失败", e);
            throw new BizException(BizErrorCode.PAYMENT_CREATE_FAILED);
        }
    }

    @Transactional
    private String savePaymentRecord(Long userId, Long orderId, OrderDTO order) {
        Payment existing = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (existing != null && existing.getStatus() == 2) {
            throw new BizException(BizErrorCode.PAYMENT_DUPLICATE);
        }
        if (existing != null) {
            return existing.getPaymentNo();
        }
        String paymentNo = "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(1);
        save(payment);
        return paymentNo;
    }

    public void handleNotify(Map<String, String> params) {
        // 1. 验签
        try {
            boolean verified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!verified) {
                log.error("支付宝回调验签失败");
                throw new BizException(BizErrorCode.SIGN_VERIFY_FAILED);
            }
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            throw new BizException(BizErrorCode.SIGN_VERIFY_ERROR);
        }

        String paymentNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");

        // 2. 更新支付记录（原子 SQL，独立事务）
        Payment payment = doMarkPaid(paymentNo, tradeNo, tradeStatus);
        if (payment == null) {
            return; // 非成功状态或重复处理
        }

        // 3. 通知订单服务（事务外，避免 Feign 占用 DB 连接）
        orderFeignClient.payOrder(payment.getOrderId(), payment.getUserId());
        log.info("支付成功: paymentNo={}, tradeNo={}", paymentNo, tradeNo);
    }

    @Transactional
    private Payment doMarkPaid(String paymentNo, String tradeNo, String tradeStatus) {
        Payment payment = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            log.error("支付记录不存在: {}", paymentNo);
            throw new BizException(BizErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return null;
        }
        int rows = paymentMapper.markPaid(paymentNo, tradeNo);
        if (rows == 0) {
            log.info("支付回调重复处理: paymentNo={}", paymentNo);
            return null;
        }
        return payment;
    }

    public Payment queryPayment(Long userId, Long orderId) {
        Payment payment = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (payment == null || !payment.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    public void refund(Long orderId, Long userId, BigDecimal refundAmount, String refundReason) {
        Payment payment = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (payment == null || !payment.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payment.getStatus() != 2) {
            throw new BizException(BizErrorCode.PAYMENT_NOT_PAID);
        }

        String outRequestNo = "REFUND" + payment.getPaymentNo() + System.currentTimeMillis();
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + payment.getPaymentNo() + "\"," +
                "\"refund_amount\":" + refundAmount + "," +
                "\"out_request_no\":\"" + outRequestNo + "\"," +
                "\"refund_reason\":\"" + (refundReason != null ? refundReason : "") + "\"" +
                "}");
        try {
            com.alipay.api.response.AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                log.info("退款成功: paymentNo={}, refundNo={}", payment.getPaymentNo(), outRequestNo);
            } else {
                log.error("退款失败: paymentNo={}, msg={}", payment.getPaymentNo(), response.getSubMsg());
                throw new BizException(BizErrorCode.REFUND_FAILED, response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("退款调用异常: orderId={}", orderId, e);
            throw new BizException(BizErrorCode.REFUND_FAILED);
        }
    }
}
