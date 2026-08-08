package com.tianji.mall.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewResponse {

    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Integer rating;
    private String content;
    private String images;
    private Integer status;
    private String reply;          // 商家回复
    private LocalDateTime replyTime;
    private LocalDateTime createTime;
    private String username;   // 评价用户昵称（Feign 查 user-service，失败为 null）
}
