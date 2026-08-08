package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("promotion")
public class Promotion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;          // 活动名称，如 "全场满300减50"
    private BigDecimal threshold; // 满多少
    private BigDecimal discount;  // 减多少
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;       // 1=启用 0=停用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
