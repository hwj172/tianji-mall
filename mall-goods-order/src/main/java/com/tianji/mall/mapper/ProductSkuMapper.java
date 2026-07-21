package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Update("UPDATE product_sku SET stock = stock - #{qty}, sales = sales + #{qty} WHERE id = #{skuId} AND stock >= #{qty}")
    int deductStock(@Param("skuId") Long skuId, @Param("qty") int qty);

    @Update("UPDATE product_sku SET stock = stock + #{qty}, sales = sales - #{qty} WHERE id = #{skuId}")
    int restoreStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
