package com.tianji.aichat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 对话内容 */
    private String content;

    /** 关联商品ID（逗号分隔） */
    private String productIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
