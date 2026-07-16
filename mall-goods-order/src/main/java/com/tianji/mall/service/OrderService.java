package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizException;
import com.tianji.mall.dto.OrderCreateRequest;
import com.tianji.mall.dto.OrderDetailResponse;
import com.tianji.mall.dto.OrderItemResponse;
import com.tianji.mall.entity.*;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private final OrderItemMapper orderItemMapper;
    private final CartService cartService;
    private final ProductService productService;
    private final AddressService addressService;

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

        // 3. 校验库存并计算金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Product product = productService.getById(cartItem.getProductId());
            if (product == null || product.getStatus() == 0) {
                throw new BizException("商品「" + (product != null ? product.getName() : "未知") + "」已下架");
            }
            if (product.getStock() < cartItem.getQuantity()) {
                throw new BizException("商品「" + product.getName() + "」库存不足");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItems.add(orderItem);

            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        // 4. 生成订单号
        String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // 5. 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(1); // 待付款
        order.setAddressId(req.getAddressId());
        save(order);

        // 6. 写入订单明细
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 7. 扣库存
        for (OrderItem item : orderItems) {
            Product product = productService.getById(item.getProductId());
            product.setStock(product.getStock() - item.getQuantity());
            productService.updateById(product);
        }

        // 8. 清购物车
        cartService.removeByIds(req.getCartItemIds());

        return order;
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
                .map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(), i.getPrice(), i.getQuantity()))
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

        // 恢复库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            Product product = productService.getById(item.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productService.updateById(product);
            }
        }
    }

    @Transactional
    public void payOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BizException("订单状态不允许支付");
        }
        order.setStatus(2); // 已付款
        order.setPayType(1); // 支付宝
        updateById(order);
    }
}
