package com.tianji.mall.dto;

import com.tianji.mall.entity.Banner;
import com.tianji.mall.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeResponse {

    private List<Banner> banners;
    private List<CategoryTreeResponse> categories;
    private List<Product> hotProducts;
    private RecommendResponse recommend;
}
