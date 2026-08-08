package com.tianji.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.mall.entity.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    /**
     * 卖家待确认收货的退货单：退款明细关联商品，商品归属卖家店铺，且买家已寄回（SHIPPED）。
     */
    @Select("SELECT DISTINCT r.* FROM refund r " +
            "INNER JOIN refund_item ri ON ri.refund_id = r.id " +
            "INNER JOIN product p ON p.id = ri.product_id " +
            "WHERE p.shop_id = #{shopId} AND r.refund_type = 'RETURN_REFUND' " +
            "AND r.return_status = 'SHIPPED' AND r.status = 'processing' " +
            "ORDER BY r.created_at DESC")
    List<Refund> selectSellerPendingRefunds(@Param("shopId") Long shopId);
}
