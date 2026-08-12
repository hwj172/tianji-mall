package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    // GMV 剔除已成功退款的金额，避免退款订单全额虚计
    @Select("SELECT COALESCE(SUM(total_amount), 0) - COALESCE((SELECT SUM(amount) FROM refund WHERE status = 'success'), 0) "
            + "FROM `order` WHERE status IN (2,3,4)")
    BigDecimal selectTotalGmv();

    @Select("SELECT COALESCE(SUM(total_amount), 0) - COALESCE((SELECT SUM(amount) FROM refund WHERE status = 'success'), 0) "
            + "FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
    BigDecimal selectGmvByTimeRange(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4)")
    Long selectPaidOrderCount();

    @Select("SELECT COUNT(*) FROM `order` WHERE status IN (2,3,4) AND create_time >= #{startTime}")
    Long selectPaidOrderCountByTimeRange(@Param("startTime") LocalDateTime startTime);

    @Select("SELECT status, COUNT(*) as cnt FROM `order` GROUP BY status")
    List<Map<String, Object>> selectStatusDistribution();

    /**
     * 查询超时未付款（status=1 且创建时间早于 cutoff）的订单 ID，供定时兜底扫描取消。
     */
    @Select("SELECT id FROM `order` WHERE status = 1 AND create_time < #{cutoff}")
    List<Long> selectExpiredPendingOrderIds(@Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT status, COUNT(*) AS cnt FROM `order` WHERE user_id = #{userId} GROUP BY status")
    List<Map<String, Object>> selectOrderStats(@Param("userId") Long userId);

    /**
     * 原子 CAS：仅当订单仍为待付款(status=1)时才更新到目标状态。
     * 返回 affected rows，=0 说明订单已被并发路径取消/状态已变更，调用方不得重复恢复库存/优惠券。
     */
    @Update("UPDATE `order` SET status = #{targetStatus} WHERE id = #{id} AND status = 1")
    int updateStatusIfPending(@Param("id") Long id, @Param("targetStatus") Integer targetStatus);

    /**
     * 对订单行加排他锁（须在事务内调用），用于退款防重检查等并发控制。
     */
    @Select("SELECT * FROM `order` WHERE id = #{id} FOR UPDATE")
    Order selectByIdForUpdate(@Param("id") Long id);
}
