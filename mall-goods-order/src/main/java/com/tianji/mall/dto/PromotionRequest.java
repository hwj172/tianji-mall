package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PromotionRequest {

    @NotBlank(message = "活动名称不能为空")
    private String name;

    @NotNull(message = "满减门槛不能为空")
    private BigDecimal threshold;

    @NotNull(message = "减免金额不能为空")
    private BigDecimal discount;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
