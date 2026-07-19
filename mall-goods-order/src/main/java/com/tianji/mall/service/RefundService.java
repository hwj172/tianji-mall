package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService extends ServiceImpl<RefundMapper, Refund> {

    private final OrderService orderService;
    private final PayFeignClient payFeignClient;

    @Transactional
    public Refund requestRefund(Long userId, Long orderId, String reason) {
        Order order = orderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 2) {
            throw new BizException("仅已付款订单可申请退款");
        }

        // 检查是否已有进行中的退款
        long count = count(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .ne(Refund::getStatus, "fail"));
        if (count > 0) {
            throw new BizException("退款申请已提交");
        }

        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(order.getTotalAmount());
        refund.setReason(reason);
        refund.setStatus("processing");
        save(refund);

        // best-effort: 调用 pay-service 发起支付宝退款
        try {
            payFeignClient.refundOrder(orderId, userId, order.getTotalAmount(), reason);
        } catch (Exception e) {
            log.error("调用退款失败: orderId={}", orderId, e);
        }

        return refund;
    }

    public Refund getRefundByOrderId(Long orderId) {
        return getOne(new LambdaQueryWrapper<Refund>().eq(Refund::getOrderId, orderId));
    }
}
