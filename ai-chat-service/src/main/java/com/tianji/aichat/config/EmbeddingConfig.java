package com.tianji.aichat.config;

import com.openai.client.OpenAIClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    private final OpenAIClient openAiClient;

    public EmbeddingConfig(OpenAIClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    @Bean
    @ConditionalOnMissingBean(OpenAiEmbeddingModel.class)
    public EmbeddingModel embeddingModel() {
        return new OpenAiEmbeddingModel(openAiClient);
    }
}
