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

项目初始化阶段已完成，当前正在进行用户与共享基础档案模块的详细设计和实现。目前仓库处于第一张表和 Entity 阶段：数据库中已创建 `app_user`，代码中已有 `AppUser` 和 `UserStatus`，但 Mapper、Service、Controller、注册登录、JWT/Spring Security、Vue 前端和 AI 能力均尚未实现。

启动日志中的 `No MyBatis mapper was found` 是因为当前尚未创建 Mapper，并非 MyBatis-Plus 本身发生故障。

当前数据源用户名配置已修正为 `${DB_USERNAME:root}`，数据库密码从 `${DB_PASSWORD}` 读取。Spring Boot 项目以前完成过启动验证，但尚未通过 Mapper 执行真实 SQL，因此 MyBatis-Plus → MySQL 的真实查询链路仍待验证。JDBC URL 中显式配置的 `characterEncoding=utf8` 也将在该查询链路建立后结合 `utf8mb4` 数据库核验；当前不据此断言连接字符集存在问题。

详细完成度见 [开发状态](docs/DEVELOPMENT_STATUS.md)。

## 本地运行前提

1. 安装 Java 21 和 MySQL。
2. 创建数据库 `career_platform`，字符集使用 `utf8mb4`，排序规则使用 `utf8mb4_unicode_ci`。
3. 在该数据库中执行 [`sql/001_create_app_user.sql`](sql/001_create_app_user.sql)。
4. 通过环境变量提供数据库凭证：
   - `DB_PASSWORD`：必填。
   - `DB_USERNAME`：可选，默认值为 `root`。
5. 执行 `./mvnw spring-boot:run`；Windows PowerShell 可执行 `.\mvnw.cmd spring-boot:run`。

任何真实数据库密码、Token 或 API Key 都不得提交到 Git。文档和示例中也只应使用环境变量名或占位符。

## 文档导航

- [项目规划](docs/PROJECT_PLAN.md)：业务范围、版本路线、预期规模和风险降级。
- [开发状态](docs/DEVELOPMENT_STATUS.md)：严格区分已完成、正在进行和尚未开始。
- [系统架构](docs/ARCHITECTURE.md)：当前技术基线、目标分层和 AI 写入边界。
- [数据库说明](docs/DATABASE.md)：当前真实表结构、命名映射和候选表规划。
- [原始开工方案](docs/reference/大学生职业发展与求职管理平台_项目开工方案_v1.md)：保留项目最初的完整范围和初始设计基线，不作为当前实时维护文档；当前实际状态以真实代码和上述持续维护文档为准。
