package com.tianji.mall.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.result.R;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 行政区划数据 — 省市区三级级联选择
 * 数据源: Administrative-divisions-of-China (pca-code.json)
 */
@Slf4j
@RestController
@RequestMapping("/api/region")
public class RegionController {

    private List<?> regionTree = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            regionTree = mapper.readValue(
                    new ClassPathResource("regions.json").getInputStream(),
                    List.class);
            log.info("Loaded {} provinces", regionTree.size());
        } catch (Exception e) {
            log.warn("Failed to load regions.json: {}", e.getMessage());
        }
    }

    @GetMapping("/tree")
    public R<List<?>> tree() {
        return R.ok(regionTree);
    }
}
