package com.tianji.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_similarity")
public class ProductSimilarity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long similarProductId;
    private Integer coCount;
    private BigDecimal score;
    private LocalDateTime updateTime;
}
