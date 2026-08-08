package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponRequest {

    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    @NotBlank(message = "优惠类型不能为空")
    private String discountType;

    @NotNull(message = "优惠金额/折扣不能为空")
    private BigDecimal discountValue;

    @NotNull(message = "最低消费门槛不能为空")
    private BigDecimal minOrderAmount;

    @NotNull(message = "发放总量不能为空")
    private Integer totalQuantity;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    private Long applicableCategoryId; // NULL=全部

    private Long applicableProductId; // NULL=全部

    private Integer isNewbie; // 1=新人专享券（注册后自动发放），默认 0
}
