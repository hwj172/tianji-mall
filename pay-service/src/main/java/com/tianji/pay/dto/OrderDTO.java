package com.tianji.pay.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDTO {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer status;
    private Long addressId;
}
