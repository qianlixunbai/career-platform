# Milestone 6B — Closing Verification Report

日期：2026-09-05。Closing Reviewer：Astra。

## 1. 当前结论

**GO — READY FOR M6B CHECKPOINT / NOT COMMITTED / NOT PUSHED。**

M6B 独立审计、必要测试修正、真实 MySQL Full Maven 与两条真实 DeepSeek Flash smoke 均已完成。用户通过 IDEA 提供全量 **155 / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS**，事务集成类 **3/3 PASS**；Astra 随后对用户启动的 localhost backend 实际执行 Plan/Review smoke，各一次且均 PASS。最终机械检查通过，两个 P1 验证门禁均已关闭。

- 已确认的生产缺陷：P0 = 0、P1 = 0。
- 未关闭的 P1 验证门禁 = 0。
- P2 = 4 项边界问题，另保留 Vite 主 chunk、Mockito future-JDK 两项既有警告；按本轮范围未修改这些生产行为。
- 本轮未 commit、未 push、未启动 M6C。Ready for Checkpoint 不代表已经建立或冻结 M6B checkpoint。

## 2. 实际 Agent 编排与独立审计

主控 Astra 是唯一 Tech Lead / Closing Reviewer。创建了以下三个 Worker，创建请求均指定 `gpt-5.6-luna` 与 `max`；工具结果没有提供独立的模型运行遥测，因此这里记录可核查的创建参数与工作结果，不声称有额外模型证明。

| Worker | 工作 | 结果 |
|---|---|---|
| backend_review | 后端 ownership、evidence、预算、事务、metrics、安全边界，只读 | 未发现已确认生产 P0/P1；发现 null task 请求的 P2 |
| test_config_review | 配置和回归测试独立审计，随后按 Astra 分工补充测试，并复核 Astra 的注入修正 | 缺口已补；修正后的真实 MySQL 事务类由用户 IDEA 验证 3/3 PASS |
| frontend_docs_review | 候选编辑、事实/建议分离、Review 应用流程、文档和机械统计，只读 | 无明确 P0/P1；前端边界问题交 Astra 复核 |

Astra 自行阅读相关生产代码及真实 diff，复核 Worker 发现，运行测试、修正文档并作出本报告结论。未使用 Terra、外部 Codex CLI 或 PowerShell Agent fallback。首次读取附件同轮误读了一次 `using-superpowers/SKILL.md`，随后遵从用户禁令，未采用该技能流程，也未创建或操作其目录及计划文件。

## 3. 审计发现与本轮修改

### Closing 必需的测试缺口

1. 原 `LearningAiControllerIntegrationTests` 的 confirm 用例带测试级 `@Transactional`，非法批次在写入前被拒绝；`LearningServiceAiConfirmTest` 直接构造 Service。这些证据不能证明 Spring 事务真实提交，也不能证明第一条 Task 已写入后发生异常会回滚。
2. 原全局开关测试只匹配 properties 字符串，不能证明 Spring 对嵌套占位符的绑定和优先级。
3. 原 suggestion 无写入断言仅比较 Plan 数量或已有 Review 的 summary，未覆盖其他 Learning 行与字段变化。

整个 Closing 阶段只修改测试和文档，**production code 未修改**。中间一次用户 IDEA 全量结果为 154 项、1 failure、0 errors：新增事务测试对 abstract MyBatis Mapper 使用 Mockito `callRealMethod()`，未执行到预期 SQL 故障点。Astra 移除 Mapper spy，改用仅在该测试上下文注册的 MyBatis `Executor.update` 拦截器：第一次真实 SQL 后查询确认 Plan/Task 已存在，第二次 INSERT 入口确认同一 Spring 事务连接后抛出专属异常；事务外按实际 ID 查询零残留。增加无 DB 的装配检查，确认真实 Mapper、拦截器和 INSERT statement。该问题属于测试实现错误，修正后用户 IDEA 全量 155 项全绿，事务类 3/3 PASS。

| 本轮修改文件 | 原因 |
|---|---|
| `src/test/java/com/careerplatform/learning/LearningServiceAiConfirmTransactionIntegrationTests.java` | 无外层事务的 commit/duplicate/rollback、真实 MyBatis 故障注入、离线装配检查及专属用户清理 |
| `src/test/java/com/careerplatform/ai/DeepSeekFlashConfigurationTest.java` | 加载生产 properties，实际绑定默认值、legacy fallback、新开关优先级 |
| `src/test/java/com/careerplatform/ai/LearningAiControllerIntegrationTests.java` | 两类 suggestion 前后六张 Learning 表整行快照对比；逐任务 actualMinutes 断言 |
| `README.md`、`docs/ARCHITECTURE.md`、`docs/PROJECT_PLAN.md` | 当前 Controller 数 24，M6B 更新为 GO / READY FOR CHECKPOINT / NOT COMMITTED / NOT PUSHED |
| `docs/DEVELOPMENT_STATUS.md`、`docs/DATABASE.md` | 更新用户 IDEA MySQL 证据、Astra live smoke、已关闭门禁及证据来源 |
| `docs/M6B_CLOSING_VERIFICATION.md` | 本报告 |

`target/` 内的日志、机械审计、smoke 脚本及结果是 Git 忽略的工作产物，不含真实 secret。用户原有约 42 个 M6B 变更文件均保留。最终 live 阶段未修改生产代码或测试，只同步六份维护文档。

### 经 Astra 复核保留的 P2

| 问题 | 触发与影响 |
|---|---|
| Review 完成率单位判断错误 | `LearningPlanDetailView.vue` 的 `completionRateLabel` 将 `<=1` 再乘 100；Java 返回百分数，100 个任务中完成 1 个会把 1% 显示为 100%。通常的 2～6 任务候选不触发 |
| focusNote 长度不一致 | `LearningPlansView.vue` 允许 2000 字，后端最大 1000；1001～2000 字会收到 400，而页面显示 AI 暂不可用 |
| 已有 Review 提示依赖旧页面状态 | 提示使用本地 `review`，未使用新响应 `hasExistingReview`；初次查询失败或并发新增 Review 时可能漏提示。Apply 仍只改表单，保存仍需用户点击 |
| confirm 接受 null 列表元素进入映射 | `tasks:[null]` 未被元素级约束拦截，Controller 映射会抛 NPE，预期表现为 500；写入前失败，不产生半写 |

Worker 提出的“每任务 rationale 缺省显示通用建议文案”未被认定为 Closing 缺陷：当前契约要求 Plan-level rationale，未要求逐任务 rationale，且兜底内容没有被标为可信事实。

## 4. 生产边界审计

| 项目 | 静态/定向结论与证据边界 |
|---|---|
| AI configuration | `AI_CHAT_ENABLED` 为主；未设置才 fallback 到 `AI_JD_PARSE_ENABLED`。新开关 false 可覆盖 legacy true；provider/key 条件不变 |
| Provider | DeepSeek Official API，`https://api.deepseek.com`，OpenAI-compatible adapter；本轮通过用户启动的真实 backend 完成两条 authenticated smoke |
| Production model | properties 与 Gateway 固定 `deepseek-v4-flash`；实际 Prompt options 的 Flash/tool 禁用有 deterministic gateway 测试 |
| Pro calls | 0；两条 live 请求均通过固定 Flash 的 production Gateway，无 Pro 选择或调用路径 |
| Fallback / dynamic override | 无模型 fallback、无 Flash→Pro fallback、无 `AI_MODEL`、无客户端/query/header/DTO 选模；Gateway 使用固定 options |
| Business Tool Calling | 无业务 tools，`toolChoice=none`、`internalToolExecutionEnabled=false`。基础 ToolCallingManager 无业务注册 |
| RAG / Agent / Embedding / ChatMemory | 均未引入 |
| Plan suggestion contract | `POST /api/v1/learning-plans/ai/plan-suggestion`：周期、分钟预算、可选 Goal/Job/focus；返回 mainGoal、rationale、2～6 个 typed tasks、warnings、Java totals/buffer |
| Java task validation | 验证任务数量、标题/排序唯一、正分钟、dueDate 闭区间及总分钟不超预算；long 累加后在有效预算内转为 int |
| Owner isolation | Goal/Job 通过 owner-aware Service；Requirement 先校验父 Job owner；Learning 查询带 userId。越权在 Provider 调用前被拒绝；用户 IDEA Full Maven 已覆盖，live 只访问独立测试账号资源 |
| Evidence trust | 仅接受本次 Java map 中精确匹配的 key，unknown key 被过滤；每个 Plan task 至少一条有效 evidence。返回的 label/excerpt 来自 Java map，不信任模型 source text/DB ID |
| Facts vs AI Advice | UI 分区展示服务器来源与模型建议；模型文本不作为可信来源登记 |
| Context budget | Skill 30、Job 5、Requirement 每 Job 10/总 30、近期 Plan 2、Task 20、Record 30、Note 12、Review 2；单段约 300，Java context text 预算 12000。该数字是 evidence 文本计数，非完整转义后 prompt/token 长度 |
| Raw JD | Learning Prompt 只序列化白名单 evidence，不序列化 Job 对象/raw JD；相关 unit/prompt 测试通过 |
| Plan confirm transaction | `POST /api/v1/learning-plans/ai/plan-suggestion/confirm` 只调用 `LearningService.createPlanWithTasks`；先校验，再单事务写 Plan 与 Tasks，重复 week 拒绝。用户可编辑，确认可保留 1～6 个 Task；真实提交及部分写入后 rollback 由用户 IDEA 事务集成测试验证通过，live 未调用 confirm |
| Weekly Review metrics | Java 使用完整 owner 数据计算任务数、四类状态数、百分比、planned/actual minutes、记录数及逐任务实际分钟；AI DTO 不承载可覆盖 metrics 的字段 |
| Review no-auto-write | `POST /api/v1/learning-plans/{planId}/ai/review-suggestion` 返回候选，代码没有 Review 写路径。前端 Apply 只复制到表单，显式保存继续走既有 PUT |
| Prompt security | 业务文本视为 untrusted；禁止 role/schema override、prompt/secret disclosure、network/tool instructions；文本换行/XML 转义，来源 key 由 Java 构造 |
| AI disabled | 无 key 的 Spring Context 可启动，Gateway 失败关闭；传统 Learning Service 不依赖 Provider |

## 5. 验证记录及证据来源

| 验证 | 当前结果 | 证据来源 |
|---|---|---|
| Main/test compile | PASS，包括修正后的事务测试 | Astra/Codex，在本任务上一轮修复阶段运行 |
| M6B deterministic/foundation | 21 项 PASS | Astra/Codex，本任务修复阶段运行 |
| M6A JD prompt/service regression | 15 项 PASS | Astra/Codex，本任务修复阶段运行 |
| Learning AI 未认证 | 1 项 PASS | Astra/Codex，本任务修复阶段 MockMvc |
| MyBatis 注入装配检查 | 1 项 PASS | Astra/Codex，不访问 DB/Provider |
| 最新离线定向运行合计 | **38 / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS** | `target/m6b-transaction-fix-offline.log`；最终 live 阶段未重复运行 |
| Learning AI 6 项集成测试 | PASS，包含六表整行快照与已有复盘不变 | 已包含在用户 IDEA 全绿 Full Maven 中 |
| confirm 事务集成类 | **3/3 PASS**：装配、提交/重复拒绝、部分写入后回滚 | 用户 IDEA 明确提供的真实结果，其中两项操作 MySQL |
| Real MySQL Full Maven | **155 / 0 failures / 0 errors / 0 skipped，BUILD SUCCESS** | 用户通过 IDEA `Full Maven Test` 实际运行后提供统计；不是 Astra 自行运行 |
| Frontend typecheck | PASS | Astra/Codex，在本任务初始审计阶段实际运行；此后前端未改动 |
| Frontend build | PASS，保留既有 chunk warning | Astra/Codex，在本任务初始审计阶段实际运行；最终 live 阶段未重复构建 |
| Real Weekly Plan Provider smoke | **PASS**，3 tasks，270/300 分钟，buffer 30，13 条 trusted evidence | Astra 对用户 IDEA backend 实际执行，一次请求 |
| Real Weekly Review Provider smoke | **PASS**，全部指定 metrics 匹配，9 条 trusted evidence | Astra 对用户 IDEA backend 实际执行，一次请求 |
| Plan/Review candidate DB delta | 两次 live owner 资源快照 delta 均 0，已有 Review 未变；MySQL 六表快照断言通过 | HTTP 快照由 Astra 验证；直接 SQL 证据来自用户 IDEA 集成测试，二者分开记录 |
| Secret audit | PASS：跟踪文件及未忽略新文件规则扫描与 diff 审阅，未发现真实 API key/DB 密码/JWT secret/Authorization token | Astra/Codex；测试占位值不是真实凭据 |
| git diff --check | PASS | Astra/Codex |

历史 `151 / 0 failures / 97 errors` 是 Codex 环境的 MySQL 认证问题；随后用户 IDEA `154 / 1 failure / 0 errors` 暴露测试注入缺陷。两者均已被修正后用户提供的 **155 项全绿**结果取代。未将 97 个环境 errors 或 Mockito 故障升级为 production transaction bug。

## 6. Live smoke 实际执行

用户确认 Full Maven 全绿，并在 IDEA 启动 `localhost:8080` backend 后，Astra 执行 `node.exe target/m6b-live-smoke.mjs --run-live`，退出码 0。完成时间为 **2026-09-05 13:22:54（Asia/Shanghai）**。结果见 `target/m6b-live-smoke-result.json`。

- 两个业务 suggestion 请求各 1 次，脚本自动重试 0、AI confirm 0。生产模型固定 `deepseek-v4-flash`，Pro calls 0、模型 fallback 0、动态选模 0。
- 模型/Provider 依据为审核过的固定 Gateway、生产配置及经该 backend 的真实成功请求；报告不声称读取了供应商账单或独立逐请求模型遥测。脚本计数是应用请求次数，不冒充供应商请求日志。

- Plan：owner Goal、结构化 JobRequirement、历史 Learning 数据；typed response 通过，3 个任务，分钟为正、dueDate 在 2026-09-07 至 2026-09-13 内、标题及排序唯一、合计 270 <= 300、buffer 30。13 条 evidence 全部属于测试 fixture 允许的 Java 来源，每任务至少一条有效 evidence；请求前后 owner 资源快照一致。
- Review：typed response 通过，taskCount 4、DONE/TODO/IN_PROGRESS/SKIPPED 各 1、completionRate 25、plannedMinutes 300、actualMinutes 90、studyRecordCount 3、逐任务实际分钟 70/0/20/0。9 条 evidence 可追溯，`hasExistingReview=true`，原 Review 及其他 owner 资源快照均不变。
- 本地 HTTP GET 快照只能证明 API 可见 owner 资源未变化；真实 SQL 六表 delta 来自 Full Maven 集成测试，报告会区分两种证据。
- 已通过现有删除接口清理 Plan 716（及 Tasks/Records/Review）、Job 345（及 Requirement）、Company 400、Goal 60，均返回成功；没有操作真实用户数据。
- 应用没有删除账号的 API，保留独立空测试账号 `userId=1890`、`username=m6b_smoke_657a2f5ad567478c9215`，供后续精准清理。随机登录凭据和 token 仅存内存，未写文件或打印；不将“业务 fixture 已清理”表述为账号也已删除。

## 7. 机械统计、Git 与文档状态

- 业务表：SQL `CREATE TABLE` 与实体 `@TableName` 均为 **28**；真实 MySQL metadata 以 Full Maven 为准。
- `@RestController`：精确 annotation 扫描 **24**，排除 `@RestControllerAdvice`。
- Routed frontend pages：**20**。
- 正式 AI 功能：**2**（JD Structured Parse；AI Learning Planning + Weekly Review）；课程最低 3，仍缺至少 1。
- M6B migration：**0**；新增 AI table：**0**。
- Branch：`main`。
- HEAD：`058a46ebcf323b0f8cccbd3d7e749991d8a5336f`。
- 本地 `origin/main`：`058a46ebcf323b0f8cccbd3d7e749991d8a5336f`；本轮未 fetch 或 push。
- Working tree：预期 dirty，共 44 个变更文件（原 42 个，加两个 Closing 新文件）。live 执行前后原 44 个文件哈希完全一致，随后仅更新六份维护文档。未 reset/stash/clean/checkout。
- M6A：`FROZEN / COMMITTED / PUSHED`，功能 checkpoint `07712a687685e368e35e5c04bb0f294ae218c265`。
- M6B maintained docs：`GO / READY FOR CHECKPOINT / NOT COMMITTED / NOT PUSHED`；未标记为 FROZEN/COMMITTED/PUSHED。

**Astra 最终结论：GO — READY FOR M6B CHECKPOINT / NOT COMMITTED / NOT PUSHED。等待用户另行明确授权建立 checkpoint；不开始 M6C。**
