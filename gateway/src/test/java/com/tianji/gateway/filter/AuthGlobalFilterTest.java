package com.tianji.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthGlobalFilter 单元测试")
class AuthGlobalFilterTest {

    private static final String SECRET = "test-secret-key-32-chars-minimum!!";
    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private AuthGlobalFilter filter;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
        when(response.setComplete()).thenReturn(Mono.empty());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    /**
     * 创建 ServerWebExchange，使用字段级 response mock，确保后续 verify 有效。
     */
    private ServerWebExchange createExchange(String path) {
        return createExchange(path, new HttpHeaders());
    }

    private ServerWebExchange createExchange(String path, HttpHeaders headers) {
        ServerHttpRequest req = mock(ServerHttpRequest.class);
        when(req.getURI()).thenReturn(URI.create(path));
        when(req.getHeaders()).thenReturn(headers);

        // request mutate 用于 X-User-Id 注入
        ServerHttpRequest.Builder builder = mock(ServerHttpRequest.Builder.class);
        when(req.mutate()).thenReturn(builder);
        when(builder.header(anyString(), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(req);

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(req);
        when(exchange.getResponse()).thenReturn(response); // 使用字段 mock
        return exchange;
    }

    private String createValidToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    private String createTokenWithRole(String userId, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    private String createExpiredToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(KEY)
                .compact();
    }

    // ==================== 公开路径 ====================

    @Nested
    @DisplayName("公开路径 — 直接放行，无需鉴权")
    class PublicPaths {

        @Test
        @DisplayName("/api/user/login 无需鉴权直接放行")
        void shouldPassThroughLogin() {
            ServerWebExchange exchange = createExchange("/api/user/login");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/user/register 无需鉴权直接放行")
        void shouldPassThroughRegister() {
            ServerWebExchange exchange = createExchange("/api/user/register");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/product/list 无需鉴权直接放行")
        void shouldPassThroughProductList() {
            ServerWebExchange exchange = createExchange("/api/product/list");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/product/1 无需鉴权直接放行")
        void shouldPassThroughProductDetail() {
            ServerWebExchange exchange = createExchange("/api/product/1");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/pay/notify 无需鉴权直接放行")
        void shouldPassThroughPayNotify() {
            ServerWebExchange exchange = createExchange("/api/pay/notify");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/shop/1 无需鉴权直接放行")
        void shouldPassThroughShopDetail() {
            ServerWebExchange exchange = createExchange("/api/shop/1");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/region/tree 无需鉴权直接放行")
        void shouldPassThroughRegionTree() {
            ServerWebExchange exchange = createExchange("/api/region/tree");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("公开路径带 Authorization 头也放行（不报错）")
        void shouldPassThroughWithAuthHeader() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer some-token");
            ServerWebExchange exchange = createExchange("/api/user/login", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }
    }

    // ==================== 内部路径 ====================

    @Nested
    @DisplayName("内部路径 — X-Internal-Token 鉴权")
    class InternalPaths {

        @Test
        @DisplayName("有效 X-Internal-Token 放行")
        void shouldPassThroughWithValidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", INTERNAL_TOKEN);
            ServerWebExchange exchange = createExchange("/api/order/internal", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("无效 X-Internal-Token 返回 401")
        void shouldReturn401WithInvalidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", "wrong-token");
            ServerWebExchange exchange = createExchange("/api/order/internal", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("缺少 X-Internal-Token 返回 401")
        void shouldReturn401WithMissingToken() {
            ServerWebExchange exchange = createExchange("/api/cart/internal");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================== 非 API 路径 ====================

    @Nested
    @DisplayName("非 API 路径 — 直接放行")
    class NonApiPaths {

        @Test
        @DisplayName("/health 直接放行")
        void shouldPassThroughHealth() {
            ServerWebExchange exchange = createExchange("/health");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("/actuator/health 直接放行")
        void shouldPassThroughActuator() {
            ServerWebExchange exchange = createExchange("/actuator/health");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("根路径 / 放行")
        void shouldPassThroughRoot() {
            ServerWebExchange exchange = createExchange("/");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }
    }

    // ==================== JWT 鉴权 ====================

    @Nested
    @DisplayName("API 路径 — JWT Bearer Token 鉴权")
    class JwtAuth {

        @Test
        @DisplayName("有效 JWT 放行并注入 X-User-Id 和 X-User-Role 请求头")
        void shouldPassThroughWithValidJwt() {
            String token = createValidToken("12345");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/cart/list", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());

            // 验证 X-User-Id 和 X-User-Role 注入
            ServerHttpRequest req = exchange.getRequest();
            ServerHttpRequest.Builder builder = req.mutate();
            verify(builder).header("X-User-Id", "12345");
            verify(builder).header("X-User-Role", "user");
        }

        @Test
        @DisplayName("缺少 Authorization 头返回 401")
        void shouldReturn401WithoutAuthHeader() {
            ServerWebExchange exchange = createExchange("/api/user/info");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Authorization 头格式错误（非 Bearer）返回 401")
        void shouldReturn401WithNonBearerAuth() {
            String token = createValidToken("12345");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + token);
            ServerWebExchange exchange = createExchange("/api/user/info", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("JWT 过期返回 401")
        void shouldReturn401WithExpiredJwt() {
            String token = createExpiredToken("12345");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/user/info", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("JWT 签名无效返回 401")
        void shouldReturn401WithInvalidSignature() {
            // 用不同密钥签名
            SecretKey wrongKey = Jwts.SIG.HS256.key().build();
            String token = Jwts.builder()
                    .subject("12345")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(wrongKey)
                    .compact();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/user/info", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("JWT 格式非法（乱码字符串）返回 401")
        void shouldReturn401WithMalformedJwt() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer not.a.valid.jwt.token");
            ServerWebExchange exchange = createExchange("/api/user/info", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("JWT 无 subject 仍放行（X-User-Id 为 null）")
        void shouldPassThroughWithMissingSubject() {
            String token = Jwts.builder()
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600_000))
                    .signWith(KEY)
                    .compact();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/user/info", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("内部路径前缀匹配：/api/order/internal/detail 也匹配")
        void shouldMatchInternalPathWithSuffix() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", INTERNAL_TOKEN);
            ServerWebExchange exchange = createExchange("/api/order/internal/detail", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("公开路径前缀匹配：/api/product/detail 也匹配")
        void shouldMatchPublicPathWithSuffix() {
            ServerWebExchange exchange = createExchange("/api/product/detail");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("GET /api/order/list 需 JWT 鉴权")
        void shouldRequireJwtForOrderList() {
            ServerWebExchange exchange = createExchange("/api/order/list");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("filter 优先级为 -100")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(-100);
        }
    }

    // ==================== 商品内部路径（向量回填） ====================

    @Nested
    @DisplayName("/api/product/internal — 内部 token 鉴权（先于公开前缀匹配）")
    class ProductInternalPaths {

        @Test
        @DisplayName("/api/product/internal/sync-vectors 无 token 返回 401")
        void shouldRejectProductInternalWithoutToken() {
            ServerWebExchange exchange = createExchange("/api/product/internal/sync-vectors");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("/api/product/internal/sync-vectors 携带正确 token 放行")
        void shouldPassProductInternalWithValidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Internal-Token", INTERNAL_TOKEN);
            ServerWebExchange exchange = createExchange("/api/product/internal/sync-vectors", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/product/list 仍是公开路径（顺序调整回归保护）")
        void shouldKeepProductListPublic() {
            ServerWebExchange exchange = createExchange("/api/product/list");

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }
    }

    // ==================== Seller 路径鉴权 ====================

    @Nested
    @DisplayName("/api/seller/** — seller 角色鉴权")
    class SellerPaths {

        @Test
        @DisplayName("seller 角色访问 /api/seller/shop 放行")
        void shouldPassThroughWithSellerRole() {
            String token = createTokenWithRole("2", "seller");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/seller/shop", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("admin 角色也能访问 /api/seller/shop")
        void shouldPassThroughWithAdminRole() {
            String token = createTokenWithRole("1", "admin");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/seller/shop", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("普通 user 角色访问 /api/seller/shop 返回 403")
        void shouldReturn403WithUserRole() {
            String token = createTokenWithRole("2", "user");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/seller/shop", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }
    }

    // ==================== Admin 路径鉴权 ====================

    @Nested
    @DisplayName("/api/admin/** — admin 角色鉴权")
    class AdminPaths {

        @Test
        @DisplayName("admin 角色访问 /api/admin/product 放行并注入 X-User-Role")
        void shouldPassThroughWithAdminRole() {
            String token = createTokenWithRole("1", "admin");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/admin/product", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
            // 验证 X-User-Id 和 X-User-Role 注入
            ServerHttpRequest req = exchange.getRequest();
            ServerHttpRequest.Builder builder = req.mutate();
            verify(builder).header("X-User-Id", "1");
            verify(builder).header("X-User-Role", "admin");
        }

        @Test
        @DisplayName("普通 user 角色访问 /api/admin/product 返回 403")
        void shouldReturn403WithUserRole() {
            String token = createTokenWithRole("1", "user");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/admin/product", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("无 role claim 的 JWT 访问 /api/admin/product 返回 403（默认 role=user）")
        void shouldReturn403WithNoRoleClaim() {
            String token = createValidToken("1");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/admin/product", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("/api/admin/category 同属 admin 路径，user 角色返回 403")
        void shouldReturn403ForAdminCategory() {
            String token = createTokenWithRole("1", "user");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ServerWebExchange exchange = createExchange("/api/admin/category", headers);

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("/api/pay/internal — 内部 token 鉴权")
    class PayInternalPaths {

        @Test
        @DisplayName("/api/pay/internal/refund 携带正确 token 放行")
        void shouldPassPayInternalWithValidToken() {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Internal-Token", INTERNAL_TOKEN);
            ServerWebExchange exchange = createExchange("/api/pay/internal/refund", headers);

            filter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("/api/pay/internal/refund 无 token 返回 401")
        void shouldRejectPayInternalWithoutToken() {
            ServerWebExchange exchange = createExchange("/api/pay/internal/refund");

            filter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }

    @Test
    @DisplayName("internal.token 未配置时应拒绝所有内部请求（fail-closed）")
    void shouldRejectInternalRequestsWhenTokenNotConfigured() {
        ReflectionTestUtils.setField(filter, "internalToken", "");

        ServerWebExchange exchange = createExchange("/api/order/internal");
        filter.filter(exchange, chain);

        verify(response).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(chain, never()).filter(any());

        // 恢复
        ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
    }
}
