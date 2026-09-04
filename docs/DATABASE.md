# 数据库说明

## 已验证基线

- MySQL，数据库 `career_platform`
- InnoDB、`utf8mb4`、`utf8mb4_unicode_ci`
- `BIGINT AUTO_INCREMENT` 主键
- 数据库密码只从 `${DB_PASSWORD}` 读取

截至 2026-09-04，真实 MySQL `information_schema` 查询确认数据库共有 19 张 `BASE TABLE`：`app_user`、Milestone 2 的 12 张新增表和 Milestone 3 Learning 的 6 张表。业务测试真实经过 Controller、Service、Mapper 和 MySQL；没有使用 H2。

## SQL 文件与实际应用状态

| 文件 | 内容 | 状态 |
|---|---|---|
| `sql/001_create_app_user.sql` | 用户表 | 已应用 |
| `sql/002_create_shared_profile_tables.sql` | 共享档案 7 表 | 已应用并验证 |
| `sql/003_create_career_exploration_tables.sql` | 职业探索 5 表 | 已应用并验证 |
| `sql/004_create_learning_tables.sql` | Learning 6 表 | 已应用并验证 |

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

### Learning

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `learning_plan` | `app_user` 1 → n | `(user_id, week_start)` UNIQUE；`(id, user_id)` UNIQUE；owner FK |
| `learning_task` | Plan 1 → n | `(id, user_id)` 与 `(id, plan_id, user_id)` UNIQUE；`(plan_id, user_id)` 索引；owner FK |
| `study_record` | Task 1 → n | `(task_id, user_id)` 索引；Task owner 复合 FK |
| `weekly_review` | Plan 1 → 0..1 | `plan_id` UNIQUE；Plan owner 复合 FK |
| `learning_note` | Plan 1 → n；可选 Task | `(plan_id, user_id)`、`(task_id, plan_id, user_id)` 索引；Plan/Task owner 复合 FK |
| `learning_material` | Plan 1 → n；可选 Task | `(plan_id, user_id)`、`(task_id, plan_id, user_id)` 索引；`source_url VARCHAR(2048) NULL`；Plan/Task owner 复合 FK |

Learning 共 13 个外键，全部为默认 `RESTRICT`（MySQL metadata 的 `NO ACTION`）：各表直接保存 `user_id`，并通过 Plan/Task owner-aware 复合外键阻止跨用户或错误父子组合。Service 负责校验 owner、日期和删除顺序，不依赖数据库异常替代业务校验。

Learning 共 5 个非主键 UNIQUE：`uk_learning_plan_user_week_start`、`uk_learning_plan_id_user_id`、`uk_learning_task_id_user_id`、`uk_learning_task_id_plan_id_user_id`、`uk_weekly_review_plan_id`。其中 owner-aware 目标键用于复合 FK，Review 唯一键保证一个 Plan 至多一份复盘。

## 有限集合

- `app_user.status`：`ACTIVE`、`DISABLED`
- `user_skill.proficiency`：`BEGINNER`、`FAMILIAR`、`PROFICIENT`
- `certificate_award.type`：`CERTIFICATE`、`AWARD`
- `career_goal.status`：`ACTIVE`、`PAUSED`、`ACHIEVED`
- `job.job_type`：`FULL_TIME`、`INTERNSHIP`、`CAMPUS`、`PART_TIME`、`CONTRACT`、`OTHER`
- `job.source_type`：`MANUAL`、`CAMPUS_SITE`、`COMPANY_WEBSITE`、`RECRUITMENT_PLATFORM`、`REFERRAL`、`OTHER`
- `job_requirement.requirement_type`：`SKILL`、`EDUCATION`、`MAJOR`、`EXPERIENCE`、`LANGUAGE`、`OTHER`
- `learning_plan.status`：`PLANNED`、`IN_PROGRESS`、`COMPLETED`
- `learning_task.status`：`TODO`、`IN_PROGRESS`、`DONE`、`SKIPPED`

这些值由 Java enum 和业务校验约束；当前 SQL 没有额外 CHECK 约束。

## 数据安全与迁移原则

- 只保存 BCrypt 密码哈希，不保存明文密码。
- `DB_PASSWORD`、`JWT_SECRET` 不写入 SQL、源码或文档。
- 字段使用 Java 小驼峰与数据库下划线自动映射，Entity 和 SQL 已通过真实业务读写测试交叉验证。
- 后续结构变化应新增有序迁移脚本，不应改写已经执行过的脚本语义。
- Resume、Application 等后续表仍是规划，未写成当前真实结构；Learning 六表已由 004 脚本应用并通过 Schema 集成测试。
