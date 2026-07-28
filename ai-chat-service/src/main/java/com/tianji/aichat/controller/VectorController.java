package com.tianji.aichat.controller;

import com.tianji.aichat.dto.VectorUpsertRequest;
import com.tianji.aichat.service.VectorSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
