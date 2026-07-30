package com.tianji.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Gateway 业务扩展配置属性（Nacos Config 动态刷新）。
 * 注意：类名避免与 Spring Cloud Gateway 内置的 GatewayProperties 冲突。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.app")
public class GatewayAppProperties {
}
