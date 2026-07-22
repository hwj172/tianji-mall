package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ProductSimilarity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductSimilarityMapper extends BaseMapper<ProductSimilarity> {

    @Delete("DELETE FROM product_similarity")
    void truncate();

    @Insert("<script>" +
            "INSERT IGNORE INTO product_similarity (product_id, similar_product_id, co_count, score) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.productId}, #{item.similarProductId}, #{item.coCount}, #{item.score})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<ProductSimilarity> list);

    @Select("SELECT * FROM product_similarity WHERE product_id = #{productId} ORDER BY score DESC LIMIT #{limit}")
    List<ProductSimilarity> selectByProductId(@Param("productId") Long productId, @Param("limit") int limit);
}
