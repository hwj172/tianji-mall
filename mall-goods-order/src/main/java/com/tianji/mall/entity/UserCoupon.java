package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long couponId;
    private String status;         // UNUSED / USED / EXPIRED
    private LocalDateTime usedTime;
    private Long usedOrderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
