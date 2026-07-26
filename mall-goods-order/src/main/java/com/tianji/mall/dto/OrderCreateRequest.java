package com.tianji.mall.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {

    @NotNull(message = "收货地址ID不能为空")
    private Long addressId;

    @NotEmpty(message = "购物车项不能为空")
    private List<Long> cartItemIds;

    private Long couponId;  // 用户优惠券记录ID，可选

    private String groupBuyGroupId;      // 拼团团ID（join时传入，start不传）
    private java.math.BigDecimal groupBuyDiscount; // 拼团折扣金额
}
