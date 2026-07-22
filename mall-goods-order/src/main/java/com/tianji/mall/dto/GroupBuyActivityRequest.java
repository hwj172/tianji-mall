package com.tianji.mall.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupBuyActivityRequest {
    @NotNull
    private Long productId;
    @NotEmpty
    private List<GroupBuyTier> tiers;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private Integer expireHours = 24;
}
