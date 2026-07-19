package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund")
public class Refund {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String reason;
    private String status;
    @TableField("alipay_refund_no")
    private String alipayRefundNo;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
