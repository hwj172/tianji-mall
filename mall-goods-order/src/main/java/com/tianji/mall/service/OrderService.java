package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.dto.OrderItemResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private final OrderItemMapper orderItemMapper;
    private final CartService cartService;
    private final ProductService productService;
    private final ProductSkuService skuService;
    private final AddressService addressService;
    private final CouponService couponService;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final SeckillService seckillService;
    private final ProductMapper productMapper;

    @Transactional
    public Order createOrder(Long userId, OrderCreateRequest req) {
        // 1. 校验地址
        Address address = addressService.getById(req.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException("收货地址不存在");
        }

        // 2. 查询选中的购物车项
        List<CartItem> cartItems = cartService.listByIds(req.getCartItemIds());
        if (cartItems.isEmpty()) {
            throw new BizException("购物车项不存在");
        }
        for (CartItem item : cartItems) {
            if (!item.getUserId().equals(userId)) {
                throw new BizException("购物车项不属于当前用户");
            }
            if (item.getChecked() != 1) {
                throw new BizException("请先选中商品");
            }
        }

        // 3. 获取商品 ID 列表
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .distinct()
                .toList();

        // 3.5 获取分布式锁（按 productId 和 skuId 排序，避免死锁）
        List<String> lockKeyStrings = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            if (cartItem.getSkuId() != null) {
                lockKeyStrings.add("lock:sku:" + cartItem.getSkuId());
            } else {
                lockKeyStrings.add("lock:product:" + cartItem.getProductId());
            }
        }
        List<String> sortedLockKeys = lockKeyStrings.stream().sorted().distinct().toList();
        RLock[] lockArray = sortedLockKeys.stream()
                .map(key -> redissonClient.getLock(key))
                .toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(lockArray);

        try {
            if (!multiLock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BizException("系统繁忙，请稍后重试");
            }

            // 4. 锁内重新读取库存（拿到锁后库存可能已变化）
            Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            // 5. 校验库存并计算金额
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                Product product = productMap.get(cartItem.getProductId());
                if (product == null || product.getStatus() == 0) {
                    throw new BizException("商品「" + (product != null ? product.getName() : "未知") + "」已下架");
                }

                BigDecimal itemPrice;
                String skuSpecs = null;

                if (cartItem.getSkuId() != null) {
                    // SKU 商品：用 SKU 价格和库存
                    ProductSku sku = skuService.getById(cartItem.getSkuId());
                    if (sku == null || !sku.getProductId().equals(cartItem.getProductId())) {
                        throw new BizException("商品「" + product.getName() + "」的规格已失效");
                    }
                    if (sku.getStock() < cartItem.getQuantity()) {
                        throw new BizException("商品「" + product.getName() + "」库存不足");
                    }
                    itemPrice = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
                    skuSpecs = sku.getSpecs();
                } else {
                    // 无 SKU：判秒杀窗口
                    if (seckillService.isSeckillActive(product)) {
                        if (product.getSeckillStock() < cartItem.getQuantity()) {
                            throw new BizException("秒杀商品「" + product.getName() + "」库存不足");
                        }
                        itemPrice = product.getSeckillPrice();
                    } else {
                        if (product.getStock() < cartItem.getQuantity()) {
                            throw new BizException("商品「" + product.getName() + "」库存不足");
                        }
                        itemPrice = product.getPrice();
                    }
                }

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setSkuId(cartItem.getSkuId());
                orderItem.setSkuSpecs(skuSpecs);
                orderItems.add(orderItem);

                totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            }

            // 6. 优惠券折扣（锁内）
            BigDecimal discount = BigDecimal.ZERO;
            if (req.getCouponId() != null) {
                discount = couponService.applyCoupon(userId, req.getCouponId(), totalAmount);
            }

            // 7. 生成订单号
            String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

            // 8. 创建订单
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(totalAmount.subtract(discount));
            order.setStatus(1); // 待付款
            order.setAddressId(req.getAddressId());
            save(order);

            // 绑定优惠券到订单ID
            if (req.getCouponId() != null) {
                couponService.bindOrderId(req.getCouponId(), order.getId());
            }

            // 9. 写入订单明细
            for (OrderItem item : orderItems) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 10. 原子扣库存
            for (OrderItem item : orderItems) {
                if (item.getSkuId() != null) {
                    skuService.deductStock(item.getProductId(), item.getSkuId(), item.getQuantity());
                } else {
                    Product product = productMap.get(item.getProductId());
                    if (product != null && seckillService.isSeckillActive(product)) {
                        int affected = productMapper.deductSeckillStock(item.getProductId(), item.getQuantity());
                        if (affected == 0) {
                            throw new BizException("秒杀商品「" + product.getName() + "」库存不足");
                        }
                    } else {
                        productService.deductStock(item.getProductId(), item.getQuantity());
                    }
                }
            }

            // 11. 清购物车
            cartService.removeByIds(req.getCartItemIds());

            // 12. 发送订单创建事件
            publishOrderEvent(order, "CREATED");

            // 13. 发送 30 分钟超时延迟消息（best-effort）
            try {
                org.springframework.messaging.Message<String> timeoutMsg =
                        org.springframework.messaging.support.MessageBuilder
                                .withPayload(order.getId().toString())
                                .setHeader("DELAY", "16")
                                .build();
                rocketMQTemplate.syncSend("order-topic:TIMEOUT_CHECK", timeoutMsg, 3000);
            } catch (Exception e) {
                log.error("发送超时延迟消息失败: orderId={}", order.getId(), e);
            }

            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("系统繁忙，请稍后重试");
        } finally {
            multiLock.unlock();
        }
    }

    public List<Order> getOrderList(Long userId) {
        return list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
    }

    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getPrice(), i.getQuantity(),
                        i.getSkuId(), i.getSkuSpecs()))
                .toList();

        Address address = addressService.getById(order.getAddressId());

        return new OrderDetailResponse(order, itemResponses, address);
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BizException("仅待付款订单可取消");
        }

        order.setStatus(5); // 已取消
        updateById(order);

        // 原子恢复库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                skuService.restoreStock(item.getProductId(), item.getSkuId(), item.getQuantity());
            } else {
                Product product = productMapper.selectById(item.getProductId());
                if (product != null && product.getSeckillPrice() != null && product.getSeckillStock() != null) {
                    productMapper.restoreSeckillStock(item.getProductId(), item.getQuantity());
                } else {
                    productService.restoreStock(item.getProductId(), item.getQuantity());
                }
            }
        }

        // 恢复优惠券（best-effort）
        try {
            couponService.restoreCoupon(orderId);
        } catch (Exception e) {
            log.error("恢复优惠券失败: orderId={}", orderId, e);
        }

        // 发送订单取消事件
        publishOrderEvent(order, "CANCELLED");
    }

    @Transactional
    public void payOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException("订单不属于当前用户");
        }
        if (order.getStatus() != 1) {
            throw new BizException("订单状态不允许支付");
        }
        order.setStatus(2); // 已付款
        order.setPayType(1); // 支付宝
        updateById(order);

        // 发送订单支付事件
        publishOrderEvent(order, "PAID");
    }

    // ==================== 后台管理方法 ====================

    @Transactional
    public void shipOrder(Long orderId, String logisticsCompany, String trackingNumber) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 2) {
            throw new BizException("仅已付款订单可发货");
        }
        order.setStatus(3); // 已发货
        order.setLogisticsCompany(logisticsCompany);
        order.setTrackingNumber(trackingNumber);
        updateById(order);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> getOrderListAdmin(
            int page, int size, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    @Transactional
    public void completeOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 3) {
            throw new BizException("仅已发货订单可完成");
        }
        order.setStatus(4); // 已完成
        order.setReceiveTime(LocalDateTime.now());
        updateById(order);
    }

    // ==================== 超时取消 ====================

    @Transactional
    public void cancelOrderByTimeout(Long orderId) {
        Order order = getById(orderId);
        if (order == null || order.getStatus() != 1) {
            return; // 不在待付款状态，无需处理
        }
        order.setStatus(5); // 已取消
        updateById(order);

        // 恢复库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                skuService.restoreStock(item.getProductId(), item.getSkuId(), item.getQuantity());
            } else {
                Product product = productMapper.selectById(item.getProductId());
                if (product != null && product.getSeckillPrice() != null && product.getSeckillStock() != null) {
                    productMapper.restoreSeckillStock(item.getProductId(), item.getQuantity());
                } else {
                    productService.restoreStock(item.getProductId(), item.getQuantity());
                }
            }
        }

        // 恢复优惠券（best-effort）
        try {
            couponService.restoreCoupon(orderId);
        } catch (Exception e) {
            log.error("恢复优惠券失败: orderId={}", orderId, e);
        }

        // 发送订单取消事件
        publishOrderEvent(order, "CANCELLED");
    }

    // ==================== 用户侧方法 ====================

    @Transactional
    public void confirmReceive(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 3) {
            throw new BizException("仅已发货订单可确认收货");
        }
        order.setStatus(4); // 已完成
        order.setReceiveTime(LocalDateTime.now());
        updateById(order);

        // 发送订单完成事件
        publishOrderEvent(order, "COMPLETED");
    }

    private void publishOrderEvent(Order order, String eventType) {
        try {
            OrderEvent event = new OrderEvent(
                    order.getId(),
                    order.getUserId(),
                    order.getOrderNo(),
                    order.getTotalAmount(),
                    eventType,
                    LocalDateTime.now());
            rocketMQTemplate.convertAndSend("order-topic:" + eventType, event);
            log.info("订单事件已发送: orderId={}, eventType={}", order.getId(), eventType);
        } catch (Exception e) {
            log.error("发送订单事件失败: orderId={}, eventType={}", order.getId(), eventType, e);
        }
    }
}
