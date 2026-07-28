package com.tianji.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * MCP 服务配置属性（Nacos Config 动态刷新）。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {
}
