package com.tianji.mall.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyStartRequest extends OrderCreateRequest {

    @NotNull(message = "拼团活动ID不能为空")
    private Long activityId;

    @Min(value = 2, message = "目标人数至少为2")
    private int targetCount;
}
