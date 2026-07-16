package com.tianji.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.pay.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
