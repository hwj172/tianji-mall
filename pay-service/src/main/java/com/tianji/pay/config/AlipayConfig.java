package com.tianji.pay.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlipayConfig {

    @Bean
    public AlipayClient alipayClient(@Value("${alipay.gateway-url}") String gatewayUrl,
                                     @Value("${alipay.app-id}") String appId,
                                     @Value("${alipay.private-key}") String privateKey,
                                     @Value("${alipay.alipay-public-key}") String alipayPublicKey) {
        return new DefaultAlipayClient(gatewayUrl, appId, privateKey,
                "json", "UTF-8", alipayPublicKey, "RSA2");
    }
}
