package com.tianji.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipRequest {

    @NotBlank(message = "物流单号不能为空")
    private String trackingNumber;

    @NotBlank(message = "物流公司不能为空")
    private String trackingCompany;
}
