package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /** 原子扣减已领取数量，校验库存 */
    @Update("UPDATE coupon SET used_quantity = used_quantity + 1 WHERE id = #{id} AND used_quantity < total_quantity")
    int incrementUsedQuantity(@Param("id") Long id);
}
