package com.tianji.gateway.config;

import com.alibaba.csp.sentinel.init.InitExecutor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 强制初始化 Sentinel 传输模块。
 * Gateway 使用 WebFlux 适配器，不会自动触发 InitExecutor，
 * 也不会将 Spring 配置映射为 Sentinel 系统属性，
 * 导致无法向 Dashboard 注册心跳和拉取规则。
 */
@Configuration
public class SentinelInitConfig {

    @Value("${spring.cloud.sentinel.transport.dashboard}")
    private String dashboard;

    @Value("${spring.cloud.sentinel.transport.port:8719}")
    private String port;

    @PostConstruct
    public void init() {
        System.setProperty("csp.sentinel.dashboard.server", dashboard);
        System.setProperty("csp.sentinel.api.port", port);
        InitExecutor.doInit();
    }
}
