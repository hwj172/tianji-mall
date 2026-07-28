package com.tianji.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 商城业务配置属性（Nacos Config 动态刷新）。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "mall")
public class MallProperties {
}
