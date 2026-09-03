package com.careerplatform.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.enums.UserStatus;
import com.careerplatform.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserMapper appUserMapper;

    @Test
    @Transactional
    void registerShouldReturnCreatedSafeResponse() throws Exception {
        String username = uniqueUsername("http_success_");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, "123456")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @Transactional
    void registerShouldReturnConflictForDuplicateUsername() throws Exception {
        String username = uniqueUsername("http_duplicate_");
        String body = registerJson(username, "123456");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(" ", "123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("username: ")
                ))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerShouldRejectBlankPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueUsername("blank_password_"), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("password: ")
                ))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerShouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueUsername("short_password_"), "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("password: ")
                ))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void registerShouldRejectPasswordExceedingBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                uniqueUsername("long_utf8_password_"),
                                "密".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void loginShouldReturnTokenAndSafeUserResponse() throws Exception {
        String username = uniqueUsername("login_success_");
        String password = "correct-password";
        register(username, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @Transactional
    void loginShouldReturnSameUnauthorizedResponseForWrongPassword() throws Exception {
        String username = uniqueUsername("login_wrong_password_");
        register(username, "correct-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginShouldReturnSameUnauthorizedResponseForUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(uniqueUsername("login_unknown_"), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @Transactional
    void loginShouldRejectDisabledUserWithoutLeakingStatus() throws Exception {
        String username = uniqueUsername("login_disabled_");
        String password = "correct-password";
        register(username, password);
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<>();
        query.eq(AppUser::getUsername, username);
        AppUser user = appUserMapper.selectOne(query);
        user.setStatus(UserStatus.DISABLED);
        appUserMapper.updateById(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginShouldValidateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(" ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginShouldRejectPasswordExceedingBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(
                                uniqueUsername("long_utf8_login_"),
                                "密".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String registerJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private String loginJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, password)))
                .andExpect(status().isCreated());
    }
}
