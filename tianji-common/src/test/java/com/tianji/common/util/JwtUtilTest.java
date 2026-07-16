package com.tianji.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "my-test-secret-key-that-is-long-enough-for-hmac-sha";
    private static final long EXPIRATION = 3600000L; // 1 hour

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION);
    }

    @Test
    void shouldThrowWhenSecretIsNull() {
        assertThatThrownBy(() -> new JwtUtil(null, EXPIRATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    void shouldThrowWhenSecretIsBlank() {
        assertThatThrownBy(() -> new JwtUtil("   ", EXPIRATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    void shouldGenerateAndParseToken() {
        String token = jwtUtil.generateToken(1L, "testuser");

        Claims claims = jwtUtil.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("username", String.class)).isEqualTo("testuser");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void shouldGetUserIdFromToken() {
        String token = jwtUtil.generateToken(42L, "user42");

        Long userId = jwtUtil.getUserId(token);
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = jwtUtil.generateToken(1L, "testuser");
        String tampered = token.substring(0, token.length() - 1) + "X";

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldRejectExpiredToken() {
        // Create a JwtUtil with 1ms expiration for testing
        JwtUtil shortLivedJwt = new JwtUtil(SECRET, 1);
        String token = shortLivedJwt.generateToken(1L, "testuser");

        // Wait for token to expire
        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertThatThrownBy(() -> shortLivedJwt.parseToken(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        String differentSecret = "a-different-secret-key-that-is-also-long-enough";
        SecretKey otherKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));

        // Sign a token with a different key
        String foreignToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(otherKey)
                .compact();

        assertThatThrownBy(() -> jwtUtil.parseToken(foreignToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldGenerateTokensWithDifferentUsers() {
        String token1 = jwtUtil.generateToken(1L, "alice");
        String token2 = jwtUtil.generateToken(2L, "bob");

        assertThat(jwtUtil.getUserId(token1)).isEqualTo(1L);
        assertThat(jwtUtil.getUserId(token2)).isEqualTo(2L);
        assertThat(token1).isNotEqualTo(token2);
    }
}
