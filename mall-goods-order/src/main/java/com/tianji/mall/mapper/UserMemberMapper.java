package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.UserMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMemberMapper extends BaseMapper<UserMember> {

    /** 原子累加积分（防并发读-改-写丢积分） */
    @Update("UPDATE user_member SET points = points + #{points}, total_points = total_points + #{points}, "
            + "update_time = NOW() WHERE user_id = #{userId}")
    int incrementPoints(@Param("userId") Long userId, @Param("points") int points);

    /** 仅更新等级字段（不覆盖 points，防并发覆盖） */
    @Update("UPDATE user_member SET level = #{level}, update_time = NOW() WHERE user_id = #{userId}")
    int updateLevel(@Param("userId") Long userId, @Param("level") int level);
}
