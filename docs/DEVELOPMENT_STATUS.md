# 开发状态

> 更新日期：2026-09-04。本文只记录真实完成度；长期范围见[项目规划](PROJECT_PLAN.md)。

## Milestone 2 结论

Milestone 2“最小身份闭环 + 共享基础档案 + 职业探索后端”已完成并冻结，checkpoint 为 `228bc97628a7bd9a12d9e36d65f0bebef0da094e`。该里程碑通过 Java 21 编译、MockMvc 全链路测试和真实 MySQL 集成测试。没有使用 H2，未实现本里程碑禁止范围内的功能。

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
- `DatabaseSchemaIntegrationTests` 已从 `information_schema` 验证 M2 的 12 张新增表、14 个外键和 4 个关键唯一索引。
- `@MapperScan` 已显式覆盖 user、profile、career 三个 mapper 包，Context Test 与业务测试均通过。

## Milestone 3 Learning 结论

Milestone 3 Learning 已完成并冻结，checkpoint 为 `9959ea40189d1360329ca27cabdaa0a8f9c8a28a`。该里程碑通过 Java 21 编译、真实 MySQL MockMvc 集成测试、Schema metadata 测试和并发复盘测试。其历史验证基线为 19 张 `BASE TABLE`、15 个 `@RestController`；不再把已冻结里程碑描述为未提交。

### 已实现并验证

- `LearningPlan`：创建、列表、详情、更新、删除；同一用户同一 `weekStart` 唯一，且校验 `weekEnd >= weekStart`。
- `LearningTask`：Plan 下的完整 CRUD；校验 Plan owner、Task owner、dueDate 闭区间、正计划用时和非负排序值。
- `StudyRecord`：Task 下的完整 CRUD；校验 Task owner 和正学习时长，新增记录不改变 Task 状态。
- `WeeklyReview`：Plan 下 `GET/PUT`；PUT 首次创建、后续更新同一行，数据库 `UNIQUE(plan_id)` 保证 0..1。
- `LearningNote`：Plan 下完整 CRUD；`taskId` 可空，非空时必须是同一 owner、同一 Plan 的 Task。
- `LearningMaterial`：Plan 下完整 CRUD；保存资料元数据，`sourceUrl` 可空且不处理文件；`taskId` 使用同一归属校验。
- 删除语义：Task 删除解绑 Note/Material 后删除 Records；Plan 删除清理全部后代，不留下孤儿数据。
- 统一错误：Bean Validation、非法 JSON/枚举/路径类型均返回 `400 VALIDATION_ERROR`；越权私有资源返回 `404 RESOURCE_NOT_FOUND`。
- 数据库：004 脚本已真实应用；六张 Learning 表、13 个 FK、5 个非主键 UNIQUE、InnoDB/utf8mb4_unicode_ci 和六个 Mapper 注册均已验证。

### Learning 测试统计

按当前 Surefire XML，Learning 相关定向文件共运行 23 项：`DatabaseSchemaIntegrationTests` 2、`LearningCoreIntegrationTests` 11、`LearningPlanTaskRaceIntegrationTests` 3、`LearningSupportIntegrationTests` 6、`LearningReviewConcurrencyIntegrationTests` 1；其中 Schema suite 的 1 项为 M2 基线、1 项为 M3 Learning Schema，故新增 Learning 测试为 22 项。全部 PASS，FAIL/ERROR/SKIPPED 均为 0。全量测试结果以 Task 4 审计报告中的最新 XML 统计为准。

## Milestone 4 Resume 状态

Resume 后端已完成并冻结于 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56`，新增 `resume`、`resume_version`、`resume_content_item` 三层资源及三个 Controller：

- Resume 支持按当前用户创建、列表、详情、更新和删除；含 FINALIZED 版本时删除返回 `409 RESOURCE_IN_USE`。
- ResumeVersion 支持空 DRAFT、共享 Profile 快照生成、列表、详情、更新、复制、定稿和删除；版本号按 Resume 唯一，定稿幂等且保留首次 `finalizedAt`。
- ResumeContentItem 支持 DRAFT 下创建、列表、详情、更新、删除；生成内容写入持久化 source snapshot，复制后使用独立 item 行。
- owner-aware composite FK、完整路径父级校验和 FINALIZED 写保护覆盖到 Service 与数据库边界；错误 owner/父级组合返回 404，定稿后写入返回 `409 INVALID_RESOURCE_STATE`。
- `ResumeIntegrationTests` 覆盖上述 CRUD、快照独立性、复制隔离、越权/错父级、定稿状态、并发版本号和 finalize-vs-item-write race；`DatabaseSchemaIntegrationTests` 已补充三表、关键复合 FK 列序、版本号唯一键、metadata 和三个 Mapper bean 断言。
- 新增测试已通过 Java/Maven 编译和真实 MySQL 验证：定向 `DatabaseSchemaIntegrationTests` 3 + `ResumeIntegrationTests` 9 共 12 项，Failures 0、Errors 0、Skipped 0；全量 Maven test 81 项，Failures 0、Errors 0、Skipped 0。005 已通过 login-path 幂等应用，数据库确认 22 张 `BASE TABLE`。M4 已以 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56` 固化并 push 到 `main`。

## Milestone 5A Vue Frontend Foundation

Milestone 5A Vue Frontend Foundation 已完成并冻结，状态为 `FROZEN / COMMITTED / PUSHED`，checkpoint 为 `6f503667c0df8b4556fe65c6a6c0a667d85fee81`。

### 已实现并验证

- Vue 3、TypeScript、Vite、Vue Router、Axios 和 Element Plus 已进入 `main`。
- 实际 routed frontend pages：18。
- `npm run typecheck`：PASS。
- `npm run build`：PASS。
- 后端 HTTP smoke：12/12 PASS。
- P0 = 0，P1 = 0。
- P2 = 1：Vite 主 chunk 约 1.07 MB warning。该 warning 当前不阻塞项目，本轮不为此重构前端。

以上为 M5A 冻结时的既有验证证据；本轮仅同步文档，未重新执行测试或构建。

Application、Assessment、Interview、Offer、FinalReview 和 AI 能力仍未实现。

代码与迁移脚本扫描得到 22 张业务表（含 `app_user`）和 18 个 `@RestController`；M5A 已落地 18 个 routed frontend pages。

## 当前未实现

- Spring Security 完整框架、RBAC、OAuth、Refresh Token、Token 黑名单和复杂 Logout。
- Application、Assessment、Interview、Offer、FinalReview。
- 正式落地 AI 功能当前为 0 个；Spring AI、JD AI 解析、AI 学习规划与周复盘、AI 面试与求职复盘、Resume + JD matching、RAG、Embedding、Agent、Tool Calling、AI Evaluation 均尚未实现。
- Redis、MQ、Elasticsearch、管理员后台和 HR 端。

## 已知技术债

- `ProfileService` 与 `CareerService` 已开始变大，后续模块扩展时可按聚合拆分；本轮为保持课程项目简单未提前抽象。
- `UserSkill` 列表响应需要逐项读取 Skill，数据量增大后可改为联表查询以消除 N+1。
- 测试启动时 Mockito 提示未来 JDK 将限制动态加载 agent；当前 Java 21 不影响测试结果，后续升级 JDK/Mockito 时再处理。
- 当前使用轻量 MVC Interceptor 鉴权；出现 RBAC 或更多认证方式时再评估 Spring Security，不在本轮扩张。

## 下一步建议

下一正式开发任务为 Milestone 5B — Application Management。Application 和 AI 继续保持未实现的规划边界。
