# 数据库说明

## M7 / P2-A 增量（MySQL Gate PASS）

M7 已完成并冻结，状态为 `FROZEN / COMMITTED / PUSHED`；feature checkpoint 为 `6e4db93ccae7b4efc9530c8950926d257eb21aaf`，已 push 到 `origin/main`。MySQL 验收结果为 checkpoint 前实际证据，本次未再次执行迁移或测试。

新增一次性迁移 `sql/007_add_learning_material_rag.sql`：扩展 learning_material 的原件 BLOB、文件名/MIME/大小、索引状态、Chunk 数与 Embedding 身份；新增 `learning_material_chunk` 技术表。M7 冻结时为 28 张业务表 + 1 张 RAG 技术表，共 29 张；当前源码再加入 `sql/009_add_resume_file.sql` 后为 29 张业务表 + 1 张技术表，共 30 张 DDL 表。用户 IDEA 最新 MySQL Gate 已验证 007 结构和事务行为。

Chunk 保存 user_id、material_id、chunk_index、text、location_label、可空 page_number、JSON embedding、embedding_identity、created_at。`UNIQUE(material_id,chunk_index)` 保证文档顺序唯一；复合 FK `(material_id,user_id) → learning_material(id,user_id) ON DELETE CASCADE` 阻止跨 owner 子记录，并让原有 Material/Plan 删除事务清理 Chunk。其他既有 Learning FK 不变。

上传的 metadata 与原件同事务提交，索引全部 Chunk 与 READY 同事务提交。文件不支持原地替换，重新索引使用数据库原件；失败不提交半套 READY；已有索引重建失败保留旧数据。删除直接移除同一数据库中的 BLOB，不存在待删除磁盘文件。普通列表查询通过 `@TableField(select=false)` 排除 BLOB，原件仅通过显式 owner-scoped SELECT 读取。

007 的 ALTER TABLE 只执行一次；勿盲目重复执行，本轮最新 Gate 后未重复执行 007。执行及集成证据见 [M7 Closing](M7_RAG_CLOSING_VERIFICATION.md)，下方 M6 与更早规模为历史证据。

本轮用户 IDEA 实测已由主控读取 XML 核验：最新指定套件共 30 项且全绿，`DatabaseSchemaIntegrationTests` 5、`LearningCoreIntegrationTests` 11、`LearningSupportIntegrationTests` 6、`RagMaterialIntegrationTests` 8，Failures/Errors/Skipped 均为 0，`BUILD SUCCESS` 46.750s，证据为 `target/m7-mysql-confirmed-result.json`。第一次 30 项结果中旧全局表数 expected 28/actual 29 的 1 failure 继续保留于 `target/m7-mysql-first-result.json`；机械确认新增第 29 张表为 `learning_material_chunk` 后修正计数与方法名，未修改数据库迁就旧断言。

## 当前增量：Resume File（COMMITTED / NOT PUSHED）

`sql/009_add_resume_file.sql` 已包含在本地 feature checkpoint `b44993f58449f38860973c497e3e6f9315ff4895`，新增业务表 `resume_file`，为每个 `ResumeVersion` 保存至多一个原始 PDF/DOCX 文件。列包括 `user_id`、`resume_version_id`、`original_filename`、`content_type`、`file_size`、`file_data MEDIUMBLOB`、`created_at` 与 `updated_at`；`UNIQUE(resume_version_id)` 保证版本与文件 0..1，`(resume_version_id,user_id)` → `resume_version(id,user_id)` owner-aware composite FK 防止跨用户关联，另有 `user_id` → `app_user` 外键和查询索引。该脚本没有 `ON DELETE CASCADE`，Service 按 FK 顺序显式删除子行。

本轮未执行 `009`，也不把源码和本地 commit 中存在脚本等同于目标数据库已经迁移。`007` 的“一次执行”是 M7 Closing 的历史 MySQL Gate 事实；`008`、`009` 的目标数据库应用状态均需在备份和确认 schema 后由使用者按顺序检查/执行，不能由本段推断。

## 已验证基线

- MySQL，数据库 `career_platform`
- InnoDB、`utf8mb4`、`utf8mb4_unicode_ci`
- `BIGINT AUTO_INCREMENT` 主键
- 数据库密码只从 `${DB_PASSWORD}` 读取

历史基线（截至 2026-09-04）：真实 MySQL `information_schema` 确认有 28 张 `BASE TABLE`，即原有 22 张加 Milestone 5B Application 的 6 张表。006 已通过 login-path 连续应用两次并验证幂等；Application 定向集成测试 8 项与 Schema 测试 4 项共 12 项，以及全量 Maven test 90 项，均为 Failures 0、Errors 0、Skipped 0。全程不使用 H2。M7 冻结时为 29 张 DDL；当前源码清单为 29 张业务表 + 1 张技术表，共 30 张，最新 M7 30 项 Gate 仍为历史证据。

Milestone 6A 不新增 migration 或业务表。JD AI parse candidate 只存在于响应和前端审核状态；用户确认后仍写入既有 `job_requirement`，且不会自动删除/覆盖人工条目。因此业务表数量保持 28。通过 IDEA 运行配置启动的 localhost application 已完成真实 MySQL business smoke，确认 parse 不写要求、confirm 才追加要求；最终真实 MySQL full Maven 为 132 项、Failures 0、Errors 0、Skipped 0。

Milestone 6B 已冻结，状态为 `FROZEN / COMMITTED / PUSHED`，功能 checkpoint 为 `805a3801af76e4e88434e52e154d2069ad3c4d1b`，已 push 到 `origin/main`。同样不新增 migration、业务表或 AI table。Plan/Review suggestion 都是 ephemeral；用户确认 Plan 后只在一个事务中写入既有 `learning_plan` 与 `learning_task`，正式 Review 继续写既有 `weekly_review`。源码 schema 仍为 28 张业务表。以下为 checkpoint 前的 Closing 历史证据，本次未重跑数据库测试或 Provider smoke：用户已通过 IDEA Full Maven Test 取得真实 MySQL 155 项全绿，事务集成类 3/3 PASS，包含部分写入后完整回滚验证；Plan/Review 六表整行快照断言通过。Astra 对用户 IDEA backend 实际执行两条真实 Flash smoke，owner 资源 HTTP 快照 delta 均为 0，已有 Review 未被覆盖；HTTP 与直接 SQL 证据分别记录于 Closing 报告。

## SQL 文件与实际应用状态

### M6C Job Discovery

M6C 不增加 migration 或表，数据库结构继续复用现有 Job/Company。候选只存在于有容量上限和 15 分钟 TTL 的内存 store，重启后丢失；没有搜索历史表。Discovery 读取 owner-owned CareerGoal/UserSkill，不写 Job、Company、JobRequirement、CareerGoal 或 UserSkill。

显式 confirm 在 Java 中以 candidateId 找回来源事实，并在事务中复用 `CareerService.createJob()`：companyId 必须属于当前用户，jobType/title/city 由用户确认；sourceName 是原网页 host，sourceUrl 是搜索结果原始 URL；publishDate 仅映射 Provider 明确提供且可安全解析的日期，否则 null。用户未粘贴完整 JD 时 rawJd 为 null，不能拿 snippet 代替。数据库事务提交成功后才消费候选，失败保留候选供重试。

没有新增 `UNIQUE(sourceUrl)`；同一候选的重复确认受一次性消费保护，不同 discovery 找到同一 URL 仍可能被用户分别保存。2026-09-06 Closing 本轮实际执行 JobDiscoveryControllerIntegrationTests：3 tests / 0 failures / 0 errors / 0 skipped，真实 MySQL connection 已建立。覆盖 owner isolation、deterministic discovery no-write、trusted confirm 与 concurrent confirm；未运行 Full Maven。Smoke #4 的 HTTP owner-scoped Job/Company 数量均 0→0，属于独立真实 Provider 证据。详细证据见 [M6C Closing](M6C_CLOSING_VERIFICATION.md)。

### 既有迁移

| 文件 | 内容 | 状态 |
|---|---|---|
| `sql/001_create_app_user.sql` | 用户表 | 已应用 |
| `sql/002_create_shared_profile_tables.sql` | 共享档案 7 表 | 已应用并验证 |
| `sql/003_create_career_exploration_tables.sql` | 职业探索 5 表 | 已应用并验证 |
| `sql/004_create_learning_tables.sql` | Learning 6 表 | 已应用并验证 |
| `sql/005_create_resume_tables.sql` | Resume 3 表 | 已通过 login-path 幂等应用并验证 |
| `sql/006_create_application_tables.sql` | Application Management 6 表 | 已通过 login-path 连续应用两次并验证 |
| `sql/007_add_learning_material_rag.sql` | LearningMaterial 原件字段与 `learning_material_chunk` | 已执行一次并由 M7 MySQL Gate 验证 |
| `sql/008_add_profile_email.sql` | `user_profile.email` 有序增量 | 脚本存在；本轮未执行或验证目标数据库状态 |
| `sql/009_add_resume_file.sql` | `resume_file` 原始文件表 | 已在 feature branch 本地提交；本轮未执行或验证目标数据库状态 |

`CREATE TABLE` 脚本使用 `IF NOT EXISTS`，不会删除表或业务数据；007、008 属于一次性 `ALTER TABLE`，009 为当前新增建表脚本，均不应盲目重复执行。已有历史脚本没有被覆盖。

## 当前表与关系

当前 DDL inventory 为 29 张业务表（含 `app_user` 与当前增量 `resume_file`）+ 1 张技术表 `learning_material_chunk`，合计 30 张；技术表不计入业务表数量。

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

### M7 RAG 技术表

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `learning_material_chunk` | LearningMaterial 1 → n | `UNIQUE(material_id, chunk_index)`；`(material_id,user_id)` 复合 FK → `learning_material(id,user_id)`，`ON DELETE CASCADE`；保存位置、可空页码、原文、JSON embedding 与 identity |

该技术表使源码 DDL 总数由 M6 历史的 28 张增加到 M7 的 29 张；Resume File checkpoint 再加入 `resume_file`，因此清单总数为 30 张。原件 BLOB、索引状态与 Chunk 事务行为由最新 MySQL Gate 验证；普通资料列表不读取 BLOB，删除由数据库级联清理 Chunk。

Learning 共 5 个非主键 UNIQUE：`uk_learning_plan_user_week_start`、`uk_learning_plan_id_user_id`、`uk_learning_task_id_user_id`、`uk_learning_task_id_plan_id_user_id`、`uk_weekly_review_plan_id`。其中 owner-aware 目标键用于复合 FK，Review 唯一键保证一个 Plan 至多一份复盘。

### Resume

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `resume` | `app_user` 1 → n | `user_id` FK → `app_user`；`(id, user_id)` UNIQUE 供复合 FK 使用 |
| `resume_version` | Resume 1 → n | `(resume_id, version_no)` UNIQUE；`(id, user_id)` UNIQUE；`(resume_id, user_id)` 复合 FK → `resume(id, user_id)`；owner FK → `app_user` |
| `resume_content_item` | ResumeVersion 1 → n | `(version_id, user_id)` 复合 FK → `resume_version(id, user_id)`；owner FK → `app_user` |
| `resume_file` | ResumeVersion 1 → 0..1 | `UNIQUE(resume_version_id)`；`file_data MEDIUMBLOB` 与文件 metadata；`(resume_version_id, user_id)` 复合 FK → `resume_version(id, user_id)`；owner FK → `app_user` |

Resume 关系固定为 `Resume 1 → n ResumeVersion`，每个 Version 有 `0..n ResumeContentItem`，并可有 `0..1 resume_file`；Application 仍只允许绑定已 `FINALIZED` 的历史 ResumeVersion。Resume 的 owner-aware 复合外键按父键顺序保存列：`(resume_id, user_id)`、`(version_id, user_id)` 与 `(resume_version_id, user_id)`。四张表均为 InnoDB、`utf8mb4_unicode_ci`，默认外键删除规则为 `RESTRICT`（MySQL metadata 显示 `NO ACTION`）。2026-09-07 Closing 的 Schema 集成 artifact 已验证四张 Resume 表、关键复合 FK 列序、版本号与文件唯一键、表 metadata 和 Mapper；这是历史 evidence，非本轮执行结果。

### Application Management

| 表 | 归属 / 关系 | 关键约束与索引 |
|---|---|---|
| `application` | 用户、Job、FINALIZED ResumeVersion | `(id,user_id)` UNIQUE；`(user_id,ongoing_job_id)` UNIQUE；Job FK；ResumeVersion owner 复合 FK；用户/阶段与 Job 查询索引 |
| `application_stage_history` | Application 1 → n | `(application_id,user_id)` owner 复合 FK；按 changed_at/id 追加查询索引 |
| `assessment` | Application 1 → n | Application owner 复合 FK；scheduled_at 查询索引 |
| `interview` | Application 1 → n | Application owner 复合 FK；round_no 查询索引 |
| `offer` | Application 1 → 0..1 | `application_id` UNIQUE；Application owner 复合 FK |
| `final_review` | Application 1 → 0..1 | `application_id` UNIQUE；Application owner 复合 FK |

`application.ongoing_job_id` 是 STORED generated column：进行中时等于 `job_id`，ENDED 时为 NULL。MySQL UNIQUE 允许多行 NULL，因此同一用户同一岗位最多一条 ongoing，同时可保留多条 ENDED 历史。Service 的 Job 行锁负责将同一父资源的创建与删除顺序化，生成列唯一键负责拦截遗漏或竞争条件下的重复写入，事务负责让 Application/Offer 状态与 StageHistory 原子提交。

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
- `resume_version.status`：`DRAFT`、`FINALIZED`
- `resume_content_item.section_type`：`PROFILE`、`EDUCATION`、`SKILL`、`PROJECT`、`INTERNSHIP`、`CERTIFICATE`
- `resume_content_item.source_type`：`PROFILE`、`EDUCATION`、`SKILL`、`PROJECT`、`INTERNSHIP`、`CERTIFICATE`（手工内容为空）
- `application.current_stage`：`APPLIED`、`ASSESSMENT`、`INTERVIEW`、`OFFER`、`ENDED`
- `offer.status`：`CONSIDERING`、`ACCEPTED`、`REJECTED`
- `assessment.type`：`ONLINE_ASSESSMENT`、`WRITTEN_TEST`、`CODING_TEST`、`OTHER`
- `assessment.result`：`PENDING`、`PASSED`、`FAILED`、`OTHER`
- `interview.type`：`HR`、`TECHNICAL`、`MANAGER`、`FINAL`、`OTHER`
- `interview.result`：`PENDING`、`PASSED`、`REJECTED`、`OTHER`

这些值由 Java enum 和业务校验约束；当前 SQL 没有额外 CHECK 约束。

## 数据安全与迁移原则

- 只保存 BCrypt 密码哈希，不保存明文密码。
- `DB_PASSWORD`、`JWT_SECRET` 不写入 SQL、源码或文档。
- 字段使用 Java 小驼峰与数据库下划线自动映射，Entity 和 SQL 已通过真实业务读写测试交叉验证。
- 后续结构变化应新增有序迁移脚本，不应改写已经执行过的脚本语义。
- 005 是 Resume 的新增有序迁移脚本；006 是 Application Management 的新增有序迁移脚本，六张 Application 表已应用并通过 Schema 集成测试。Learning 六表已由 004 脚本应用并通过 Schema 集成测试。007 是 M7 历史上执行一次的 RAG 迁移；008 与 009 为后续有序脚本，本轮未执行，不把文档同步当作迁移完成证明。
