package com.tianji.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInResponse {

    /** 本次签到是否成功（已签过则为 false） */
    private Boolean signed;
    /** 本次签到获得的积分数 */
    private Integer points;
    /** 今天是否已签到 */
    private Boolean todaySigned;
}
