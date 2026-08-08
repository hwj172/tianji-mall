package com.tianji.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exception.BizErrorCode;
import com.tianji.common.exception.BizException;
import com.tianji.common.result.R;
import com.tianji.mall.dto.PendingReviewResponse;
import com.tianji.mall.dto.ReviewCreateRequest;
import com.tianji.mall.dto.ReviewResponse;
import com.tianji.mall.entity.Order;
import com.tianji.mall.entity.OrderItem;
import com.tianji.mall.entity.Product;
import com.tianji.mall.entity.Review;
import com.tianji.mall.feign.UserFeignClient;
import com.tianji.mall.mapper.OrderItemMapper;
import com.tianji.mall.mapper.OrderMapper;
import com.tianji.mall.mapper.ProductMapper;
import com.tianji.mall.mapper.ReviewMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReviewService extends ServiceImpl<ReviewMapper, Review> {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final UserFeignClient userFeignClient;

    public ReviewService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                         ProductMapper productMapper, UserFeignClient userFeignClient) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.userFeignClient = userFeignClient;
    }

    @Transactional
    public void createReview(Long userId, ReviewCreateRequest req) {
        // 验证订单存在且属于当前用户
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(BizErrorCode.ORDER_NOT_FOUND);
        }

        // 验证订单已完成
        if (order.getStatus() != 4) {
            throw new BizException(BizErrorCode.REVIEW_ORDER_NOT_COMPLETED);
        }

        // 验证订单包含该商品
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, req.getOrderId())
                        .eq(OrderItem::getProductId, req.getProductId()));
        if (items.isEmpty()) {
            throw new BizException(BizErrorCode.REVIEW_PRODUCT_NOT_IN_ORDER);
        }

        // 验证未重复评价
        Review existing = baseMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getOrderId, req.getOrderId())
                .eq(Review::getProductId, req.getProductId()));
        if (existing != null) {
            throw new BizException(BizErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setProductId(req.getProductId());
        review.setOrderId(req.getOrderId());
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setImages(req.getImages());
        review.setStatus(1); // 默认审核通过
        save(review);

        log.info("用户 {} 评价商品 {}，评分 {}", userId, req.getProductId(), req.getRating());
    }

    public IPage<ReviewResponse> getProductReviews(Long productId, int page, int size, String filter) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .eq(Review::getStatus, 1)
                .orderByDesc(Review::getCreateTime);
        applyRatingFilter(wrapper, filter);
        Page<Review> pageResult = page(new Page<>(page, size), wrapper);
        return pageResult.convert(this::toResponse);
    }

    /**
     * 按评价分组筛选：good=好评(≥4)、middle=中评(=3)、bad=差评(≤2)，其他值/all 返回全部。
     */
    private void applyRatingFilter(LambdaQueryWrapper<Review> wrapper, String filter) {
        if (filter == null) return;
        switch (filter) {
            case "good" -> wrapper.ge(Review::getRating, 4);
            case "middle" -> wrapper.eq(Review::getRating, 3);
            case "bad" -> wrapper.le(Review::getRating, 2);
            default -> { /* all */ }
        }
    }

    /** 商家查看本店商品评价（按 shop 过滤） */
    public IPage<ReviewResponse> getShopReviews(Long shopId, int page, int size) {
        List<Long> productIds = productMapper.selectList(
                        new LambdaQueryWrapper<Product>().eq(Product::getShopId, shopId))
                .stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return new Page<>(page, size);
        }
        Page<Review> pageResult = page(new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .in(Review::getProductId, productIds)
                        .eq(Review::getStatus, 1)
                        .orderByDesc(Review::getCreateTime));
        return pageResult.convert(this::toResponse);
    }

    /** 商家回复评价（归属校验由调用方 SellerController 完成） */
    public void replyReview(Long reviewId, String reply) {
        Review review = getById(reviewId);
        if (review == null) {
            throw new BizException(BizErrorCode.REVIEW_NOT_FOUND);
        }
        review.setReply(reply);
        review.setReplyTime(LocalDateTime.now());
        updateById(review);
        log.info("商家回复评价 {}：{}", reviewId, reply);
    }

    public List<ReviewResponse> getMyReviews(Long userId, int page, int size) {
        Page<Review> pageResult = page(new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime));
        return pageResult.getRecords().stream().map(this::toResponse).toList();
    }

    private ReviewResponse toResponse(Review review) {
        ReviewResponse resp = new ReviewResponse();
        BeanUtils.copyProperties(review, resp);
        resp.setUsername(fetchUsername(review.getUserId()));
        return resp;
    }

    /** 查询评价用户昵称（best-effort，失败返回 null 不阻塞评价列表） */
    private String fetchUsername(Long userId) {
        if (userId == null) return null;
        try {
            R<Map<String, Object>> result = userFeignClient.getUserById(userId);
            Map<String, Object> data = result != null ? result.getData() : null;
            return data != null ? (String) data.get("username") : null;
        } catch (Exception e) {
            log.warn("查询评价用户昵称失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    public List<PendingReviewResponse> getPendingReviews(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = baseMapper.selectPendingReviews(userId, offset, size);
        return rows.stream().map(row -> {
            PendingReviewResponse resp = new PendingReviewResponse();
            resp.setOrderId(((Number) row.get("order_id")).longValue());
            resp.setProductId(((Number) row.get("product_id")).longValue());
            resp.setProductName((String) row.get("product_name"));
            resp.setProductImage((String) row.get("product_image"));
            resp.setPrice(new BigDecimal(row.get("price").toString()));
            Number skuId = (Number) row.get("sku_id");
            if (skuId != null) {
                resp.setSkuId(skuId.longValue());
            }
            resp.setSkuSpecs((String) row.get("sku_specs"));
            resp.setOrderCreateTime(((java.sql.Timestamp) row.get("order_create_time")).toLocalDateTime());
            return resp;
        }).toList();
    }
}
