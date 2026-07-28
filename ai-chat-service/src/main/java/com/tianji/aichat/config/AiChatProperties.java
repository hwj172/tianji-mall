package com.tianji.aichat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * AI 导购配置属性（Nacos Config 动态刷新）。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "ai")
public class AiChatProperties {
}
