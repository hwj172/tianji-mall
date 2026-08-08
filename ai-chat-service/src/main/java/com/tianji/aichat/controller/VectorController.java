package com.tianji.aichat.controller;

import com.tianji.aichat.dto.VectorImageRequest;
import com.tianji.aichat.dto.VectorUpsertRequest;
import com.tianji.aichat.service.VectorSearchService;
import com.tianji.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
public class VectorController {

    private final VectorSearchService vectorSearchService;

    @PostMapping("/upsert")
    public void upsert(@RequestBody @Valid VectorUpsertRequest request) {
        vectorSearchService.upsertProduct(
                request.getProductId(), request.getName(), request.getDescription());
    }

    /** 商品图片向量回填（内部端点） */
    @PostMapping("/image-upsert")
    public void imageUpsert(@RequestBody @Valid VectorImageRequest request) {
        vectorSearchService.upsertProductImage(request.getProductId(), request.getImageUrl());
    }

    /** 以图搜图：图片 → 相似商品 ID 列表 */
    @PostMapping("/image-search")
    public R<List<Long>> imageSearch(@RequestBody @Valid VectorImageRequest request) {
        return R.ok(vectorSearchService.searchByImage(request.getImageUrl(), 10));
    }
}
