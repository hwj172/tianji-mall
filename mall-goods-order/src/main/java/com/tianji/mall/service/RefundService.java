package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.RefundRequest;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Refund;
import com.tianji.mall.entity.RefundItem;
import com.tianji.mall.feign.PayFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.RefundItemMapper;
import com.tianji.mall.mapper.RefundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RefundService extends ServiceImpl<RefundMapper, Refund> {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundItemMapper refundItemMapper;
    private final OrderService orderService;
    private final PayFeignClient payFeignClient;

    public RefundService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                         RefundItemMapper refundItemMapper, OrderService orderService,
                         PayFeignClient payFeignClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundItemMapper = refundItemMapper;
        this.orderService = orderService;
        this.payFeignClient = payFeignClient;
    }

    /**
     * 申请退款（按商品），支持仅退款和退货退款。
     */
    @Transactional
    public Refund requestRefund(Long userId, Long orderId, RefundRequest req) {
        // SELECT ... FOR UPDATE 对订单行加排他锁：并发申请退款时串行化，
        // 后到的事务在该行锁上等待，前一个事务提交后再执行防重检查，可看到已插入的退款记录，从而阻止双插入
        Order order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        // 仅已付款、已发货、已完成订单可申请退款
        if (order.getStatus() < 2 || order.getStatus() > 4) {
            throw new BizException(BizErrorCode.REFUND_ORDER_STATUS_INVALID);
        }

        // 检查是否已有进行中的退款
        long count = count(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .ne(Refund::getStatus, "fail"));
        if (count > 0) {
            throw new BizException(BizErrorCode.REFUND_DUPLICATE);
        }

        // 获取订单所有明细
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        // 计算退款金额
        BigDecimal totalRefund = BigDecimal.ZERO;
        List<RefundItem> refundItems = new ArrayList<>();

        for (RefundRequest.RefundItemRequest itemReq : req.getItems()) {
            OrderItem orderItem = allItems.stream()
                    .filter(i -> i.getId().equals(itemReq.getOrderItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BizException(BizErrorCode.REFUND_ITEM_NOT_FOUND, itemReq.getOrderItemId().toString()));

            int qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : orderItem.getQuantity();
            if (qty <= 0 || qty > orderItem.getQuantity()) {
                throw new BizException(BizErrorCode.REFUND_QUANTITY_INVALID, orderItem.getProductName());
            }

            BigDecimal itemAmount = orderItem.getPrice().multiply(BigDecimal.valueOf(qty));
            totalRefund = totalRefund.add(itemAmount);

            RefundItem ri = new RefundItem();
            ri.setOrderItemId(orderItem.getId());
            ri.setProductId(orderItem.getProductId());
            ri.setSkuId(orderItem.getSkuId());
            ri.setQuantity(qty);
            ri.setAmount(itemAmount);
            refundItems.add(ri);
        }

        if (totalRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(BizErrorCode.REFUND_AMOUNT_INVALID);
        }

        String refundType = req.getRefundType() != null ? req.getRefundType() : "REFUND_ONLY";

        // 创建退款记录
        Refund refund = new Refund();
        refund.setOrderId(orderId);
        refund.setUserId(userId);
        refund.setAmount(totalRefund);
        refund.setReason(req.getReason());
        refund.setStatus("processing");
        refund.setRefundType(refundType);
        save(refund);

        // 保存退款商品明细
        for (RefundItem ri : refundItems) {
            ri.setRefundId(refund.getId());
            refundItemMapper.insert(ri);
        }

        // 仅退款：直接调用 pay-service
        if ("REFUND_ONLY".equals(refundType)) {
            executeRefund(refund);
        }
        // 退货退款：等待卖家确认收货后再退款

        log.info("用户 {} 申请退款: orderId={}, refundId={}, type={}, amount={}",
                userId, orderId, refund.getId(), refundType, totalRefund);
        return refund;
    }

    /**
     * 买家退货填快递单号（仅退货退款类型）。
     */
    @Transactional
    public void returnShip(Long userId, Long refundId, String trackingNumber, String trackingCompany) {
        Refund refund = getById(refundId);
        if (refund == null || !refund.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.REFUND_NOT_FOUND);
        }
        if (!"RETURN_REFUND".equals(refund.getRefundType())) {
            throw new BizException(BizErrorCode.REFUND_NOT_RETURN_TYPE);
        }
        if (!"processing".equals(refund.getStatus())) {
            throw new BizException(BizErrorCode.REFUND_PROCESSING_INVALID);
        }
        refund.setTrackingNumber(trackingNumber);
        refund.setTrackingCompany(trackingCompany);
        refund.setReturnStatus("SHIPPED");
        updateById(refund);
        log.info("用户 {} 退货寄回: refundId={}, tracking={}", userId, refundId, trackingNumber);
    }

    /**
     * 卖家确认收到退货，执行退款。
     */
    @Transactional
    public void confirmReceive(Long refundId) {
        Refund refund = getById(refundId);
        if (refund == null) {
            throw new BizException(BizErrorCode.REFUND_NOT_FOUND);
        }
        if (!"RETURN_REFUND".equals(refund.getRefundType())) {
            throw new BizException(BizErrorCode.REFUND_NOT_RETURN_RECEIVE);
        }
        if (!"SHIPPED".equals(refund.getReturnStatus())) {
            throw new BizException(BizErrorCode.REFUND_NOT_SHIPPED_BACK);
        }
        refund.setReturnStatus("RECEIVED");
        updateById(refund);
        executeRefund(refund);
        log.info("卖家确认收货，执行退款: refundId={}", refundId);
    }

    /**
     * 退款详情（含商品明细）。
     */
    public Map<String, Object> getRefundDetail(Long refundId) {
        Refund refund = getById(refundId);
        if (refund == null) {
            throw new BizException(BizErrorCode.REFUND_NOT_FOUND);
        }
        List<RefundItem> items = refundItemMapper.selectByRefundId(refundId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refund", refund);
        result.put("items", items);
        return result;
    }

    /**
     * 我的退款列表（分页）。
     */
    public Page<Refund> getMyRefunds(Long userId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Refund>()
                        .eq(Refund::getUserId, userId)
                        .orderByDesc(Refund::getCreatedAt));
    }

    /**
     * 根据订单 ID 查询退款记录。
     */
    public Refund getRefundByOrderId(Long orderId) {
        return getOne(new LambdaQueryWrapper<Refund>().eq(Refund::getOrderId, orderId));
    }

    // ============ private ============

    private void executeRefund(Refund refund) {
        try {
            payFeignClient.refundOrder(refund.getOrderId(), refund.getUserId(),
                    refund.getAmount(), refund.getReason());
            refund.setStatus("success");
            updateById(refund);
        } catch (Exception e) {
            log.error("调用退款失败: refundId={}", refund.getId(), e);
            refund.setStatus("fail");
            refund.setFailReason("支付服务调用失败");
            updateById(refund);
        }
    }
}
