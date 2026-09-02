# 数据库说明

## 当前数据库基线

- 数据库产品：MySQL
- 数据库名称：`career_platform`
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_unicode_ci`
- 当前已确认存在的业务表：仅 `app_user`

其余表名均属于候选规划，尚未创建。本文不会为尚未实现的表虚构字段、索引、外键或约束。

“已确认”依据是本项目当前提供的真实数据库基线。本次文档任务没有使用数据库凭证连接实时 MySQL，也没有重新执行 `SHOW CREATE TABLE`；当前结构已经与 `AppUser` 和初始化 SQL 交叉核对。

## 当前 `app_user` 表

| 字段 | 类型 | 可空 | 默认值 / 属性 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 主键、自增 | 用户主键 |
| `username` | `VARCHAR(50)` | 否 | 唯一 | 用户名 |
| `password_hash` | `VARCHAR(255)` | 否 | 无 | 密码哈希，不保存明文密码 |
| `status` | `VARCHAR(20)` | 否 | `'ACTIVE'` | Java 枚举当前对应 `ACTIVE`、`DISABLED` |
| `created_at` | `DATETIME` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `DATETIME` | 否 | `CURRENT_TIMESTAMP`，更新时自动刷新 | 更新时间 |

当前结构只包含上述六个字段。`email`、`phone`、`role`、`deleted`、`version` 等字段尚未确定，因此没有加入初始化 SQL。

`status` 当前仍是普通 `VARCHAR(20)`，表结构没有额外的 `CHECK` 或数据库枚举约束；`ACTIVE`、`DISABLED` 是现有 Java `UserStatus` 的取值，不应误写成数据库已强制限制。

## Java 与数据库命名映射

Java 属性使用小驼峰，数据库字段使用下划线：

| Java | 数据库 |
|---|---|
| `passwordHash` | `password_hash` |
| `createdAt` | `created_at` |
| `updatedAt` | `updated_at` |

当前计划使用 MyBatis-Plus 的下划线到驼峰自动映射。`AppUser` 已通过 `@TableName("app_user")` 指定表名，`id` 已配置为自增主键。Mapper 尚未创建，因此 MyBatis-Plus → MySQL 的真实查询和枚举读写链路仍待验证。

## 初始化 SQL

当前表结构脚本位于 [`sql/001_create_app_user.sql`](../sql/001_create_app_user.sql)。执行前需要先创建并选择 `career_platform` 数据库；脚本中也使用 `USE career_platform` 明确目标数据库。

脚本采用 `CREATE TABLE IF NOT EXISTS`，用途是让同一份初始化脚本可以在表已经存在时重复执行而不因“表已存在”直接失败。它只负责创建缺失的表：

- 不会删除现有数据。
- 不会覆盖现有表。
- 不会自动把旧结构迁移成本文结构。
- 不会检查已存在表是否与期望结构完全一致。

后续若出现字段变更，应新增有序迁移脚本，而不是悄悄改写已经执行过的历史迁移语义。

## 候选表规划

初始设计基线规划约 28 张核心业务表，当前候选名称如下。除 `app_user` 外，均尚未建表。

### 共享基础档案

- `app_user`
- `user_profile`
- `education_experience`
- `skill`
- `user_skill`
- `project_experience`
- `internship_experience`
- `certificate_award`

### 职业探索与目标管理

- `career_goal`
- `company`
- `job`
- `job_requirement`
- `job_note`

第一版计划由 `job` 字段保存岗位来源。`company` 独立，`job` 引用 `company`。具体字段和约束尚未设计完成。

### 学习提升管理

- `learning_plan`
- `learning_task`
- `study_record`
- `weekly_review`
- `learning_note`
- `learning_material`

已冻结的关系方向为：`LearningPlan` 1 → n `LearningTask`，`LearningTask` 1 → n `StudyRecord`，`LearningPlan` 1 → 0..1 `WeeklyReview`。学习笔记与学习资料分别建模。

### 简历管理

- `resume`
- `resume_version`
- `resume_content_item`

`Resume` 1 → n `ResumeVersion`，`ResumeVersion` 由多个 `ResumeContentItem` 组成。定稿版本需要保留历史快照，不随共享档案变化。

### 求职过程管理

- `application`
- `application_stage_history`
- `assessment`
- `interview`
- `offer`
- `final_review`

`Application` 保存当前阶段及阶段历史；测评和面试独立；一个申请最多一个 Offer 和一个最终复盘。具体外键、唯一约束、并发控制和归档字段仍需后续详细设计，当前不写成已确定的数据库约束。

### P2 技术数据

RAG 可能增加 `document_chunk` 等检索相关结构。该部分尚未设计，也不计入当前真实表。

## 数据安全

- 数据库只保存密码哈希，不保存明文密码。
- 数据库密码通过必填的 `DB_PASSWORD` 环境变量提供。
- 数据源用户名使用 `${DB_USERNAME:root}`，`DB_USERNAME` 可选且默认 `root`；重复文本问题已经修复。
- JDBC URL 当前显式写有 `characterEncoding=utf8`，应在真实 SQL 查询链路建立后核验其与 `utf8mb4` 数据库的连接行为；当前不据此断言连接字符集存在问题。
- 真实密码、Token、API Key 和其他凭证不得写入 SQL、Markdown 或 Git。
