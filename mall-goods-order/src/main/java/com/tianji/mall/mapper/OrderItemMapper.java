package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("SELECT oi.product_id, p.name, SUM(oi.quantity) as sales, " +
            "SUM(oi.price * oi.quantity) as amount " +
            "FROM order_item oi JOIN product p ON oi.product_id = p.id " +
            "JOIN `order` o ON oi.order_id = o.id " +
            "WHERE o.status IN (2,3,4) " +
            "GROUP BY oi.product_id, p.name ORDER BY sales DESC LIMIT 10")
    List<Map<String, Object>> selectTopSellingProducts();

    @Select("SELECT p.category_id, c.name as category_name, " +
            "SUM(oi.price * oi.quantity) as amount " +
            "FROM order_item oi JOIN product p ON oi.product_id = p.id " +
            "JOIN category c ON p.category_id = c.id " +
            "JOIN `order` o ON oi.order_id = o.id " +
            "WHERE o.status IN (2,3,4) " +
            "GROUP BY p.category_id, c.name")
    List<Map<String, Object>> selectCategorySales();
}
