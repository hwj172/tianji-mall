package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_item")
public class RefundItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long refundId;
    private Long orderItemId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private BigDecimal amount;
    private LocalDateTime createTime;
}
