# 数据库说明

## 已验证基线

- MySQL，数据库 `career_platform`
- InnoDB、`utf8mb4`、`utf8mb4_unicode_ci`
- `BIGINT AUTO_INCREMENT` 主键
- 数据库密码只从 `${DB_PASSWORD}` 读取

截至 2026-09-03，测试通过 JDBC 连接真实 MySQL 并查询 `information_schema`，确认 Milestone 2 的 12 张新增表、14 个外键和 4 个关键唯一索引已经存在。业务测试也真实经过 Controller、Service、Mapper 和 MySQL；没有使用 H2。

## SQL 文件与实际应用状态

| 文件 | 内容 | 状态 |
|---|---|---|
| `sql/001_create_app_user.sql` | 用户表 | 已应用 |
| `sql/002_create_shared_profile_tables.sql` | 共享档案 7 表 | 已应用并验证 |
| `sql/003_create_career_exploration_tables.sql` | 职业探索 5 表 | 已应用并验证 |

脚本均使用 `CREATE TABLE IF NOT EXISTS`，不会删除表或业务数据。已有历史脚本没有被覆盖。

## 当前表与关系

### 用户与共享档案

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `app_user` | 用户根实体 | `username` UNIQUE |
| `user_profile` | `app_user` 1 → 0..1 | `user_id` UNIQUE、FK → `app_user` |
| `education_experience` | 用户 1 → n | FK → `app_user`；`(user_id, id)` 查询索引 |
| `skill` | 全局技能字典 | `name` UNIQUE |
| `user_skill` | 用户 n ↔ n 技能的关联 | `(user_id, skill_id)` UNIQUE；两个 FK；`skill_id` 索引 |
| `project_experience` | 用户 1 → n | FK → `app_user`；`(user_id, id)` 查询索引 |
| `internship_experience` | 用户 1 → n | FK → `app_user`；`(user_id, id)` 查询索引 |
| `certificate_award` | 用户 1 → n | FK → `app_user`；`(user_id, id)` 查询索引 |

### 职业探索

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `career_goal` | 用户 1 → n | FK → `app_user`；`(user_id, status, updated_at)` |
| `company` | 用户 1 → n | FK → `app_user`；`(user_id, name)`；`(id, user_id)` UNIQUE 供复合 FK 使用 |
| `job` | 用户 1 → n；Company 1 → n | FK → `app_user`；`(company_id, user_id)` 复合 FK → `company(id, user_id)`；归档与公司索引 |
| `job_requirement` | Job 1 → n | FK → `job`；可选 FK → `skill`；Job 和 Skill 索引 |
| `job_note` | Job 1 → n | FK → `job`；`(job_id, created_at)` |

`job` 保留 `user_id`，使岗位本身明确知道 owner，并让数据库直接阻止跨用户 Company 关联。`job_requirement` 和 `job_note` 通过父 Job 继承归属，应用层在操作子资源前先验证父 Job 的 owner。

## 有限集合

- `app_user.status`：`ACTIVE`、`DISABLED`
- `user_skill.proficiency`：`BEGINNER`、`FAMILIAR`、`PROFICIENT`
- `certificate_award.type`：`CERTIFICATE`、`AWARD`
- `career_goal.status`：`ACTIVE`、`PAUSED`、`ACHIEVED`
- `job.job_type`：`FULL_TIME`、`INTERNSHIP`、`CAMPUS`、`PART_TIME`、`CONTRACT`、`OTHER`
- `job.source_type`：`MANUAL`、`CAMPUS_SITE`、`COMPANY_WEBSITE`、`RECRUITMENT_PLATFORM`、`REFERRAL`、`OTHER`
- `job_requirement.requirement_type`：`SKILL`、`EDUCATION`、`MAJOR`、`EXPERIENCE`、`LANGUAGE`、`OTHER`

这些值由 Java enum 和业务校验约束；当前 SQL 没有额外 CHECK 约束。

## 数据安全与迁移原则

- 只保存 BCrypt 密码哈希，不保存明文密码。
- `DB_PASSWORD`、`JWT_SECRET` 不写入 SQL、源码或文档。
- 字段使用 Java 小驼峰与数据库下划线自动映射，Entity 和 SQL 已通过真实业务读写测试交叉验证。
- 后续结构变化应新增有序迁移脚本，不应改写已经执行过的脚本语义。
- Learning、Resume、Application 等后续表仍是规划，未写成当前真实结构。
