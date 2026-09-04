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
- Vue 3（规划）
- Spring AI（规划）

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

Milestone 2 后端和 Milestone 3 Learning 已实现并通过真实 MySQL 集成测试：包括注册、登录、JWT Bearer 鉴权、统一 `currentUserId`、共享基础档案、职业目标、公司、岗位、岗位要求、岗位笔记，以及周计划、学习任务、学习记录、周复盘、学习笔记和学习资料元数据。当前实际共 19 张业务表（含 `app_user`），实际包含 15 个 `@RestController`，MyBatis-Plus Mapper 已显式注册；Vue、Resume、Application 和 AI 能力仍未实现。

详细完成度见 [开发状态](docs/DEVELOPMENT_STATUS.md)。

## 本地运行前提

1. 安装 Java 21 和 MySQL。
2. 创建数据库 `career_platform`，字符集使用 `utf8mb4`，排序规则使用 `utf8mb4_unicode_ci`。
3. 按编号依次执行 [`sql/001_create_app_user.sql`](sql/001_create_app_user.sql)、[`sql/002_create_shared_profile_tables.sql`](sql/002_create_shared_profile_tables.sql)、[`sql/003_create_career_exploration_tables.sql`](sql/003_create_career_exploration_tables.sql) 和 [`sql/004_create_learning_tables.sql`](sql/004_create_learning_tables.sql)。
4. 通过环境变量提供数据库凭证：
   - `DB_PASSWORD`：必填。
   - `DB_USERNAME`：可选，默认值为 `root`。
   - `JWT_SECRET`：必填，使用足够长的随机 Secret。
   - `JWT_EXPIRATION_SECONDS`：可选，默认值为 `3600`。
5. 执行 `./mvnw spring-boot:run`；Windows PowerShell 可执行 `.\mvnw.cmd spring-boot:run`。

任何真实数据库密码、Token 或 API Key 都不得提交到 Git。文档和示例中也只应使用环境变量名或占位符。

## 文档导航

- [项目规划](docs/PROJECT_PLAN.md)：业务范围、版本路线、预期规模和风险降级。
- [开发状态](docs/DEVELOPMENT_STATUS.md)：严格区分已完成、正在进行和尚未开始。
- [系统架构](docs/ARCHITECTURE.md)：当前技术基线、目标分层和 AI 写入边界。
- [数据库说明](docs/DATABASE.md)：当前真实表结构、命名映射和候选表规划。
- [原始开工方案](docs/reference/大学生职业发展与求职管理平台_项目开工方案_v1.md)：保留项目最初的完整范围和初始设计基线，不作为当前实时维护文档；当前实际状态以真实代码和上述持续维护文档为准。
