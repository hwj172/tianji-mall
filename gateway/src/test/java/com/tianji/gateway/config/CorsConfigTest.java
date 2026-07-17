package com.tianji.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorsConfig 单元测试")
class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    @DisplayName("应创建 CorsWebFilter bean")
    void shouldCreateCorsWebFilter() {
        CorsWebFilter filter = corsConfig.corsWebFilter();
        assertThat(filter).isNotNull();
    }

    @Test
    @DisplayName("OPTIONS 预检请求不报错")
    void shouldNotErrorOnPreflight() {
        CorsWebFilter filter = corsConfig.corsWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://localhost/api/user/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
        );

        filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()).block();
    }

    @Test
    @DisplayName("GET 请求不报错")
    void shouldNotErrorOnGet() {
        CorsWebFilter filter = corsConfig.corsWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/product/list")
                        .header("Origin", "http://localhost:5173")
        );

        filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()).block();
    }

    @Test
    @DisplayName("无 Origin 头的请求不报错")
    void shouldNotErrorWithoutOrigin() {
        CorsWebFilter filter = corsConfig.corsWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/product/list")
        );

        filter.filter(exchange, e -> reactor.core.publisher.Mono.empty()).block();
    }
}
