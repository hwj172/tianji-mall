package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.GroupBuy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupBuyMapper extends BaseMapper<GroupBuy> {

    @Select("SELECT * FROM group_buy WHERE product_id = #{productId} AND status = 1 LIMIT 1")
    GroupBuy selectByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM group_buy WHERE status = 1 AND start_time <= NOW() AND end_time > NOW()")
    List<GroupBuy> selectActive();
}
