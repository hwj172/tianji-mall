package com.tianji.user.config;

import com.tianji.common.interceptor.InternalApiInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置上传文件的静态资源映射和内部 API 鉴权。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${internal.token:}")
    private String internalToken;

    @Bean
    public FilterRegistrationBean<InternalApiInterceptor> internalApiFilter() {
        FilterRegistrationBean<InternalApiInterceptor> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiInterceptor(internalToken));
        bean.addUrlPatterns("/api/user/internal/*");
        return bean;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
