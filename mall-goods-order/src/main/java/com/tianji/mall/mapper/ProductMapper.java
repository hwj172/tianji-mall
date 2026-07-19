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
}
