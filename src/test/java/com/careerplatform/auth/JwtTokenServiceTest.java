package com.careerplatform.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String TEST_SECRET =
            "test-only-jwt-secret-that-is-at-least-thirty-two-bytes-long";

    @Test
    void shouldCreateAndParseSignedToken() {
        JwtTokenService tokenService = new JwtTokenService(TEST_SECRET, 3600);

        String token = tokenService.createToken(42L, "alice");
        AuthenticatedUser user = tokenService.parseToken(token);

        assertEquals(42L, user.userId());
        assertEquals("alice", user.username());
    }

    @Test
    void shouldRejectTamperedSignature() {
        JwtTokenService tokenService = new JwtTokenService(TEST_SECRET, 3600);
        String token = tokenService.createToken(42L, "alice");
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThrows(UnauthorizedException.class, () -> tokenService.parseToken(tampered));
    }

    @Test
    void shouldRejectTamperedPayload() {
        JwtTokenService tokenService = new JwtTokenService(TEST_SECRET, 3600);
        String token = tokenService.createToken(42L, "alice");
        String[] parts = token.split("\\.");
        String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8
        ).replace("\"userId\":42", "\"userId\":43");
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThrows(
                UnauthorizedException.class,
                () -> tokenService.parseToken(String.join(".", parts))
        );
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtTokenService tokenService = new JwtTokenService(TEST_SECRET, -1);
        String token = tokenService.createToken(42L, "alice");

        assertThrows(UnauthorizedException.class, () -> tokenService.parseToken(token));
    }
}
