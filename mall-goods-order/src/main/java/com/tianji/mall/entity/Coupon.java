package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String discountType;   // FIXED=满减 / PERCENT=折扣
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private Integer totalQuantity;
    private Integer usedQuantity;
    private Integer status;        // 1=启用 0=停用
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
