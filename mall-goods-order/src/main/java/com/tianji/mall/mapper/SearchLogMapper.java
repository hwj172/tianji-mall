package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.SearchLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    @Select("SELECT keyword, COUNT(*) AS count FROM search_log " +
            "WHERE create_time > DATE_SUB(NOW(), INTERVAL 7 DAY) " +
            "GROUP BY keyword ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> selectHotKeywords();
}
