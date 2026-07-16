package com.tianji.pay.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

    @Transactional
    public PayResponse createPayment(Long userId, Long orderId) {
        // 1. 通过 Feign 获取订单信息
        R<OrderDTO> orderResult = orderFeignClient.getOrder(orderId);
        if (orderResult == null || orderResult.getCode() != 200 || orderResult.getData() == null) {
            throw new BizException("订单不存在");
        }
        OrderDTO order = orderResult.getData();

        if (!userId.equals(order.getUserId())) {
            throw new BizException("订单不属于当前用户");
        }
        if (order.getStatus() != 1) {
            throw new BizException("订单状态不允许支付");
        }
        String orderNo = order.getOrderNo();
        BigDecimal totalAmount = order.getTotalAmount();

        // 2. 检查是否已有支付记录
        Payment existing = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (existing != null && existing.getStatus() == 2) {
            throw new BizException("订单已支付");
        }
        // 已有待支付记录则复用
        String paymentNo;
        if (existing != null) {
            paymentNo = existing.getPaymentNo();
        } else {
            paymentNo = "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

            Payment payment = new Payment();
            payment.setPaymentNo(paymentNo);
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setAmount(totalAmount);
            payment.setStatus(1);
            save(payment);
        }

        // 3. 调用支付宝生成支付页面
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + paymentNo + "\"," +
                "\"total_amount\":" + totalAmount + "," +
                "\"subject\":\"天机商城订单-" + orderNo + "\"," +
                "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"" +
                "}");
        try {
            String payForm = alipayClient.pageExecute(request).getBody();
            return new PayResponse(payForm, paymentNo);
        } catch (AlipayApiException e) {
            log.error("支付宝支付创建失败", e);
            throw new BizException("支付创建失败，请稍后重试");
        }
    }

    @Transactional
    public void handleNotify(Map<String, String> params) {
        // 1. 验签
        try {
            boolean verified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!verified) {
                log.error("支付宝回调验签失败");
                throw new BizException("验签失败");
            }
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            throw new BizException("验签异常");
        }

        String paymentNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String tradeStatus = params.get("trade_status");

        // 2. 更新支付记录
        Payment payment = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) {
            log.error("支付记录不存在: {}", paymentNo);
            throw new BizException("支付记录不存在");
        }

        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            int rows = paymentMapper.markPaid(paymentNo, tradeNo);
            if (rows == 0) {
                // 已处理过，幂等返回
                log.info("支付回调重复处理: paymentNo={}", paymentNo);
                return;
            }
            // 通知订单服务更新订单状态
            orderFeignClient.payOrder(payment.getOrderId(), payment.getUserId());
            log.info("支付成功: paymentNo={}, tradeNo={}", paymentNo, tradeNo);
        }
    }

    public Payment queryPayment(Long userId, Long orderId) {
        Payment payment = getOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (payment == null || !payment.getUserId().equals(userId)) {
            throw new BizException("支付记录不存在");
        }
        return payment;
    }
}
