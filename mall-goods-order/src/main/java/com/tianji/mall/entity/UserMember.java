package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_member")
public class UserMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer level;
    private Integer points;
    private Integer totalPoints;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
