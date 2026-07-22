package com.tianji.mall.dto;

import com.tianji.mall.entity.GroupBuy;
import com.tianji.mall.entity.GroupBuyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyDetailResponse {
    private GroupBuy activity;
    private List<GroupBuyOrder> openGroups;
    private List<GroupBuyTier> tiers;
}
