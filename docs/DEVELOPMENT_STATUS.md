# 开发状态

> 更新日期：2026-09-03。本文只记录真实完成度；长期范围见[项目规划](PROJECT_PLAN.md)。

## Milestone 2 结论

Milestone 2“最小身份闭环 + 共享基础档案 + 职业探索后端”已完成实现，并通过 Java 21 编译、MockMvc 全链路测试和真实 MySQL 集成测试。没有使用 H2，未实现本里程碑禁止范围内的功能。

## 已实现并验证

### 用户与身份

- `POST /api/v1/auth/register`：Bean Validation、BCrypt、重复用户名 409。
- `POST /api/v1/auth/login`：状态检查、`PasswordEncoder.matches`、统一无效凭证 401、JWT 返回。
- Bearer Token：JJWT 签名与过期验证；`userId`、`username` claims。
- `currentUserId`：Interceptor 写 request attribute，Argument Resolver 统一注入 Controller。
- 除注册、登录外，本轮 `/api/v1/**` API 均受保护；无 Token、非法 Token、过期 Token 返回统一 401。

### 共享基础档案

- `UserProfile` 查询与 upsert。
- `EducationExperience` 完整 CRUD。
- 全局 `Skill` 创建与查询，名称唯一。
- `UserSkill` 完整 CRUD，用户与技能组合唯一，熟练度为有限枚举。
- `ProjectExperience`、`InternshipExperience`、`CertificateAward` 完整 CRUD。
- 私有资源均按 `currentUserId` 隔离，跨用户 GET/PUT/DELETE 返回 `RESOURCE_NOT_FOUND`。

### 职业探索

- `CareerGoal` 完整 CRUD。
- 用户私有 `Company` 完整 CRUD；已有 Job 引用时拒绝删除。
- `Job` 创建、列表、详情、更新、archive、unarchive；只能引用当前用户自己的 Company。
- `JobRequirement` 与 `JobNote` 完整子资源 CRUD；先验证父 Job owner。
- `SKILL` 要求必须关联已存在 Skill，其他要求类型不得携带 `skillId`。

### 数据库与映射

- 依次保留 `001`、`002`、`003` 三个 SQL 文件；没有覆盖历史脚本。
- MySQL 中已真实应用 12 张新增表。
- `DatabaseSchemaIntegrationTests` 已从 `information_schema` 验证新增表、14 个外键和 4 个关键唯一索引。
- `@MapperScan` 已显式覆盖 user、profile、career 三个 mapper 包，Context Test 与业务测试均通过。

## 当前未实现

- Spring Security 完整框架、RBAC、OAuth、Refresh Token、Token 黑名单和复杂 Logout。
- Learning、Resume、Application、Assessment、Interview、Offer、FinalReview。
- Vue 3 前端。
- Spring AI、JD AI 解析、RAG、Embedding、Agent、Tool Calling、AI Evaluation。
- Redis、MQ、Elasticsearch、管理员后台和 HR 端。

## 已知技术债

- `ProfileService` 与 `CareerService` 已开始变大，后续模块扩展时可按聚合拆分；本轮为保持课程项目简单未提前抽象。
- `UserSkill` 列表响应需要逐项读取 Skill，数据量增大后可改为联表查询以消除 N+1。
- 测试启动时 Mockito 提示未来 JDK 将限制动态加载 agent；当前 Java 21 不影响测试结果，后续升级 JDK/Mockito 时再处理。
- 当前使用轻量 MVC Interceptor 鉴权；出现 RBAC 或更多认证方式时再评估 Spring Security，不在本轮扩张。

## 下一步建议

先人工 Review 本轮未提交 diff。确认身份边界、API 和数据库关系后，再单独规划下一里程碑；不要在本次修改中顺手加入 Learning、Resume、Application 或 AI 能力。
