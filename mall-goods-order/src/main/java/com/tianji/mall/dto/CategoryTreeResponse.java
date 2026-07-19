package com.tianji.mall.dto;

import com.tianji.mall.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CategoryTreeResponse {

    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private List<Category> children;
}
