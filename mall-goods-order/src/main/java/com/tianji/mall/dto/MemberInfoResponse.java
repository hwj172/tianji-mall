package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberInfoResponse {

    private Integer level;
    private String levelName;
    private Integer points;
    private Integer totalPoints;
    private Integer nextLevel;
    private String nextLevelName;
    private Integer progress;
    /** 今天是否已签到（前端签到按钮禁用态） */
    private Boolean todaySigned;
}
