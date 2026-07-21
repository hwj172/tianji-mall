package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM `order` WHERE status IN (2,3,4)")
    BigDecimal selectTotalGmv();

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
    BigDecimal selectGmvByTimeRange(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4)")
    Long selectPaidOrderCount();

    @Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
    Long selectPaidOrderCountByTimeRange(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT status, COUNT(*) as cnt FROM `order` GROUP BY status")
    List<Map<String, Object>> selectStatusDistribution();
}
