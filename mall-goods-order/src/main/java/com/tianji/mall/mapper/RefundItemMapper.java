package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.RefundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RefundItemMapper extends BaseMapper<RefundItem> {

    @Select("SELECT * FROM refund_item WHERE refund_id = #{refundId}")
    List<RefundItem> selectByRefundId(@Param("refundId") Long refundId);
}
