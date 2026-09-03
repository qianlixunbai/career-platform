package com.careerplatform.auth;

import com.careerplatform.CareerPlatformApplication;
import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.enums.UserStatus;
import com.careerplatform.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CareerPlatformApplication.class)
@AutoConfigureMockMvc
@Import(AuthenticationIntegrationTests.ProtectedTestController.class)
class AuthenticationIntegrationTests {

    private static final String TEST_SECRET =
            "test-only-jwt-secret-that-is-at-least-thirty-two-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserMapper appUserMapper;

    @Test
    void protectedEndpointShouldRejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/current-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpointShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpointShouldRejectInvalidBearerFormats() throws Exception {
        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Basic credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Bearer   "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointShouldRejectExpiredToken() throws Exception {
        JwtTokenService expiredTokenService = new JwtTokenService(TEST_SECRET, -1);
        String expiredToken = expiredTokenService.createToken(42L, "alice");

        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void protectedEndpointShouldReceiveCurrentUserIdFromValidToken() throws Exception {
        String username = "auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";

        String registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = registration.replaceAll(".*\"id\":(\\d+).*", "$1");

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = login.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(Long.parseLong(userId)));
    }

    @Test
    @Transactional
    void protectedEndpointShouldRejectTokenAfterUserIsDisabled() throws Exception {
        String username = "disabled_token_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String password = "correct-password";

        String registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long userId = Long.parseLong(registration.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = login.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        AppUser user = appUserMapper.selectById(userId);
        user.setStatus(UserStatus.DISABLED);
        appUserMapper.updateById(user);

        mockMvc.perform(get("/api/v1/test/current-user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private String authJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/test/current-user")
        CurrentUserResponse currentUser(@CurrentUserId Long userId) {
            return new CurrentUserResponse(userId);
        }
    }

    record CurrentUserResponse(Long userId) {
    }
}
