package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("UPDATE product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("UPDATE product SET sales = sales + #{quantity} WHERE id = #{productId}")
    int incrementSales(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("UPDATE product SET seckill_stock = seckill_stock - #{quantity} WHERE id = #{productId} AND seckill_stock >= #{quantity}")
    int deductSeckillStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Update("UPDATE product SET seckill_stock = seckill_stock + #{quantity} WHERE id = #{productId}")
    int restoreSeckillStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /** 原子审核：仅待审核(status=2)商品可改为目标状态，防并发重复审核覆盖 */
    @Update("UPDATE product SET status = #{targetStatus} WHERE id = #{id} AND status = 2")
    int updateStatusIfPendingAudit(@Param("id") Long id, @Param("targetStatus") Integer targetStatus);

    @Update("UPDATE product SET seckill_price = NULL, seckill_stock = NULL, seckill_start_time = NULL, seckill_end_time = NULL WHERE id = #{productId}")
    int clearSeckill(@Param("productId") Long productId);

    /** 该店在售商品涉及的分类（categoryId + 名称 + 商品数），供店铺页分类筛选 tab */
    @Select("SELECT p.category_id AS categoryId, c.name AS name, COUNT(*) AS count " +
            "FROM product p LEFT JOIN category c ON c.id = p.category_id " +
            "WHERE p.shop_id = #{shopId} AND p.status = 1 AND p.category_id IS NOT NULL " +
            "GROUP BY p.category_id, c.name ORDER BY count DESC")
    List<Map<String, Object>> selectShopCategories(@Param("shopId") Long shopId);
}
