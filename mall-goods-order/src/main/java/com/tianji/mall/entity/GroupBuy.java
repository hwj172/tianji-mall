package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("group_buy")
public class GroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String tiers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer expireHours;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
