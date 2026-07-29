package com.tianji.pay.config;

import com.tianji.common.interceptor.InternalApiInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部 API 鉴权 + 静态资源配置。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${internal.token:}")
    private String internalToken;

    @Bean
    public FilterRegistrationBean<InternalApiInterceptor> internalApiFilter() {
        FilterRegistrationBean<InternalApiInterceptor> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiInterceptor(internalToken));
        bean.addUrlPatterns("/api/pay/internal/*");
        return bean;
    }
}
