package com.tianji.aichat.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingClientTest {

    @Mock
    private RestTemplate restTemplate;

    private EmbeddingClient embeddingClient;

    @BeforeEach
    void setUp() {
        embeddingClient = new EmbeddingClient(restTemplate);
        ReflectionTestUtils.setField(embeddingClient, "apiKey", "test-key");
        ReflectionTestUtils.setField(embeddingClient, "baseUrl", "https://api.test.com/v1");
        ReflectionTestUtils.setField(embeddingClient, "model", "BAAI/bge-large-zh-v1.5");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldParseEmbeddingFromResponse() {
        Map<String, Object> body = Map.of(
                "data", List.of(Map.of("embedding", List.of(0.1, 0.2, 0.3), "index", 0)),
                "model", "BAAI/bge-large-zh-v1.5");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));

        float[] vector = embeddingClient.embed("拍照好的手机");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldThrowWhenDataMissing() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(Map.of("error", "invalid key")));

        assertThatThrownBy(() -> embeddingClient.embed("手机"))
                .isInstanceOf(IllegalStateException.class);
    }
}
