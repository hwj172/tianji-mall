package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.GroupBuyParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupBuyParticipantMapper extends BaseMapper<GroupBuyParticipant> {

    @Select("SELECT * FROM group_buy_participant WHERE group_buy_order_id = #{gboId}")
    List<GroupBuyParticipant> selectByGroupBuyOrderId(@Param("gboId") Long gboId);
}
