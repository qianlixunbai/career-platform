# Career Platform

《大学生职业发展与求职管理平台的设计与实现》

一个帮助大学生统一管理职业目标、学习提升、简历版本和完整求职过程，并在关键环节引入可确认、可追溯 AI 建议的平台。

仓库：[qianlixunbai/career-platform](https://github.com/qianlixunbai/career-platform)

## 技术栈

- Java 21
- Spring Boot 3.5.14
- MyBatis-Plus 3.5.17
- MySQL
- Spring Web、Bean Validation
- BCrypt、JJWT
- Maven
- Vue 3 + TypeScript + Vite + Vue Router + Axios + Element Plus
- Spring AI 1.1.8（OpenAI-compatible Chat、typed structured output）

## 核心业务

平台规划为四个核心业务模块：

1. 职业探索与目标管理
2. 学习提升管理
3. 简历管理
4. 求职过程管理

个人基本信息、教育经历、技能、项目、实习和证书/获奖经历组成全系统共享的基础档案，而不是第五个独立业务模块。核心业务闭环从确定求职方向开始，经过学习、简历准备、岗位分析、投递、测评、面试和 Offer 处理，最终通过复盘反馈到下一轮职业准备。

## 版本规划

- **P0：核心业务闭环。** 完成传统业务基础能力；唯一必做 AI 功能为 JD 结构化解析，解析结果必须经用户检查、修改和确认后再由 Java Service 保存。P0 本身尚不满足课程最终至少 3 个 AI 功能的要求。
- **P1：智能增强。** 规划 AI 学习计划与周复盘、AI 面试/求职复盘、简历与 JD 匹配分析。
- **P2：技术深化。** 优先完成带来源和原文追溯的 RAG 学习资料问答，其次考虑通用 Agent + Tool Calling（受限的 Job Discovery Tool Calling 已由 M6C 完成），最后视进度开展 AI Evaluation / A-B 实验。

上述内容是版本计划，不代表当前已经实现。

## 当前状态

**Milestone 7 / P2-A RAG：已完成并冻结，状态为 `FROZEN / COMMITTED / PUSHED`。** 现有 LearningMaterial 已扩展真实 PDF/DOCX 上传、MySQL 原件存储、Chunk/Embedding、当前用户当前计划范围的语义检索和可信来源问答；入口位于学习计划详情的“学习资料”。正式完成 AI 功能为 4 个，包含 M7 RAG；外部 Tech Lead 已给出最终 GO；M7 feature checkpoint 为 `6e4db93ccae7b4efc9530c8950926d257eb21aaf`，已 push 到 `origin/main`。M7 Closing 保留提交前的验收证据，其 READY/NOT COMMITTED/NOT PUSHED 描述历史时点。证据及配置见 [M7 Closing](docs/M7_RAG_CLOSING_VERIFICATION.md)。下列 M2～M6C 内容保留各里程碑历史记录。

Milestone 2 已冻结并以 checkpoint `228bc97628a7bd9a12d9e36d65f0bebef0da094e` 固化；Milestone 3 Learning 已冻结并以 checkpoint `9959ea40189d1360329ca27cabdaa0a8f9c8a28a` 固化。两者均已通过既有真实 MySQL 集成测试。

Milestone 4 Resume 后端已完成并冻结，包含 Resume、ResumeVersion、ResumeContentItem 的归属校验、草稿编辑、生成快照、定稿、复制和状态保护。005 已通过 login-path 幂等应用，真实数据库确认有 22 张 `BASE TABLE`。编译通过；定向 `DatabaseSchemaIntegrationTests` 3 + `ResumeIntegrationTests` 9 共 12 项，以及全量 Maven test 81 项均为 Failures 0、Errors 0、Skipped 0。源码扫描得到 18 个 `@RestController`；MyBatis-Plus Mapper 已显式注册。M4 已以 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56` 固化并 push 到 `main`。

Milestone 5A Vue Frontend Foundation 已完成、冻结、commit 并 push 到 `main`，checkpoint 为 `6f503667c0df8b4556fe65c6a6c0a667d85fee81`。

Milestone 5B Application Management 已完成、冻结、commit 并 push 到 `main`，状态为 `FROZEN / COMMITTED / PUSHED`，checkpoint 为 `6225510e51c11f65213e654dcc2dce3f9de45875`。本里程碑新增 Application、阶段历史、Assessment、Interview、Offer 与 FinalReview 六张表及真实前端，M5B 冻结规模为 28 张业务表、23 个 `@RestController`、20 个 routed frontend pages。

Milestone 6A — AI Foundation + JD Structured Parse 已完成、冻结，并以 checkpoint `07712a687685e368e35e5c04bb0f294ae218c265` 固化并 push 到 `main`；commit 为 `feat: add AI JD structured parsing`。它采用 Spring AI 1.1.8、provider-neutral `AiChatGateway`、typed structured output、evidence 校验、真实 Skill 匹配、重复检测和 SHA-256 source fingerprint；parse 永不写库，只有用户确认后才在 Java 单事务中追加到既有 `job_requirement`。AI 默认关闭，无 key/provider 时传统系统仍可启动和使用。生产 Provider 固定为 DeepSeek Official API（OpenAI-compatible adapter），模型硬锁为 `deepseek-v4-flash`，不允许客户端、环境变量或 runtime options 覆盖，也没有 Pro fallback。M6A 冻结时规模为 28 张业务表与 20 个 routed pages，Controller 为 24 个。离线 AI 定向测试、Spring AI structured-output wiring、frontend typecheck/build、localhost HTTP business smoke 与 authenticated DeepSeek Flash smoke 均已通过；parse 前后 `job_requirement` 数量不变、confirm 后按选择增加、stale fingerprint 返回 409、跨用户 Job 返回 404。最终真实 MySQL full Maven 为 132 项、Failures 0、Errors 0、Skipped 0，M6A 状态为 `FROZEN / COMMITTED / PUSHED`。

Milestone 6B — AI Learning Planning + Weekly Review 已完成、冻结，状态为 `FROZEN / COMMITTED / PUSHED`；功能 checkpoint 为 `805a3801af76e4e88434e52e154d2069ad3c4d1b`，commit 为 `feat: add AI learning planning and weekly review`，已 push 到 `origin/main`。以下验证均来自 checkpoint 前的 Closing，本次未重跑：用户 IDEA Full Maven 155 项及事务集成类 3/3 全绿；Astra 对 IDEA backend 执行真实 DeepSeek Flash Plan/Review smoke 各一次，均 PASS。Learning 列表页可基于用户明确时间预算、可选职业目标、结构化岗位要求、技能与有限学习历史生成可编辑候选；候选不写库，只有用户确认后才由 `LearningService` 在同一事务内创建一个 Plan 与全部 Tasks。详情页可生成带 Java metrics 与可信 evidence 的周复盘候选；“应用到表单”不会自动覆盖或保存现有 Review。M6B 不新增表、migration 或 routed page，M6B 冻结时规模为 28 张业务表、24 个 `@RestController`、20 个 routed pages；当时正式 AI 功能数由 1 增至 2；当前数量以以下 M6C post-checkpoint 状态为准。生产模型仍硬锁 `deepseek-v4-flash`，无 tools、无动态 model override、无 Pro fallback。

详细完成度见 [开发状态](docs/DEVELOPMENT_STATUS.md)。

Milestone 6C — AI Job Discovery 已实现并通过 Final Closing Verification，是第 3 个正式 AI 功能；当前状态为 `FROZEN / COMMITTED / PUSHED`，M6C checkpoint 为 `915acc0d02ac173877ae49b10fff96243b236cd5`，已 commit 并 push 到 `origin/main`。它基于职业目标和用户技能，通过专用 Spring AI Tool Calling gateway 调用 Tavily Search；页面分开展示来源事实、待核实字段和 AI 匹配建议。发现过程不写业务数据库，只有用户选择已有公司、确认岗位类型并显式保存后才进入现有 `CareerService.createJob()`。候选有效期 15 分钟，不新增表或 migration。M6A/M6B 的 no-tools 路径保持独立。M6C Closing 阶段的 deterministic 102 项、M6A/M6B regression 31 项、真实 MySQL 集成 3 项、前端 typecheck/build 均通过；Real Provider Gate 为 `PASS`，Smoke #4 返回 3 个候选、searchCalls=2，Job/Company 均 0→0。详情见 [M6C Closing Verification](docs/M6C_CLOSING_VERIFICATION.md)。以下是 M6C checkpoint 的历史快照：28 张业务表、25 个精确 @RestController、21 个 routed pages、3 个已完成 AI 功能；当时 RAG 尚未实现。当前 M7 post-checkpoint 机械统计为 29 张表、26 个精确 @RestController、21 个 routed pages、4 个正式 AI 功能。原 Closing 的 `READY FOR CHECKPOINT / NOT COMMITTED / NOT PUSHED` 是 pre-checkpoint historical decision，不代表当前状态。

## 本地运行前提

1. 安装 Java 21 和 MySQL。
2. 创建数据库 `career_platform`，字符集使用 `utf8mb4`，排序规则使用 `utf8mb4_unicode_ci`。
3. 按编号依次执行 [`sql/001_create_app_user.sql`](sql/001_create_app_user.sql)、[`sql/002_create_shared_profile_tables.sql`](sql/002_create_shared_profile_tables.sql)、[`sql/003_create_career_exploration_tables.sql`](sql/003_create_career_exploration_tables.sql)、[`sql/004_create_learning_tables.sql`](sql/004_create_learning_tables.sql)、[`sql/005_create_resume_tables.sql`](sql/005_create_resume_tables.sql) 和 [`sql/006_create_application_tables.sql`](sql/006_create_application_tables.sql)。
4. 通过环境变量提供数据库凭证：
   - `DB_PASSWORD`：必填。
   - `DB_USERNAME`：可选，默认值为 `root`。
   - `JWT_SECRET`：必填，使用足够长的随机 Secret。
   - `JWT_EXPIRATION_SECONDS`：可选，默认值为 `3600`。
   - `AI_CHAT_ENABLED`：全局 Chat AI 开关，可选，默认 `false`。
   - `AI_JD_PARSE_ENABLED`：M6A 旧开关，仅作为 `AI_CHAT_ENABLED` 未设置时的临时兼容 fallback。
   - `AI_CHAT_PROVIDER`：启用时设为 `openai`；默认 `none`。
   - `AI_API_KEY`：启用真实 Provider 时从本地环境提供，不写入仓库。
   - `TAVILY_SEARCH_ENABLED`：M6C 外部搜索开关，默认 `false`；Job Discovery 同时要求 Chat AI 可用。
   - `TAVILY_API_KEY`：仅从用户本地环境或 IDEA Run Configuration 提供；无需且禁止在聊天中发送。搜索 endpoint 固定为官方 `https://api.tavily.com/search`，不支持任意 URL 配置。
   - DeepSeek endpoint 与生产模型在应用配置中固定为 `https://api.deepseek.com` 和 `deepseek-v4-flash`；不支持 `AI_BASE_URL`、`AI_MODEL` 或客户端动态覆盖。
5. 执行 `./mvnw spring-boot:run`；Windows PowerShell 可执行 `.\mvnw.cmd spring-boot:run`。

M7 代码运行前需在备份和确认当前数据库结构后**执行一次** [`sql/007_add_learning_material_rag.sql`](sql/007_add_learning_material_rag.sql)。007 含 ALTER TABLE，不可像早期 CREATE-only 脚本一样直接重复执行；本轮已由 MySQL Gate 验证执行结果，详见 M7 Closing。原文件保存在既有 MySQL，单份最多 5 MiB，每计划最多 20 份、100 MiB；不需要额外存储服务或向量数据库。

RAG 的 Embedding 与 DeepSeek Chat 分开配置：`EMBEDDING_ENABLED=true`、`EMBEDDING_ENDPOINT`（完整 HTTPS embeddings endpoint）、`EMBEDDING_MODEL`、`EMBEDDING_API_KEY`；可选 `EMBEDDING_VERSION`（默认 1，服务端模型修订变化时更新并重新索引）。只支持 OpenAI-compatible 的 float embedding 响应；没有默认 Provider/模型，不假设 DeepSeek 提供 Embedding。Secret 只在本地环境或 IDEA Run Configuration 提供；不要发到聊天。缺少配置时传统功能仍可使用，问答显示不可用。Chat 继续使用原有 AI 配置与硬锁 `deepseek-v4-flash`。

任何真实数据库密码、Token 或 API Key 都不得提交到 Git。文档和示例中也只应使用环境变量名或占位符。

## 文档导航

- [项目规划](docs/PROJECT_PLAN.md)：业务范围、版本路线、预期规模和风险降级。
- [开发状态](docs/DEVELOPMENT_STATUS.md)：严格区分已完成、正在进行和尚未开始。
- [系统架构](docs/ARCHITECTURE.md)：当前技术基线、目标分层和 AI 写入边界。
- [数据库说明](docs/DATABASE.md)：当前真实表结构、命名映射和候选表规划。
- [原始开工方案](docs/reference/大学生职业发展与求职管理平台_项目开工方案_v1.md)：保留项目最初的完整范围和初始设计基线，不作为当前实时维护文档；当前实际状态以真实代码和上述持续维护文档为准。
