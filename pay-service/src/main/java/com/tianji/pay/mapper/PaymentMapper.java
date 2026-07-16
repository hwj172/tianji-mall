package com.tianji.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.pay.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Update("UPDATE payment SET status = 2, trade_no = #{tradeNo} WHERE payment_no = #{paymentNo} AND status = 1")
    int markPaid(@Param("paymentNo") String paymentNo, @Param("tradeNo") String tradeNo);
}
