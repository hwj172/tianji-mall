package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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

    @Update("UPDATE product SET seckill_price = NULL, seckill_stock = NULL, seckill_start_time = NULL, seckill_end_time = NULL WHERE id = #{productId}")
    int clearSeckill(@Param("productId") Long productId);
}
