package com.tianji.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {

    @NotNull(message = "收货地址ID不能为空")
    private Long addressId;

    private List<Long> cartItemIds;             // 购物车结算项，优先取 cartItemIds；同时传入时 directItems 忽略

    @Valid
    private List<DirectOrderItem> directItems;  // 立即购买直购项，cartItemIds 为空时生效

    private Long couponId;  // 用户优惠券记录ID，可选

    private String groupBuyGroupId;      // 拼团团ID（join时传入，start不传）
    private java.math.BigDecimal groupBuyDiscount; // 拼团折扣金额
}
