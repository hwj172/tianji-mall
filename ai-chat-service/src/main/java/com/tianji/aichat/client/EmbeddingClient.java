package com.tianji.aichat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow Embedding 客户端（OpenAI 兼容 /v1/embeddings）。
 * 失败时抛出 RuntimeException，由调用方（VectorSearchService）统一降级处理。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate;

    @Value("${siliconflow.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${siliconflow.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${siliconflow.embedding-model:BAAI/bge-large-zh-v1.5}")
    private String model;

    public EmbeddingClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 文本 → 向量（BAAI/bge-large-zh-v1.5 输出 1024 维）
     */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/embeddings", new HttpEntity<>(body, headers), Map.class);

        Map<String, Object> respBody = response.getBody();
        if (respBody == null) {
            throw new IllegalStateException("Embedding API 返回空响应");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("Embedding API 返回空 data: " + respBody);
        }
        List<Number> embedding = (List<Number>) data.get(0).get("embedding");
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("Embedding API 返回空向量");
        }

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }
}
