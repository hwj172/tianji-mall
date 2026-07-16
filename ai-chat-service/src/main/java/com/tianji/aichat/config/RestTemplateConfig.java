package com.tianji.aichat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);   // 连接超时 30s
        factory.setReadTimeout(120_000);     // 读取超时 120s（DeepSeek 多轮工具调用可能较慢）
        return new RestTemplate(factory);
    }
}
