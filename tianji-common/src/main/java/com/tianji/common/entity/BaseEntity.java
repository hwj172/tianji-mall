package com.tianji.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础实体 — 公共字段（非数据库映射，供子类 @TableField 覆盖）
 */
@Data
public abstract class BaseEntity {

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
