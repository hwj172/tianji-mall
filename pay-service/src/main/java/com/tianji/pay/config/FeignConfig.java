package com.tianji.pay.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部调用配置 — 对内部端点自动添加 X-Internal-Token 请求头。
 */
@Configuration
public class FeignConfig {

    @Value("${internal.token:}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isEmpty()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }
}
