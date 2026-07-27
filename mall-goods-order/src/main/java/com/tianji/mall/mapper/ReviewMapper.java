package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    @Select("SELECT COUNT(*) as count, COALESCE(AVG(rating), 0) as avgRating, " +
            "SUM(CASE WHEN rating >= 4 THEN 1 ELSE 0 END) as goodCount " +
            "FROM review WHERE product_id = #{productId} AND status = 1")
    Map<String, Object> selectStatsByProductId(@Param("productId") Long productId);

    @Select("SELECT oi.order_id, oi.product_id, oi.product_name, oi.price, oi.sku_id, oi.sku_specs, " +
            "p.images AS product_image, o.create_time AS order_create_time " +
            "FROM order_item oi " +
            "JOIN `order` o ON o.id = oi.order_id " +
            "LEFT JOIN product p ON p.id = oi.product_id " +
            "LEFT JOIN review r ON r.user_id = o.user_id AND r.order_id = o.id AND r.product_id = oi.product_id " +
            "WHERE o.user_id = #{userId} AND o.status = 4 AND r.id IS NULL " +
            "ORDER BY o.create_time DESC " +
            "LIMIT #{offset}, #{limit}")
    java.util.List<java.util.Map<String, Object>> selectPendingReviews(@Param("userId") Long userId,
                                                                       @Param("offset") int offset,
                                                                       @Param("limit") int limit);
}
