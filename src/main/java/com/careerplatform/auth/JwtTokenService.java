package com.careerplatform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String username) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number userId = claims.get("userId", Number.class);
            String username = claims.getSubject();
            if (userId == null || username == null || username.isBlank()) {
                throw new UnauthorizedException("认证信息无效");
            }
            return new AuthenticatedUser(userId.longValue(), username);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("认证信息无效");
        }
    }
}
