package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /** 原子操作：标记优惠券为已使用（防并发重复使用） */
    @Update("UPDATE user_coupon SET status = 'USED', used_time = NOW(), used_order_id = #{orderId} WHERE id = #{id} AND status = 'UNUSED'")
    int markUsed(@Param("id") Long id, @Param("orderId") Long orderId);

    /** 原子操作：恢复优惠券为未使用（订单取消时） */
    @Update("UPDATE user_coupon SET status = 'UNUSED', used_time = NULL, used_order_id = NULL WHERE id = #{id} AND status = 'USED'")
    int restoreUnused(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM user_coupon WHERE user_id = #{userId} AND status = 'UNUSED'")
    long selectCountByUserId(@Param("userId") Long userId);
}
