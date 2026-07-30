package com.tianji.mall.config;

import com.tianji.common.interceptor.InternalApiInterceptor;
import com.tianji.mall.interceptor.AdminInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${internal.token:}")
    private String internalToken;

    @Bean
    public FilterRegistrationBean<InternalApiInterceptor> internalApiFilter() {
        FilterRegistrationBean<InternalApiInterceptor> bean = new FilterRegistrationBean<>();
        bean.setFilter(new InternalApiInterceptor(internalToken));
        bean.addUrlPatterns("/api/order/internal/*", "/api/cart/internal/*", "/api/product/internal/*");
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
