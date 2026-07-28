package com.tianji.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * User 服务业务配置属性（Nacos Config 动态刷新）。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "user")
public class UserServiceProperties {
}
