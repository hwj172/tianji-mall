package com.tianji.mall.dto;

import lombok.Data;

@Data
public class ShopUpdateRequest {
    private String name;
    private String logo;
    private String description;
    private String notice;
}
