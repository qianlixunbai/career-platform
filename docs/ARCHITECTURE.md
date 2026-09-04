# 系统架构

## 文档状态

本文记录截至 2026-09-04 已实现并通过真实 MySQL 集成测试的 Milestone 2 与 Milestone 3 Learning 架构。简历、求职过程、前端和 AI 仍是后续规划，不写作已实现能力。原始开工方案只保留历史设计基线；当前事实以代码、SQL 和持续维护文档为准。

## 技术基线

- Java 21、Spring Boot 3.5.14
- MyBatis-Plus 3.5.17、MySQL
- Spring Web、Bean Validation、BCrypt
- JJWT 0.13.0
- Maven Wrapper

## 当前分层与模块

```text
HTTP / JSON
  ↓
BearerTokenInterceptor → JwtTokenService → request.currentUserId
  ↓
Controller + Request/Response DTO
  ↓
Service（业务规则、归属校验、事务）
  ↓
MyBatis-Plus Mapper
  ↓
MySQL
```

```text
com.careerplatform
├─ auth       JWT、Bearer 拦截、currentUserId 参数解析
├─ common     ApiErrorResponse、统一异常映射
├─ config     PasswordEncoder、WebMvc 配置
├─ user       注册、登录、用户数据访问
├─ profile    共享基础档案
├─ career     职业目标、公司、岗位、要求与笔记
└─ learning   周计划、任务、学习记录、周复盘、笔记与资料元数据
```

当前源码实际包含 15 个 `@RestController`，其中 Learning 提供 6 个 Controller。Controller 不直接调用 Mapper，也不接受客户端提供的 `userId` 作为资源归属。公开端点只有 `POST /api/v1/auth/register` 和 `POST /api/v1/auth/login`；其余 `/api/v1/**` 端点都要求合法 Bearer Token。

## 身份与安全边界

登录由 `UserService.login()` 查询用户、检查 `ACTIVE` 状态并使用 `PasswordEncoder.matches(raw, hash)` 验证密码。用户名不存在、密码错误和用户不可登录统一返回 `401 INVALID_CREDENTIALS`。成功后由 `JwtTokenService` 生成包含 `userId` 和 `username` 的短期 JWT。

`BearerTokenInterceptor` 统一验证 `Authorization: Bearer ...`，将 `currentUserId` 写入 request attribute；`CurrentUserIdArgumentResolver` 为 Controller 的 `@CurrentUserId Long` 参数提供身份。没有使用 ThreadLocal。

所有私有资源的 Service 查询、更新和删除条件都包含资源主键与 `currentUserId`。资源存在但属于其他用户时，对外返回 `404 RESOURCE_NOT_FOUND`，不泄露资源存在性。

## 共享基础档案边界

共享档案是后续 Career、Learning、Resume、Application Review 和 AI 的事实源：

- `UserProfile`：每用户最多一条，`PUT` 为 upsert。
- `EducationExperience`、`ProjectExperience`、`InternshipExperience`、`CertificateAward`：用户私有 CRUD。
- `Skill`：全局标准技能字典，只提供创建和查询。
- `UserSkill`：用户与技能的关联，熟练度限定为 `BEGINNER`、`FAMILIAR`、`PROFICIENT`。

其他模块应经正式 Service 读取或修改共享档案，不能绕过业务边界直接写表。

## 职业探索边界

- `CareerGoal`：用户可维护多个目标与历史方向。
- `Company`：当前是用户私有收集的信息，不是全站企业库。
- `Job`：同时属于用户并引用该用户自己的 Company；用 `archived` 保留历史岗位，不提供物理删除端点。
- `JobRequirement`：挂在 Job 下；`SKILL` 类型必须引用已存在的全局 Skill，其他类型不得携带 `skillId`。
- `JobNote`：挂在 Job 下，保存用户自己的岗位观察。

创建或更新 Job 前，Service 验证 `company.id = companyId AND company.user_id = currentUserId`。访问 JobRequirement 或 JobNote 前，Service 先验证父 Job 属于当前用户，再以 `child.id + job.id` 操作子资源。数据库还用 `(company_id, user_id) → company(id, user_id)` 复合外键形成最终一致性防线。

## Learning 边界

Learning 只实现传统学习业务，不包含 AI、RAG、文件上传、向量化或前端。六个资源的关系固定为：

- `LearningPlan 1 -> n LearningTask`；`LearningTask 1 -> n StudyRecord`。
- `LearningPlan 1 -> 0..1 WeeklyReview`，通过 `PUT /api/v1/learning-plans/{planId}/review` 创建或更新同一条复盘。
- `LearningNote` 与 `LearningMaterial` 独立属于 Plan，可选关联同一 Plan 下的 Task。

所有 Learning 私有表都保存 `user_id`。每个 Learning API 从 `@CurrentUserId Long` 获取 owner；Service 的详情、更新、删除查询同时带资源 ID 与 owner，子资源还验证路径父级。Note/Material 的 `taskId` 使用同一 Plan、同一 owner 的任务校验，跨用户或错误父子组合统一返回 `404 RESOURCE_NOT_FOUND`。

Learning 的 Plan 周起止日期要求 `weekEnd >= weekStart`，同一用户同一 `weekStart` 唯一；Task 截止日期必须位于 Plan 闭区间，计划用时为正、排序值不小于零；StudyRecord 时长为正且不会隐式改变 Task 状态。删除 Task 时事务内先解绑 Note/Material 的可选 `taskId`，再删除 Records 和 Task；删除 Plan 时按 Material、Note、Review、Record、Task、Plan 顺序清理全部后代。

## Mapper 注册与异常

应用入口通过显式 `@MapperScan` 注册：

- `com.careerplatform.user.mapper`
- `com.careerplatform.profile.mapper`
- `com.careerplatform.career.mapper`
- `com.careerplatform.learning.mapper`

异常继续统一为 `ApiErrorResponse(code, message, timestamp)`，当前覆盖参数校验、非法 JSON/枚举/路径类型、未认证、无效凭证、资源不存在、重复资源和资源被引用等场景。Learning 的 Request DTO 与 Entity 分离，TEXT 请求字段统一限制为 16000 字符，VARCHAR 上限与 SQL 列宽一致；Response DTO 不包含 `userId` 或 `passwordHash`。

## 后续规划边界

Resume、Application、Assessment、Interview、Offer、Vue、Spring AI、RAG 和 Agent 尚未实现。未来 AI 输出仍须遵循“候选结果 → 用户确认 → Java Service 校验与持久化”，不得直接写正式业务数据。
