package com.tianji.mall.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.dto.OrderEvent;
import com.tianji.mall.dto.OrderItemResponse;
import com.tianji.mall.entity.Address;
import com.tianji.mall.entity.CartItem;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.ProductSku;
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
    private final PromotionService promotionService;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final SeckillService seckillService;
    private final ProductMapper productMapper;
    private final MemberService memberService;

    @SentinelResource(value = "createOrder", blockHandler = "createOrderBlockHandler")
    @Transactional
    public Order createOrder(Long userId, OrderCreateRequest req) {
        // 1. 校验地址
        Address address = addressService.getById(req.getAddressId());
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ADDRESS_NOT_FOUND);
        }

        // 2. 解析订单行（购物车结算 或 立即购买直购）
        List<Long> cartItemIds = req.getCartItemIds();
        boolean fromCart = cartItemIds != null && !cartItemIds.isEmpty();
        List<OrderLine> lines;
        if (fromCart) {
            List<CartItem> cartItems = cartService.listByIds(cartItemIds);
            if (cartItems.isEmpty()) {
                throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
            }
            for (CartItem item : cartItems) {
                if (!item.getUserId().equals(userId)) {
                    throw new BizException(BizErrorCode.CART_ITEM_NOT_OWNER);
                }
                if (item.getChecked() != 1) {
                    throw new BizException(BizErrorCode.CART_ITEM_NOT_CHECKED);
                }
            }
            lines = cartItems.stream()
                    .map(ci -> new OrderLine(ci.getProductId(), ci.getSkuId(), ci.getQuantity()))
                    .toList();
        } else {
            if (req.getDirectItems() == null || req.getDirectItems().isEmpty()) {
                throw new BizException(BizErrorCode.CART_ITEM_NOT_FOUND);
            }
            lines = req.getDirectItems().stream()
                    .map(d -> new OrderLine(d.getProductId(), d.getSkuId(), d.getQuantity()))
                    .toList();
        }

        // 3. 获取商品 ID 列表
        List<Long> productIds = lines.stream()
                .map(OrderLine::productId)
                .distinct()
                .toList();

        // 3.5 获取分布式锁（按 productId 和 skuId 排序，避免死锁）
        List<String> lockKeyStrings = new ArrayList<>();
        for (OrderLine line : lines) {
            if (line.skuId() != null) {
                lockKeyStrings.add("lock:sku:" + line.skuId());
            } else {
                lockKeyStrings.add("lock:product:" + line.productId());
            }
        }
        List<String> sortedLockKeys = lockKeyStrings.stream().sorted().distinct().toList();
        RLock[] lockArray = sortedLockKeys.stream()
                .map(key -> redissonClient.getLock(key))
                .toArray(RLock[]::new);
        RLock multiLock = redissonClient.getMultiLock(lockArray);

        boolean locked = false;
        try {
            locked = multiLock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException(BizErrorCode.SYSTEM_BUSY);
            }

            // 4. 锁内重新读取库存（拿到锁后库存可能已变化）
            Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            // 5. 校验库存并计算金额
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> orderItems = new ArrayList<>();
            for (OrderLine line : lines) {
                Product product = productMap.get(line.productId());
                // 仅上架商品(status=1)可下单；待审核(2)/下架(0)均拦截
                if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                    throw new BizException(BizErrorCode.PRODUCT_NOT_FOUND, product != null ? product.getName() : "未知");
                }

                BigDecimal itemPrice;
                String skuSpecs = null;

                if (line.skuId() != null) {
                    // SKU 商品：用 SKU 价格和库存
                    ProductSku sku = skuService.getById(line.skuId());
                    if (sku == null || !sku.getProductId().equals(line.productId())) {
                        throw new BizException(BizErrorCode.SKU_NOT_FOUND, product.getName());
                    }
                    if (sku.getStatus() == null || sku.getStatus() != 1) {
                        throw new BizException(BizErrorCode.SKU_NOT_FOUND, product.getName());
                    }
                    if (sku.getStock() < line.quantity()) {
                        throw new BizException(BizErrorCode.STOCK_INSUFFICIENT, product.getName());
                    }
                    itemPrice = sku.getPrice() != null ? sku.getPrice() : product.getPrice();
                    skuSpecs = sku.getSpecs();
                } else {
                    // 无 SKU：判秒杀窗口
                    if (seckillService.isSeckillActive(product)) {
                        if (product.getSeckillStock() < line.quantity()) {
                            throw new BizException(BizErrorCode.SECKILL_STOCK_INSUFFICIENT, product.getName());
                        }
                        itemPrice = product.getSeckillPrice();
                    } else {
                        if (product.getStock() < line.quantity()) {
                            throw new BizException(BizErrorCode.STOCK_INSUFFICIENT, product.getName());
                        }
                        itemPrice = product.getPrice();
                    }
                }

                OrderItem orderItem = new OrderItem();
                orderItem.setProductId(product.getId());
                orderItem.setProductName(product.getName());
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(line.quantity());
                orderItem.setSkuId(line.skuId());
                orderItem.setSkuSpecs(skuSpecs);
                orderItems.add(orderItem);

                totalAmount = totalAmount.add(itemPrice.multiply(BigDecimal.valueOf(line.quantity())));
            }

            // 6. 满减活动折扣（按优惠券前金额判断门槛，与优惠券叠加）
            BigDecimal discount = promotionService.calculateDiscount(totalAmount);

            // 6.1 优惠券折扣（锁内）
            if (req.getCouponId() != null) {
                discount = discount.add(couponService.applyCoupon(userId, req.getCouponId(), totalAmount));
            }

            // 注意：拼团折扣不在此处由客户端传入（groupBuyDiscount 客户端可控，可构造 0 元购）。
            // 拼团订单在创建后由 GroupBuyService 服务端重算折扣并调用 applyGroupBuyDiscount 应用。

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
                            throw new BizException(BizErrorCode.SECKILL_STOCK_INSUFFICIENT, product.getName());
                        }
                    } else {
                        productService.deductStock(item.getProductId(), item.getQuantity());
                    }
                }
            }

            // 11. 清购物车（仅购物车结算；直购不产生购物车项）
            if (fromCart) {
                cartService.removeByIds(cartItemIds);
            }

            // 12. 发送订单创建事件
            publishOrderEvent(order, "CREATED");

            // 13. 发送 30 分钟超时延迟消息（best-effort）
            try {
                org.springframework.messaging.Message<String> timeoutMsg =
                        org.springframework.messaging.support.MessageBuilder
                                .withPayload(order.getId().toString())
                                .build();
                // 延迟消息必须用 4 参 syncSend(dest, msg, timeout, delayLevel)——DELAY header 不生效
                rocketMQTemplate.syncSend("order-topic:TIMEOUT_CHECK", timeoutMsg, 3000, 16);
            } catch (Exception e) {
                log.error("发送超时延迟消息失败: orderId={}", order.getId(), e);
            }

            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(BizErrorCode.SYSTEM_BUSY);
        } finally {
            // 仅在持有锁时 unlock，避免对未持有的锁调用 unlock 抛 IllegalMonitorStateException 掩盖原始异常
            if (locked) {
                multiLock.unlock();
            }
        }
    }

    /**
     * Sentinel 限流熔断时触发：抛系统繁忙异常，由全局异常处理器统一返回。
     */
    public Order createOrderBlockHandler(Long userId, OrderCreateRequest req, BlockException ex) {
        log.warn("createOrder 被 Sentinel 限流: userId={}", userId);
        throw new BizException(BizErrorCode.SYSTEM_BUSY);
    }

    /** 订单行：购物车结算与立即购买直购的统一中间结构 */
    private record OrderLine(Long productId, Long skuId, Integer quantity) {}

    public List<Order> getOrderList(Long userId) {
        return getOrderPage(userId, 1, 50, null).getRecords();
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> getOrderPage(Long userId, int page, int size, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        List<OrderItemResponse> itemResponses = orderItems.stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getProductId(), i.getProductName(), i.getPrice(), i.getQuantity(),
                        i.getSkuId(), i.getSkuSpecs()))
                .toList();

        Address address = addressService.getById(order.getAddressId());

        return new OrderDetailResponse(order, itemResponses, address);
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        // 原子 CAS：仅当仍为待付款(status=1)时才取消。
        // 并发用户取消 + 超时取消时只有一个路径 CAS 成功，避免库存/优惠券被双重恢复
        int affected = baseMapper.updateStatusIfPending(orderId, 5);
        if (affected == 0) {
            throw new BizException(BizErrorCode.ORDER_CANNOT_CANCEL);
        }
        order.setStatus(5); // 已取消

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
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ORDER_NOT_OWNER);
        }
        // 原子 CAS：仅当仍为待付款(status=1)时置为已付款(2)，避免并发重复支付
        int affected = baseMapper.updateStatusIfPending(orderId, 2);
        if (affected == 0) {
            // 幂等：已支付状态视为成功（支付回调/前端重复通知场景）
            if (order.getStatus() != null && order.getStatus() == 2) {
                return;
            }
            throw new BizException(BizErrorCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(2); // 已付款
        order.setPayType(1); // 支付宝

        // 发放订单积分（best-effort，失败不阻塞支付主流程）
        try {
            memberService.addPointsForOrder(userId, order.getTotalAmount());
        } catch (Exception e) {
            log.error("发放订单积分失败: orderId={}, userId={}", orderId, userId, e);
        }

        // 发送订单支付事件
        publishOrderEvent(order, "PAID");
    }

    /**
     * 拼团折扣应用：由 GroupBuyService 服务端重算后调用（不信任客户端传入的折扣金额）。
     * 订单创建后调整 totalAmount，下限保护（不为负、不超原价）。
     */
    @Transactional
    public void applyGroupBuyDiscount(Long orderId, BigDecimal discount) {
        if (discount == null || discount.signum() <= 0) {
            return;
        }
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 1) {
            return; // 仅待付款订单可调整（已支付/取消则不生效）
        }
        // 折扣不能超过原总额（防服务端计算偏差导致负金额）
        if (discount.compareTo(order.getTotalAmount()) > 0) {
            discount = order.getTotalAmount();
        }
        order.setTotalAmount(order.getTotalAmount().subtract(discount));
        updateById(order);
    }

    // ==================== 后台管理方法 ====================

    @Transactional
    public void shipOrder(Long orderId, String logisticsCompany, String trackingNumber) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 2) {
            throw new BizException(BizErrorCode.ORDER_NOT_PAID);
        }
        order.setStatus(3); // 已发货
        order.setLogisticsCompany(logisticsCompany);
        order.setTrackingNumber(trackingNumber);
        updateById(order);

        // 发送发货事件
        publishOrderEvent(order, "SHIPPED");
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> getOrderListAdmin(
            int page, int size, Integer status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    /** 按店铺商品过滤订单（卖家只看到自己店铺的订单） */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> getOrdersByShop(
            Long shopId, int page, int size) {
        // 本店所有商品 ID
        List<Long> productIds = productService.lambdaQuery()
                .eq(Product::getShopId, shopId)
                .select(Product::getId)
                .list()
                .stream()
                .map(Product::getId)
                .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        }
        // 包含本店商品的订单 ID
        List<Long> orderIds = orderItemMapper.selectOrderIdsByProductIds(productIds);
        if (orderIds.isEmpty()) {
            return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .in(Order::getId, orderIds)
                .orderByDesc(Order::getCreateTime);
        return page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    @Transactional
    public void completeOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 3) {
            throw new BizException(BizErrorCode.ORDER_NOT_COMPLETED);
        }
        order.setStatus(4); // 已完成
        order.setReceiveTime(LocalDateTime.now());
        updateById(order);

        // 发送完成事件
        publishOrderEvent(order, "COMPLETED");
    }

    // ==================== 超时取消 ====================

    @Transactional
    public void cancelOrderByTimeout(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return; // 订单不存在，无需处理
        }
        // 原子 CAS：仅当仍为待付款(status=1)时才取消。
        // 与用户取消并发时只有一个路径 CAS 成功，失败路径直接返回，不重复恢复库存/优惠券
        int affected = baseMapper.updateStatusIfPending(orderId, 5);
        if (affected == 0) {
            return; // 已被并发路径取消，无需处理
        }
        order.setStatus(5); // 已取消

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
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 3) {
            throw new BizException(BizErrorCode.ORDER_NOT_RECEIVABLE);
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
