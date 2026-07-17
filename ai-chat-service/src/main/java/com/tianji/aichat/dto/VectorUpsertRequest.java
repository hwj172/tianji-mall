package com.tianji.aichat.dto;

import lombok.Data;

@Data
public class VectorUpsertRequest {
    private Long productId;
    private String name;
    private String description;
}
