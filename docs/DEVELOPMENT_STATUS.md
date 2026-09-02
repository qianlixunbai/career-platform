# 开发状态

> 更新日期：2026-09-02。本文只记录当前真实完成度。项目长期范围和候选设计见[项目规划](PROJECT_PLAN.md)。

## 已完成

- 已创建 Spring Boot Maven 项目和 Maven Wrapper。
- `pom.xml` 已将 Java 版本配置为 21，Spring Boot 父版本为 3.5.14。
- 已引入 MyBatis-Plus 3.5.17，并从原先规划的 JPA / Repository 方向切换为 MyBatis-Plus / Mapper。
- 已引入 Spring Web、Bean Validation 和 MySQL 驱动等基础依赖。
- 按当前已确认的项目数据库基线，MySQL 中已创建数据库 `career_platform`，字符集为 `utf8mb4`，排序规则为 `utf8mb4_unicode_ci`。
- 已建立数据源基础配置，数据库密码从 `DB_PASSWORD` 环境变量读取，仓库中未写入真实密码。
- 按当前已确认的项目数据库基线，已创建当前唯一一张真实业务表 `app_user`。
- 已创建与该表对应的 `AppUser` Entity，包含数据库映射所需字段和 Getter / Setter。
- 已创建 `UserStatus` 枚举，当前只有 `ACTIVE` 和 `DISABLED`。
- Spring Boot 项目已完成过启动验证；这不等同于 MyBatis-Plus → MySQL 真实查询链路已经通过。
- Git 仓库已初始化，当前分支为 `main`，远程仓库为 `qianlixunbai/career-platform`。

### 已知配置差异

当前 `application.properties` 中的实际内容为：

```properties
spring.datasource.username=spring.datasource.username=${DB_USERNAME:root}
```

该行比预期写法多了一段重复文本。因此，“`DB_USERNAME` 可选且默认 `root`”是当前配置意图，但尚不能视为已经正确验证的能力；它可能影响后续 MyBatis-Plus 到 MySQL 的真实查询链路。本次任务只整理文档，不修改应用配置。

此外，数据库基线使用 `utf8mb4`，而 JDBC URL 当前显式配置为 `characterEncoding=utf8`。这不改变已经创建的数据库字符集，但连接字符集行为仍需在真实查询链路中核验并按结果对齐。

本次文档任务没有使用数据库凭证连接实时 MySQL，也没有重新执行 `SHOW CREATE TABLE`。上述数据库和表的“已创建”状态来自当前已确认的项目基线；初始化 SQL、Entity 和维护文档已按该基线相互核对。

## 正在进行

- 用户与共享基础档案模块的详细设计和实现。
- 当前仍处于第一张表和 Entity 阶段，尚未形成完整用户业务链路。
- 数据库候选表、关系、约束和模块 API 仍需逐步详细设计。

## 尚未开始

- Mapper。
- Service。
- Controller。
- 用户注册。
- 用户登录。
- JWT。
- Spring Security。
- 除 `app_user` 以外的共享基础档案表和业务实现。
- 职业探索与目标管理模块。
- 学习提升管理模块。
- 简历管理模块。
- 求职过程管理模块。
- Vue 3 前端工程和页面。
- Spring AI 集成和所有 AI 功能。
- RAG。
- Agent + Tool Calling。
- AI Evaluation / A-B 实验。

## 下一步

近期应只推进最小可验证链路：

1. 单独修正并验证数据源用户名配置，同时核验 JDBC 连接字符集。
2. 创建 `AppUserMapper`。
3. 通过真实查询验证 MyBatis-Plus → MySQL 链路。
4. 在查询链路通过后，再详细设计用户模块的 Service 和 API。

## 当前日志说明

启动时出现 `No MyBatis mapper was found`，原因是仓库当前尚未创建任何 Mapper。这与现阶段代码状态一致，不代表 MyBatis-Plus 依赖或项目初始化失败。创建首个 Mapper 后再重新检查该提示。
