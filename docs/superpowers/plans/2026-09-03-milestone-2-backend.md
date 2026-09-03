# Milestone 2 Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or inline TDD. The user's no-commit rule overrides all commit steps.

**Goal:** 完成注册验收、最小登录/JWT/currentUserId、共享基础档案、职业探索后端，以及真实 MySQL 集成验收。

**Architecture:** 继续 `Controller -> DTO -> Service -> Mapper -> MySQL`。认证由无状态 JWT、MVC `HandlerInterceptor`、请求属性和 `@CurrentUserId` 参数解析器组成；私有资源查询在 Service/Mapper 层同时携带 `id` 与 `currentUserId`，父子资源先验证父级归属。

**Tech Stack:** Java 21、Spring Boot 3.5.14、MyBatis-Plus 3.5.17、MySQL、Bean Validation、BCrypt、JJWT 0.13.0、MockMvc、JUnit 5。

## Global Constraints

- 不使用 Lombok、H2、Spring AI、RBAC、OAuth、Refresh Token 或禁区模块。
- 不 commit、不 push、不覆盖用户已有未提交改动。
- `JWT_SECRET` 仅来自配置/环境；测试使用专用 Secret。
- HTTP 不返回 Entity；绝不泄露 `passwordHash`。
- 私有资源归属只能由合法 Bearer Token 得到的 `currentUserId` 决定。
- 他人资源统一按 `RESOURCE_NOT_FOUND` 处理。
- SQL 使用 InnoDB、utf8mb4、utf8mb4_unicode_ci；沿用编号脚本，不覆盖 `001_create_app_user.sql`。

---

### Task 1: Phase 0 注册最终验收

**Files:** 只验证既有注册实现与测试。

**Interfaces:** `POST /api/v1/auth/register -> RegisterRequest -> AuthController -> UserService.register -> AppUserMapper -> RegisterResponse`。

- [x] 运行 `mvnw.cmd test`，先确认无凭据时的环境失败。
- [x] 从 IntelliJ 本地运行配置仅向 Maven 子进程注入 `DB_PASSWORD`，不输出或持久化 Secret。
- [x] 真实 MySQL 运行 13 个测试并确认 13 PASS、0 FAIL、0 ERROR。
- [ ] 运行 Phase 完结 `compile`、`git diff --check`、`git status --short` 与 Secret 扫描。

### Task 2: Phase A 登录行为（TDD）

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/careerplatform/user/controller/AuthController.java`
- Modify: `src/main/java/com/careerplatform/user/service/UserService.java`
- Create: `src/main/java/com/careerplatform/user/dto/LoginRequest.java`
- Create: `src/main/java/com/careerplatform/user/dto/LoginResponse.java`
- Create: `src/main/java/com/careerplatform/user/exception/InvalidCredentialsException.java`
- Modify/Test: `src/test/java/com/careerplatform/user/controller/AuthControllerIntegrationTests.java`

**Interfaces:** `LoginResponse UserService.login(String username, String rawPassword)`；`POST /api/v1/auth/login` 返回 `token,userId,username,status`。

- [ ] 先添加登录成功、密码错误、用户名不存在、响应无 `passwordHash` 的 MockMvc 测试并验证 RED。
- [ ] 增加登录 DTO 与统一 `INVALID_CREDENTIALS` 401 异常映射。
- [ ] 使用 `PasswordEncoder.matches(raw, hash)` 校验；仅 `ACTIVE` 用户可登录。
- [ ] 接入 `JwtTokenService.createToken(userId, username)` 并验证 GREEN。

### Task 3: Phase A JWT 与 currentUserId（TDD）

**Files:**
- Create: `src/main/java/com/careerplatform/auth/JwtTokenService.java`
- Create: `src/main/java/com/careerplatform/auth/AuthenticatedUser.java`
- Create: `src/main/java/com/careerplatform/auth/BearerTokenInterceptor.java`
- Create: `src/main/java/com/careerplatform/auth/CurrentUserId.java`
- Create: `src/main/java/com/careerplatform/auth/CurrentUserIdArgumentResolver.java`
- Create: `src/main/java/com/careerplatform/auth/UnauthorizedException.java`
- Create: `src/main/java/com/careerplatform/config/WebMvcConfig.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/test/resources/application.properties`
- Create/Test: `src/test/java/com/careerplatform/auth/JwtTokenServiceTest.java`
- Create/Test: `src/test/java/com/careerplatform/auth/AuthenticationIntegrationTests.java`

**Interfaces:** `createToken(Long,String)`；`parseToken(String) -> AuthenticatedUser`；Controller 参数 `@CurrentUserId Long currentUserId`。

- [ ] 先写 Token 生成/解析、篡改/过期 Token、无/非法/合法 Bearer 请求测试并验证 RED。
- [ ] 配置 JJWT 0.13.0 的 `api/impl/jackson` 依赖，Secret 最少 256 bit。
- [ ] Interceptor 排除 `/api/v1/auth/register` 和 `/api/v1/auth/login`，其余 `/api/v1/**` 认证。
- [ ] Interceptor 把认证结果写入 request attribute；参数解析器只读取统一属性。
- [ ] 401 统一返回 `ApiErrorResponse(code=UNAUTHORIZED)`，验证 GREEN。

### Task 4: Phase B 数据库与共享模型（TDD）

**Files:**
- Create: `sql/002_create_shared_profile_tables.sql`
- Create: `src/main/java/com/careerplatform/profile/**`
- Modify: `src/main/java/com/careerplatform/CareerPlatformApplication.java`
- Create/Test: `src/test/java/com/careerplatform/profile/**`

**Interfaces:** `/api/v1/profile`、`/education-experiences`、`/skills`、`/user-skills`、`/project-experiences`、`/internship-experiences`、`/certificate-awards`。

- [ ] 先写 SQL 约束审查测试/真实集成测试骨架并验证 RED（表不存在或端点不存在）。
- [ ] 建表：`user_profile(user_id UNIQUE)`、`education_experience`、`skill(name UNIQUE)`、`user_skill(user_id,skill_id UNIQUE)`、`project_experience`、`internship_experience`、`certificate_award`，含 owner/FK/索引。
- [ ] 安全应用 SQL；如无法应用则停止 DB 相关扩大并报告 `SQL_NOT_APPLIED`。
- [ ] 为每个资源增加 Entity/Mapper/Request/Response/Service/Controller；`UserSkill.proficiency` 和 `CertificateAward.type` 使用枚举。
- [ ] 所有 detail/update/delete 查询使用 `id AND user_id`；创建只使用 `currentUserId`。
- [ ] 覆盖 Profile upsert、Education 全 CRUD、字典/绑定唯一性、三类经历纵向链路及 2-3 种跨用户 GET/PUT/DELETE。

### Task 5: Phase C 数据库与职业探索（TDD）

**Files:**
- Create: `sql/003_create_career_exploration_tables.sql`
- Create: `src/main/java/com/careerplatform/career/**`
- Create/Test: `src/test/java/com/careerplatform/career/**`

**Interfaces:** `/career-goals`、`/companies`、`/jobs`、`/jobs/{jobId}/requirements`、`/jobs/{jobId}/notes`。

- [ ] 先写代表性 MockMvc 集成测试并验证 RED。
- [ ] 建表：`career_goal`、`company`、`job`、`job_requirement`、`job_note`，子表 FK 到 `job`，Skill 类型需求可关联 `skill`。
- [ ] 安全应用 SQL并验证表/索引；不得删除业务数据。
- [ ] 实现 DTO/Service/Mapper/Controller；Company/Job 均有 owner，Job 创建先验证 Company owner。
- [ ] `archive`/`unarchive` 使用显式端点并仅更新当前用户自己的 Job。
- [ ] Requirement/Note 的所有操作先验证 `job.id AND job.user_id`，再按子资源 id 操作。
- [ ] 覆盖 CareerGoal/Company CRUD、Company->Job、跨用户 Company 创建 Job 拒绝、Job archive/unarchive、Requirement/Note owner 隔离。

### Task 6: Phase D 验收与文档

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/DATABASE.md`
- Modify: `docs/DEVELOPMENT_STATUS.md`

- [ ] 更新文档并严格区分已实现、已验证、计划、阻塞。
- [ ] 运行 `mvnw.cmd compile`、相关测试、`mvnw.cmd test`。
- [ ] 汇总 Surefire 总测试数/PASS/FAIL/ERROR，确认真实 MySQL 参与。
- [ ] 运行 `git diff --check`、Secret/owner/DTO/SQL/MapperScan 审查、`git status --short`。
- [ ] 完成最终中文 GO/NO-GO/PARTIAL-GO 报告与学习索引。
