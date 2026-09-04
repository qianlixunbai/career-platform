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
- **P2：技术深化。** 优先完成带来源和原文追溯的 RAG 学习资料问答，其次考虑 Agent + Tool Calling，最后视进度开展 AI Evaluation / A-B 实验。

上述内容是版本计划，不代表当前已经实现。

## 当前状态

Milestone 2 已冻结并以 checkpoint `228bc97628a7bd9a12d9e36d65f0bebef0da094e` 固化；Milestone 3 Learning 已冻结并以 checkpoint `9959ea40189d1360329ca27cabdaa0a8f9c8a28a` 固化。两者均已通过既有真实 MySQL 集成测试。

Milestone 4 Resume 后端已完成并冻结，包含 Resume、ResumeVersion、ResumeContentItem 的归属校验、草稿编辑、生成快照、定稿、复制和状态保护。005 已通过 login-path 幂等应用，真实数据库确认有 22 张 `BASE TABLE`。编译通过；定向 `DatabaseSchemaIntegrationTests` 3 + `ResumeIntegrationTests` 9 共 12 项，以及全量 Maven test 81 项均为 Failures 0、Errors 0、Skipped 0。源码扫描得到 18 个 `@RestController`；MyBatis-Plus Mapper 已显式注册。M4 已以 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56` 固化并 push 到 `main`。

Milestone 5A Vue Frontend Foundation 已完成、冻结、commit 并 push 到 `main`，checkpoint 为 `6f503667c0df8b4556fe65c6a6c0a667d85fee81`。

Milestone 5B Application Management 已完成、冻结、commit 并 push 到 `main`，状态为 `FROZEN / COMMITTED / PUSHED`，checkpoint 为 `6225510e51c11f65213e654dcc2dce3f9de45875`。本里程碑新增 Application、阶段历史、Assessment、Interview、Offer 与 FinalReview 六张表及真实前端，M5B 冻结规模为 28 张业务表、23 个 `@RestController`、20 个 routed frontend pages。

Milestone 6A 已在当前 working tree 实现第 1 个正式 AI 功能：JD Structured Parse。它采用 Spring AI 1.1.8、provider-neutral `AiChatGateway`、typed structured output、evidence 校验、真实 Skill 匹配、重复检测和 SHA-256 source fingerprint；parse 永不写库，只有用户确认后才在 Java 单事务中追加到既有 `job_requirement`。AI 默认关闭，无 key/provider 时传统系统仍可启动和使用。生产 Provider 固定为 DeepSeek Official API（OpenAI-compatible adapter），模型硬锁为 `deepseek-v4-flash`，不允许客户端、环境变量或 runtime options 覆盖，也没有 Pro fallback。当前规模保持 28 张业务表与 20 个 routed pages，Controller 为 24 个。离线 AI 定向测试、Spring AI structured-output wiring、frontend typecheck/build、localhost HTTP business smoke 与 authenticated DeepSeek Flash smoke 均已通过；parse 前后 `job_requirement` 数量不变、confirm 后按选择增加、stale fingerprint 返回 409、跨用户 Job 返回 404。最终真实 MySQL full Maven 为 132 项、Failures 0、Errors 0、Skipped 0，M6A 状态为 `GO / READY FOR CHECKPOINT / NOT COMMITTED`。

详细完成度见 [开发状态](docs/DEVELOPMENT_STATUS.md)。

## 本地运行前提

1. 安装 Java 21 和 MySQL。
2. 创建数据库 `career_platform`，字符集使用 `utf8mb4`，排序规则使用 `utf8mb4_unicode_ci`。
3. 按编号依次执行 [`sql/001_create_app_user.sql`](sql/001_create_app_user.sql)、[`sql/002_create_shared_profile_tables.sql`](sql/002_create_shared_profile_tables.sql)、[`sql/003_create_career_exploration_tables.sql`](sql/003_create_career_exploration_tables.sql)、[`sql/004_create_learning_tables.sql`](sql/004_create_learning_tables.sql)、[`sql/005_create_resume_tables.sql`](sql/005_create_resume_tables.sql) 和 [`sql/006_create_application_tables.sql`](sql/006_create_application_tables.sql)。
4. 通过环境变量提供数据库凭证：
   - `DB_PASSWORD`：必填。
   - `DB_USERNAME`：可选，默认值为 `root`。
   - `JWT_SECRET`：必填，使用足够长的随机 Secret。
   - `JWT_EXPIRATION_SECONDS`：可选，默认值为 `3600`。
   - `AI_JD_PARSE_ENABLED`：可选，默认 `false`。
   - `AI_CHAT_PROVIDER`：启用时设为 `openai`；默认 `none`。
   - `AI_API_KEY`：启用真实 Provider 时从本地环境提供，不写入仓库。
   - DeepSeek endpoint 与生产模型在应用配置中固定为 `https://api.deepseek.com` 和 `deepseek-v4-flash`；不支持 `AI_BASE_URL`、`AI_MODEL` 或客户端动态覆盖。
5. 执行 `./mvnw spring-boot:run`；Windows PowerShell 可执行 `.\mvnw.cmd spring-boot:run`。

任何真实数据库密码、Token 或 API Key 都不得提交到 Git。文档和示例中也只应使用环境变量名或占位符。

## 文档导航

- [项目规划](docs/PROJECT_PLAN.md)：业务范围、版本路线、预期规模和风险降级。
- [开发状态](docs/DEVELOPMENT_STATUS.md)：严格区分已完成、正在进行和尚未开始。
- [系统架构](docs/ARCHITECTURE.md)：当前技术基线、目标分层和 AI 写入边界。
- [数据库说明](docs/DATABASE.md)：当前真实表结构、命名映射和候选表规划。
- [原始开工方案](docs/reference/大学生职业发展与求职管理平台_项目开工方案_v1.md)：保留项目最初的完整范围和初始设计基线，不作为当前实时维护文档；当前实际状态以真实代码和上述持续维护文档为准。
