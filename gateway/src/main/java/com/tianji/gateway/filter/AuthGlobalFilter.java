package com.tianji.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局鉴权过滤器。
 * 公开路径直接放行，内部路径检查内部 token，其余 API 路径验证 JWT。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 管理员路径前缀，需要 admin 角色 */
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    /** 商家路径前缀，需要 seller 或 admin 角色 */
    private static final String SELLER_PATH_PREFIX = "/api/seller/";

    /** 不需要鉴权的公开路径 */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/product",
            "/api/region",
            "/api/shop",
            "/api/group-buy",
            "/api/home",
            "/api/product/search/hot",
            "/api/pay/notify"
    );

    /** 内部服务调用路径，通过 X-Internal-Token 请求头鉴权 */
    private static final List<String> INTERNAL_PATHS = List.of(
            "/api/order/internal",
            "/api/cart/internal",
            "/api/product/internal",
            "/api/user/internal"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${internal.token}")
    private String internalToken;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 内部服务路径：检查 X-Internal-Token（必须先于公开路径检查——
        // /api/product/internal 是公开前缀 /api/product 的子路径，更具体的规则先匹配）
        if (isInternalPath(path)) {
            String token = exchange.getRequest().getHeaders().getFirst("X-Internal-Token");
            if (internalToken.equals(token)) {
                return chain.filter(exchange);
            }
            log.warn("内部接口 token 无效: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 公开路径直接放行
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // 非 API 路径放行
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        // API 路径：验证 JWT（签名 + 过期）
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("未授权访问: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // 将 userId 和 role 写入请求头，下游服务可直接使用
            String userId = claims.getSubject();
            String role = (String) claims.get("role");
            if (role == null || role.isEmpty()) {
                role = "user";
            }
            exchange.getRequest().mutate().header("X-User-Id", userId);
            exchange.getRequest().mutate().header("X-User-Role", role);

            // admin 路径：必须有 admin 角色，否则返回 403
            if (path.startsWith(ADMIN_PATH_PREFIX) && !"admin".equals(role)) {
                log.warn("非管理员尝试访问 admin 路径: {}, role={}", path, role);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // seller 路径：必须有 seller 或 admin 角色，否则返回 403
            if (path.startsWith(SELLER_PATH_PREFIX) && !"seller".equals(role) && !"admin".equals(role)) {
                log.warn("非商家尝试访问 seller 路径: {}, role={}", path, role);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (JwtException e) {
            log.warn("JWT 无效: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isInternalPath(String path) {
        return INTERNAL_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
