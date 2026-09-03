# User Registration HTTP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the existing registration business operation as a validated, safe, real-MySQL HTTP endpoint that returns 201 and structured 400/409 errors.

**Architecture:** Keep registration inside the existing user module: a request DTO enters `AuthController`, `UserService` owns the transaction and persistence semantics, and a dedicated response DTO prevents password-hash serialization. Shared exception advice maps domain and validation failures to a minimal `ApiErrorResponse`.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring MVC/MockMvc, Bean Validation, MyBatis-Plus 3.5.17, MySQL, JUnit 5, Mockito, Maven.

## Global Constraints

- Registration success is `201 Created` and does not require a `Location` header.
- `RegisterResponse` contains only `id`, `username`, and `status`; it must not expose `passwordHash`.
- `username` is nonblank and at most 50 characters.
- `password` is nonblank and 6-72 characters.
- `ApiErrorResponse` contains `code`, `message`, and an `Instant timestamp` serialized by Jackson as ISO-8601.
- Validation uses only the first field error, formatted as `<field>: <default validation message>`.
- Keep the database UNIQUE constraint and convert insert-time `DuplicateKeyException` to `UsernameAlreadyExistsException`.
- Use real MySQL for HTTP integration tests and transaction rollback for database-writing tests.
- Do not add Lombok, full Spring Security, login, JWT, unrelated modules, or broad refactoring.
- Do not commit or push.

---

### Task 1: Harden the Service Transaction and Duplicate Handling

**Files:**
- Create: `src/test/java/com/careerplatform/user/service/UserServiceTest.java`
- Modify: `src/test/java/com/careerplatform/CareerPlatformApplicationTests.java`
- Modify: `src/main/java/com/careerplatform/user/service/UserService.java`

**Interfaces:**
- Consumes: `AppUserMapper.selectCount(...)`, `AppUserMapper.insert(AppUser)`, `PasswordEncoder.encode(CharSequence)`.
- Produces: transactional `AppUser UserService.register(String username, String password)` that converts both pre-check and insert-time duplicates to `UsernameAlreadyExistsException`.

- [ ] **Step 1: Write the failing duplicate-key translation unit test**

```java
package com.careerplatform.user.service;

import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import com.careerplatform.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserMapper appUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerShouldConvertDuplicateKeyException() {
        when(appUserMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        doThrow(new DuplicateKeyException("duplicate username"))
                .when(appUserMapper).insert(any(AppUser.class));

        UsernameAlreadyExistsException exception = assertThrows(
                UsernameAlreadyExistsException.class,
                () -> userService.register("race_user", "123456")
        );

        assertEquals("用户名已存在", exception.getMessage());
    }
}
```

- [ ] **Step 2: Run the focused unit test and verify RED**

Run: `./mvnw.cmd -Dtest=UserServiceTest test`

Expected: FAIL because `DuplicateKeyException` escapes instead of becoming `UsernameAlreadyExistsException`.

- [ ] **Step 3: Add the transaction boundary and minimal exception conversion**

Add imports and change `register` in `UserService.java`:

```java
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public AppUser register(String username, String password) {
    if (usernameExists(username)) {
        throw new UsernameAlreadyExistsException("用户名已存在");
    }
    String passwordHash = passwordEncoder.encode(password);
    AppUser user = new AppUser();
    user.setUsername(username);
    user.setPasswordHash(passwordHash);
    user.setStatus(UserStatus.ACTIVE);
    try {
        appUserMapper.insert(user);
    } catch (DuplicateKeyException exception) {
        throw new UsernameAlreadyExistsException("用户名已存在");
    }
    return user;
}
```

- [ ] **Step 4: Verify GREEN for duplicate-key conversion**

Run: `./mvnw.cmd -Dtest=UserServiceTest test`

Expected: PASS.

- [ ] **Step 5: Complete the existing duplicate registration integration test**

Add imports and replace the empty method body in `CareerPlatformApplicationTests.java`:

```java
import com.careerplatform.user.exception.UsernameAlreadyExistsException;
import java.util.UUID;

@Test
@Transactional
void registerShouldRejectDuplicateUsername() {
    String username = "service_" + UUID.randomUUID().toString().replace("-", "");
    userService.register(username, "123456");

    assertThrows(
            UsernameAlreadyExistsException.class,
            () -> userService.register(username, "123456")
    );
}
```

- [ ] **Step 6: Run the focused service tests**

Run: `./mvnw.cmd -Dtest=UserServiceTest,CareerPlatformApplicationTests#registerShouldRejectDuplicateUsername test`

Expected: PASS when `DB_PASSWORD` provides access to the configured MySQL database; otherwise report the authentication failure without changing datasource secrets.

---

### Task 2: Add the Safe 201 Registration Endpoint

**Files:**
- Create: `src/test/java/com/careerplatform/user/controller/AuthControllerIntegrationTests.java`
- Create: `src/main/java/com/careerplatform/user/dto/RegisterRequest.java`
- Create: `src/main/java/com/careerplatform/user/dto/RegisterResponse.java`
- Create: `src/main/java/com/careerplatform/user/controller/AuthController.java`

**Interfaces:**
- Consumes: `AppUser UserService.register(String username, String password)`.
- Produces: `POST /api/v1/auth/register` returning `ResponseEntity<RegisterResponse>` with status 201.

- [ ] **Step 1: Write the failing successful-registration HTTP test**

```java
package com.careerplatform.user.controller;

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
@Transactional
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
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

    private String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String registerJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }
}
```

- [ ] **Step 2: Run the HTTP test and verify RED**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldReturnCreatedSafeResponse test`

Expected: FAIL with HTTP 404 because the registration endpoint does not exist.

- [ ] **Step 3: Add minimal request and response DTOs**

Create `RegisterRequest.java`:

```java
package com.careerplatform.user.dto;

public class RegisterRequest {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
```

Create `RegisterResponse.java`:

```java
package com.careerplatform.user.dto;

import com.careerplatform.user.enums.UserStatus;

public class RegisterResponse {
    private final Long id;
    private final String username;
    private final UserStatus status;

    public RegisterResponse(Long id, String username, UserStatus status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public UserStatus getStatus() { return status; }
}
```

- [ ] **Step 4: Add the minimal Controller**

Create `AuthController.java`:

```java
package com.careerplatform.user.controller;

import com.careerplatform.user.dto.RegisterRequest;
import com.careerplatform.user.dto.RegisterResponse;
import com.careerplatform.user.entity.AppUser;
import com.careerplatform.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = userService.register(request.getUsername(), request.getPassword());
        RegisterResponse response = new RegisterResponse(user.getId(), user.getUsername(), user.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

- [ ] **Step 5: Re-run the successful HTTP test and verify GREEN**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldReturnCreatedSafeResponse test`

Expected: PASS with real MySQL and rollback.

---

### Task 3: Enforce Request Validation

**Files:**
- Modify: `src/test/java/com/careerplatform/user/controller/AuthControllerIntegrationTests.java`
- Modify: `src/main/java/com/careerplatform/user/dto/RegisterRequest.java`
- Create: `src/main/java/com/careerplatform/common/exception/ApiErrorResponse.java`
- Create: `src/main/java/com/careerplatform/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: validation failures as HTTP 400 with `ApiErrorResponse("VALIDATION_ERROR", message, timestamp)`.

- [ ] **Step 1: Add failing blank and length validation tests**

Add these methods to `AuthControllerIntegrationTests`:

```java
@Test
void registerShouldRejectBlankUsername() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson(" ", "123456")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("username: ")))
            .andExpect(jsonPath("$.timestamp").exists());
}

@Test
void registerShouldRejectBlankPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson(uniqueUsername("blank_password_"), "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("password: ")))
            .andExpect(jsonPath("$.timestamp").exists());
}

@Test
void registerShouldRejectShortPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson(uniqueUsername("short_password_"), "12345")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("password: ")))
            .andExpect(jsonPath("$.timestamp").exists());
}
```

- [ ] **Step 2: Run validation tests and verify RED**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldRejectBlankUsername+registerShouldRejectBlankPassword+registerShouldRejectShortPassword test`

Expected: FAIL because the DTO has no constraints and there is no structured validation handler.

- [ ] **Step 3: Add exact Bean Validation constraints**

Add imports and annotations in `RegisterRequest.java`:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@NotBlank(message = "用户名不能为空")
@Size(max = 50, message = "用户名长度不能超过50个字符")
private String username;

@NotBlank(message = "密码不能为空")
@Size(min = 6, max = 72, message = "密码长度必须在6到72个字符之间")
private String password;
```

- [ ] **Step 4: Add the minimal error DTO and validation advice**

Create `ApiErrorResponse.java`:

```java
package com.careerplatform.common.exception;

import java.time.Instant;

public class ApiErrorResponse {
    private final String code;
    private final String message;
    private final Instant timestamp;

    public ApiErrorResponse(String code, String message, Instant timestamp) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
```

Create the initial `GlobalExceptionHandler.java`:

```java
package com.careerplatform.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().getFirst();
        String message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        ApiErrorResponse response = new ApiErrorResponse("VALIDATION_ERROR", message, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
```

- [ ] **Step 5: Re-run validation tests and verify GREEN**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldRejectBlankUsername+registerShouldRejectBlankPassword+registerShouldRejectShortPassword test`

Expected: PASS.

---

### Task 4: Map Duplicate Usernames to Structured HTTP 409

**Files:**
- Modify: `src/test/java/com/careerplatform/user/controller/AuthControllerIntegrationTests.java`
- Modify: `src/main/java/com/careerplatform/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `UsernameAlreadyExistsException` from `UserService`.
- Produces: HTTP 409 with `ApiErrorResponse("USERNAME_ALREADY_EXISTS", "用户名已存在", timestamp)`.

- [ ] **Step 1: Write the failing duplicate-username HTTP test**

Add to `AuthControllerIntegrationTests`:

```java
@Test
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
```

- [ ] **Step 2: Run the duplicate HTTP test and verify RED**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldReturnConflictForDuplicateUsername test`

Expected: FAIL because the domain exception is not mapped to HTTP 409.

- [ ] **Step 3: Add the minimal domain exception handler**

Add this import and method to `GlobalExceptionHandler`:

```java
import com.careerplatform.user.exception.UsernameAlreadyExistsException;

@ExceptionHandler(UsernameAlreadyExistsException.class)
public ResponseEntity<ApiErrorResponse> handleUsernameAlreadyExists(
        UsernameAlreadyExistsException exception) {
    ApiErrorResponse response = new ApiErrorResponse(
            "USERNAME_ALREADY_EXISTS",
            exception.getMessage(),
            Instant.now()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
}
```

- [ ] **Step 4: Re-run the duplicate HTTP test and verify GREEN**

Run: `./mvnw.cmd -Dtest=AuthControllerIntegrationTests#registerShouldReturnConflictForDuplicateUsername test`

Expected: PASS.

- [ ] **Step 5: Run all registration-related tests**

Run: `./mvnw.cmd -Dtest=UserServiceTest,CareerPlatformApplicationTests,AuthControllerIntegrationTests test`

Expected: PASS with available MySQL credentials and no leftover test rows because database-writing integration tests roll back.

---

### Task 5: Final Verification and Scope Audit

**Files:**
- Verify all modified and created files; do not add implementation outside the frozen design.

**Interfaces:**
- Produces: evidence for the final GO/NO-GO report.

- [ ] **Step 1: Compile main code**

Run: `./mvnw.cmd -DskipTests compile`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run the complete test suite**

Run: `./mvnw.cmd test`

Expected: BUILD SUCCESS when `DB_PASSWORD` grants MySQL access. If authentication fails, preserve the exact failure as a reported limitation.

- [ ] **Step 3: Check whitespace errors**

Run: `git diff --check`

Expected: no output and exit code 0.

- [ ] **Step 4: Capture final status**

Run: `git status --short`

Expected: only the user's pre-existing work plus registration-slice files and the approved design/plan documents; no commits or pushes.

- [ ] **Step 5: Review the final diff for scope and secrets**

Run: `git diff -- . ':!target'` and `git diff --cached -- . ':!target'`.

Expected: no login/JWT/other-module implementation, no real database password, and no removal of the UNIQUE constraint.
