package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String logo;
    private String description;
    private Long sellerId;
    private Integer status;       // 1=营业 0=关闭
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
