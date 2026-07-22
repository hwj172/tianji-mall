package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.BrowsingHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BrowsingHistoryMapper extends BaseMapper<BrowsingHistory> {

    @Delete("DELETE FROM browsing_history WHERE user_id = #{userId}")
    int clearAll(@Param("userId") Long userId);
}
