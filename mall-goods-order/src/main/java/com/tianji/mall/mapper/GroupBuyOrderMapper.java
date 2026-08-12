package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.GroupBuyOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GroupBuyOrderMapper extends BaseMapper<GroupBuyOrder> {

    @Select("SELECT * FROM group_buy_order WHERE group_id = #{groupId} FOR UPDATE")
    GroupBuyOrder selectByGroupIdForUpdate(@Param("groupId") String groupId);

    @Select("SELECT * FROM group_buy_order WHERE group_id = #{groupId}")
    GroupBuyOrder selectByGroupId(@Param("groupId") String groupId);

    @Update("UPDATE group_buy_order SET current_count = current_count + 1, "
            + "status = CASE WHEN current_count + 1 >= target_tier THEN 'SUCCESS' ELSE 'OPEN' END "
            + "WHERE id = #{id} AND current_count < target_tier AND status = 'OPEN'")
    int incrementCount(@Param("id") Long id);

    @Select("SELECT * FROM group_buy_order WHERE product_id = #{productId} AND status = 'OPEN' AND expire_time > NOW() ORDER BY create_time DESC")
    List<GroupBuyOrder> selectOpenByProductId(@Param("productId") Long productId);

    @Select("SELECT * FROM group_buy_order WHERE status = 'OPEN' AND expire_time < NOW()")
    List<GroupBuyOrder> selectExpiredOpen();

    @Select("SELECT * FROM group_buy_order WHERE status = 'OPEN' ORDER BY create_time DESC")
    List<GroupBuyOrder> selectAllOpen();

    @Select("SELECT * FROM group_buy_order WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<GroupBuyOrder> selectByUserId(@Param("userId") Long userId);
}
