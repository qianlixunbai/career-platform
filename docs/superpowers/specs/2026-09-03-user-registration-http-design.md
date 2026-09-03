# User Registration HTTP Vertical Slice Design

## Goal

Complete the existing user registration work as a real HTTP vertical slice that can be reused as the development pattern for later modules. The scope is limited to registration and does not include login, JWT, full Spring Security, or other business modules.

## Confirmed HTTP Contract

- Endpoint: `POST /api/v1/auth/register`
- Request JSON fields: `username`, `password`
- Successful status: `201 Created`
- No `Location` response header is required in the first version.
- Successful response fields: `id`, `username`, `status`
- The response type must not contain `passwordHash`.
- Duplicate usernames return `409 Conflict`.
- Invalid request fields return `400 Bad Request`.

## Components and Responsibilities

### RegisterRequest

Create `com.careerplatform.user.dto.RegisterRequest` as a regular Java class without Lombok.

- `username`: `@NotBlank`, `@Size(max = 50)`
- `password`: `@NotBlank`, `@Size(min = 6, max = 72)`

The username limit matches `app_user.username VARCHAR(50)`. The password constraint is intentionally limited to a simple first-version length rule rather than a password-strength system.

### RegisterResponse

Create `com.careerplatform.user.dto.RegisterResponse` as a dedicated safe response DTO containing only:

- `Long id`
- `String username`
- `UserStatus status`

The DTO has no `passwordHash` property, preventing accidental serialization of the stored hash.

### AuthController

Create `com.careerplatform.user.controller.AuthController`.

- Accept `RegisterRequest` through `@Valid @RequestBody`.
- Delegate registration to the existing `UserService.register(String username, String password)` method.
- Map the returned `AppUser` to `RegisterResponse`.
- Return `201 Created` without adding a `Location` header.
- Contain no registration business logic.

### UserService

Keep the existing registration semantics and add `@Transactional` to `register` as the business transaction boundary.

The flow remains:

1. Check whether the username already exists.
2. Throw `UsernameAlreadyExistsException` when the pre-check finds an existing user.
3. Encode the password through the existing `PasswordEncoder` bean.
4. Create an active `AppUser` and insert it through `AppUserMapper`.
5. Return the inserted user.

The database UNIQUE constraint remains the final concurrency constraint. If `appUserMapper.insert()` throws `DuplicateKeyException`, convert it to the existing `UsernameAlreadyExistsException`. No multithreaded race test or more complex concurrency design is included.

## Request and Response Flow

```text
POST /api/v1/auth/register
  -> RegisterRequest validation
  -> AuthController
  -> UserService.register()
  -> AppUserMapper.insert()
  -> MySQL app_user
  -> RegisterResponse
  -> HTTP 201 Created
```

## Error Handling

Create shared error types under `com.careerplatform.common.exception`.

### ApiErrorResponse

Keep the response deliberately small:

- `String code`
- `String message`
- `Instant timestamp`

Jackson must serialize `timestamp` as a standard ISO-8601 value.

### GlobalExceptionHandler

Handle at least:

- `UsernameAlreadyExistsException`: return `409 Conflict` with code `USERNAME_ALREADY_EXISTS` and the exception message.
- `MethodArgumentNotValidException`: return `400 Bad Request` with code `VALIDATION_ERROR`.

For validation failures, use the first concrete field error and format the message as `<field>: <default validation message>`. Do not introduce a nested or multi-error response structure in this version.

## Testing Strategy

### Existing MySQL Integration Tests

Preserve the existing tests in `CareerPlatformApplicationTests` and complete `registerShouldRejectDuplicateUsername()`:

1. Register a user successfully.
2. Register the same username again.
3. Assert `UsernameAlreadyExistsException`.

The test remains transactional so its database writes roll back.

### HTTP Vertical Integration Tests

Create `AuthControllerIntegrationTests` using:

- `@SpringBootTest`
- `@AutoConfigureMockMvc`
- `MockMvc`
- real MySQL configuration
- `@Transactional` rollback for database-writing tests

Cover these observable behaviors:

1. Valid registration returns `201 Created` with `id`, `username`, and `status`.
2. The successful response has no `passwordHash` field.
3. Registering the same username twice returns `409 Conflict` with a structured error response.
4. A blank username returns `400 Bad Request`.
5. A blank password or a password outside the 6-72 character range returns `400 Bad Request`.

### Duplicate-Key Translation Unit Test

Add a focused `UserService` unit test using Mockito already supplied by the current test starter:

1. Make the username pre-check report no existing user.
2. Make `appUserMapper.insert()` throw `DuplicateKeyException`.
3. Assert conversion to `UsernameAlreadyExistsException`.

This test covers the database-race fallback branch without adding a multithreaded test or another dependency.

## Verification

After implementation, run:

1. Maven compilation.
2. Relevant service and HTTP tests.
3. The complete Maven test suite when the local `DB_PASSWORD` environment allows it.
4. `git diff --check`.
5. `git status --short`.

If MySQL authentication fails because `DB_PASSWORD` is unavailable, report that exact limitation. Do not place a real password in `application.properties` or any repository file.

## Scope Boundaries

- Do not use Lombok.
- Do not add full Spring Security.
- Do not add login or JWT behavior.
- Do not remove or weaken the database UNIQUE constraint.
- Do not create unrelated modules or perform broad package refactoring.
- Do not commit or push.
