package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作人ID */
    private Long operatorId;
    /** 操作人角色 */
    private String operatorRole;
    /** 操作动作 */
    private String action;
    /** 对象类型 */
    private String targetType;
    /** 对象ID */
    private Long targetId;
    /** 详情 */
    private String detail;
    /** 操作IP */
    private String ip;
    private LocalDateTime createTime;
}
