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
}
