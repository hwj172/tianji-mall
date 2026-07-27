package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ShopFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShopFollowMapper extends BaseMapper<ShopFollow> {

    @Select("SELECT COUNT(*) FROM shop_follow WHERE user_id = #{userId}")
    long selectCountByUserId(@Param("userId") Long userId);
}
