# 开发状态

## M7 / P2-A RAG 当前状态

**required gates 已通过，M7 状态为 `FROZEN / COMMITTED / PUSHED`，正式 AI 功能计 4 个。** 本轮扩展现有 LearningMaterial，实现 PDF/DOCX 原件上传、MySQL BLOB 与 Chunk 持久化、独立 Embedding、owner/plan-scoped 检索、Flash 问答和 Java 可信引用重建，前端融入原学习计划详情，不新增 routed page。真实 MySQL 与最小真实 Provider 证据均已通过；外部 Tech Lead 已给出最终 GO；M7 feature checkpoint 为 `6e4db93ccae7b4efc9530c8950926d257eb21aaf`，已 push 到 `origin/main`。Closing 中未提交状态为提交前历史记录。详细实际执行结果统一记录于 [M7 Closing](M7_RAG_CLOSING_VERIFICATION.md)。以下 M2～M6C 的未实现项与统计是各 checkpoint 历史上下文。

本轮已核验：M7 离线 9 类去重库存 42 项全绿（单次 41，随后 mapper 2）；M6 回归 17 类 129 项全绿，其中 M6A/M6B 共享 no-tools 31、M6C 专用 98，历史 OfflineFlow 4 不计入本轮；typecheck/build 与 browser fixture QA 11 组通过、pageErrors=[]。当前机械统计 29 个 unique `CREATE TABLE`、26 个 exact `@RestController`（排除 Advice）、21 个 routed pages、4 个 completed AI functions。用户 IDEA 最新 MySQL 指定套件 30 项全绿：Schema 5、Core 11、Support 6、RAG 8，`BUILD SUCCESS` 46.750s；真实 Provider PASS 为 Jina AI `jina-embeddings-v5-text-small` + 固定 `deepseek-v4-flash`，Query2 citation=0 且 plan snapshot unchanged、cleanup=true、retry=0。运行时 Query2 Chat calls/provider wire counts 均为 `NOT OBSERVABLE`，确定性 no-evidence 分支 skips Chat 为 PASS。当前状态 FROZEN / COMMITTED / PUSHED；以上均为 checkpoint 前验收证据，本次仅同步文档，未重跑门禁。

> 更新日期：2026-09-07。本文只记录真实完成度；长期范围见[项目规划](PROJECT_PLAN.md)。

## Milestone 6C 历史基线

M6C AI Job Discovery 已实现并通过 Final Closing Verification，正式 AI 功能达到 3 个。当前状态为 **FROZEN / COMMITTED / PUSHED**；M6C checkpoint / 本次 docs-only cleanup 起始 HEAD 为 `915acc0d02ac173877ae49b10fff96243b236cd5`，已 push 到 `origin/main`。本次 cleanup 只同步文档，不改变 M6C checkpoint；原 Closing 的 READY FOR CHECKPOINT 决策属于 pre-checkpoint historical decision。

M6C Closing 阶段的历史验证（2026-09-06）包括：deterministic 11 类 102 项、M6A/M6B regression 7 类 31 项、真实 MySQL JobDiscoveryControllerIntegrationTests 3 项、frontend typecheck/build 均 PASS。2026-09-05 browser fixture QA 9 组为历史证据，本次未重跑。Smoke #4 历史实测 HTTP 200 / candidates=3 / searchCalls=2 / exactly one discovery / retry=0；Job、Company 均 0→0。Closing 阶段新增真实 Provider 调用为 0；本次 post-M6C docs-only cleanup 未运行测试或 build。

M6C checkpoint 历史快照为：28 张业务表、25 个精确 @RestController（不含 @RestControllerAdvice）、21 个 routed pages、3 个已完成 AI 功能；RAG 在该 checkpoint 范围内尚未实现。详见 [M6C Closing](M6C_CLOSING_VERIFICATION.md)。M7 的当前统计和门禁见本文开头及专项报告，下方 M2～M6B 保留为历史里程碑记录。

## Milestone 2 结论

Milestone 2“最小身份闭环 + 共享基础档案 + 职业探索后端”已完成并冻结，checkpoint 为 `228bc97628a7bd9a12d9e36d65f0bebef0da094e`。该里程碑通过 Java 21 编译、MockMvc 全链路测试和真实 MySQL 集成测试。没有使用 H2，未实现本里程碑禁止范围内的功能。

## 已实现并验证

### 用户与身份

- `POST /api/v1/auth/register`：Bean Validation、BCrypt、重复用户名 409。
- `POST /api/v1/auth/login`：状态检查、`PasswordEncoder.matches`、统一无效凭证 401、JWT 返回。
- Bearer Token：JJWT 签名与过期验证；`userId`、`username` claims。
- `currentUserId`：Interceptor 写 request attribute，Argument Resolver 统一注入 Controller。
- 除注册、登录外，本轮 `/api/v1/**` API 均受保护；无 Token、非法 Token、过期 Token 返回统一 401。

### 共享基础档案

- `UserProfile` 查询与 upsert。
- `EducationExperience` 完整 CRUD。
- 全局 `Skill` 创建与查询，名称唯一。
- `UserSkill` 完整 CRUD，用户与技能组合唯一，熟练度为有限枚举。
- `ProjectExperience`、`InternshipExperience`、`CertificateAward` 完整 CRUD。
- 私有资源均按 `currentUserId` 隔离，跨用户 GET/PUT/DELETE 返回 `RESOURCE_NOT_FOUND`。

### 职业探索

- `CareerGoal` 完整 CRUD。
- 用户私有 `Company` 完整 CRUD；已有 Job 引用时拒绝删除。
- `Job` 创建、列表、详情、更新、archive、unarchive；只能引用当前用户自己的 Company。
- `JobRequirement` 与 `JobNote` 完整子资源 CRUD；先验证父 Job owner。
- `SKILL` 要求必须关联已存在 Skill，其他要求类型不得携带 `skillId`。

### 数据库与映射

- 依次保留 `001`、`002`、`003` 三个 SQL 文件；没有覆盖历史脚本。
- MySQL 中已真实应用 12 张新增表。
- `DatabaseSchemaIntegrationTests` 已从 `information_schema` 验证 M2 的 12 张新增表、14 个外键和 4 个关键唯一索引。
- `@MapperScan` 已显式覆盖 user、profile、career 三个 mapper 包，Context Test 与业务测试均通过。

## Milestone 3 Learning 结论

Milestone 3 Learning 已完成并冻结，checkpoint 为 `9959ea40189d1360329ca27cabdaa0a8f9c8a28a`。该里程碑通过 Java 21 编译、真实 MySQL MockMvc 集成测试、Schema metadata 测试和并发复盘测试。其历史验证基线为 19 张 `BASE TABLE`、15 个 `@RestController`；不再把已冻结里程碑描述为未提交。

### 已实现并验证

- `LearningPlan`：创建、列表、详情、更新、删除；同一用户同一 `weekStart` 唯一，且校验 `weekEnd >= weekStart`。
- `LearningTask`：Plan 下的完整 CRUD；校验 Plan owner、Task owner、dueDate 闭区间、正计划用时和非负排序值。
- `StudyRecord`：Task 下的完整 CRUD；校验 Task owner 和正学习时长，新增记录不改变 Task 状态。
- `WeeklyReview`：Plan 下 `GET/PUT`；PUT 首次创建、后续更新同一行，数据库 `UNIQUE(plan_id)` 保证 0..1。
- `LearningNote`：Plan 下完整 CRUD；`taskId` 可空，非空时必须是同一 owner、同一 Plan 的 Task。
- `LearningMaterial`：Plan 下完整 CRUD；保存资料元数据，`sourceUrl` 可空且不处理文件；`taskId` 使用同一归属校验。
- 删除语义：Task 删除解绑 Note/Material 后删除 Records；Plan 删除清理全部后代，不留下孤儿数据。
- 统一错误：Bean Validation、非法 JSON/枚举/路径类型均返回 `400 VALIDATION_ERROR`；越权私有资源返回 `404 RESOURCE_NOT_FOUND`。
- 数据库：004 脚本已真实应用；六张 Learning 表、13 个 FK、5 个非主键 UNIQUE、InnoDB/utf8mb4_unicode_ci 和六个 Mapper 注册均已验证。

### Learning 测试统计

按当前 Surefire XML，Learning 相关定向文件共运行 23 项：`DatabaseSchemaIntegrationTests` 2、`LearningCoreIntegrationTests` 11、`LearningPlanTaskRaceIntegrationTests` 3、`LearningSupportIntegrationTests` 6、`LearningReviewConcurrencyIntegrationTests` 1；其中 Schema suite 的 1 项为 M2 基线、1 项为 M3 Learning Schema，故新增 Learning 测试为 22 项。全部 PASS，FAIL/ERROR/SKIPPED 均为 0。全量测试结果以 Task 4 审计报告中的最新 XML 统计为准。

## Milestone 4 Resume 状态

Resume 后端已完成并冻结于 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56`，新增 `resume`、`resume_version`、`resume_content_item` 三层资源及三个 Controller：

- Resume 支持按当前用户创建、列表、详情、更新和删除；含 FINALIZED 版本时删除返回 `409 RESOURCE_IN_USE`。
- ResumeVersion 支持空 DRAFT、共享 Profile 快照生成、列表、详情、更新、复制、定稿和删除；版本号按 Resume 唯一，定稿幂等且保留首次 `finalizedAt`。
- ResumeContentItem 支持 DRAFT 下创建、列表、详情、更新、删除；生成内容写入持久化 source snapshot，复制后使用独立 item 行。
- owner-aware composite FK、完整路径父级校验和 FINALIZED 写保护覆盖到 Service 与数据库边界；错误 owner/父级组合返回 404，定稿后写入返回 `409 INVALID_RESOURCE_STATE`。
- `ResumeIntegrationTests` 覆盖上述 CRUD、快照独立性、复制隔离、越权/错父级、定稿状态、并发版本号和 finalize-vs-item-write race；`DatabaseSchemaIntegrationTests` 已补充三表、关键复合 FK 列序、版本号唯一键、metadata 和三个 Mapper bean 断言。
- 新增测试已通过 Java/Maven 编译和真实 MySQL 验证：定向 `DatabaseSchemaIntegrationTests` 3 + `ResumeIntegrationTests` 9 共 12 项，Failures 0、Errors 0、Skipped 0；全量 Maven test 81 项，Failures 0、Errors 0、Skipped 0。005 已通过 login-path 幂等应用，数据库确认 22 张 `BASE TABLE`。M4 已以 checkpoint `c61756f539aefc367473dd56ca1dcb2384143f56` 固化并 push 到 `main`。

## Milestone 5A Vue Frontend Foundation

Milestone 5A Vue Frontend Foundation 已完成并冻结，状态为 `FROZEN / COMMITTED / PUSHED`，checkpoint 为 `6f503667c0df8b4556fe65c6a6c0a667d85fee81`。

### 已实现并验证

- Vue 3、TypeScript、Vite、Vue Router、Axios 和 Element Plus 已进入 `main`。
- 实际 routed frontend pages：18。
- `npm run typecheck`：PASS。
- `npm run build`：PASS。
- 后端 HTTP smoke：12/12 PASS。
- P0 = 0，P1 = 0。
- P2 = 1：Vite 主 chunk 约 1.07 MB warning。该 warning 当前不阻塞项目，本轮不为此重构前端。

以上为 M5A 冻结时的既有验证证据；M5B 的验证证据见下节。

## Milestone 5B Application Management

M5B 生产实现已完成并冻结，当前状态为 `FROZEN / COMMITTED / PUSHED`。checkpoint 为 `6225510e51c11f65213e654dcc2dce3f9de45875`，commit 为 `feat: complete application management`。

以下验证证据均已在 M5B Closing Review / checkpoint 前完成；本轮仅同步文档，没有重新执行这些验证。

- 新增 Application、ApplicationStageHistory、Assessment、Interview、Offer、FinalReview 六张表；006 通过 login-path 连续执行两次。
- Application 只绑定当前 owner 的 Job 与 FINALIZED ResumeVersion，并冻结岗位标题、公司、地点和原始 JD。
- 同 user+job ongoing 唯一由 Job 行锁、事务内检查、generated column 唯一键三层保证；真实双线程测试严格得到一成功、一 `DUPLICATE_RESOURCE`，数据库 ongoing count = 1。
- 状态机支持向前跳级，ENDED 为终态；currentStage 更新与 History 追加原子提交，非法迁移不留下半写历史。
- Assessment/Interview 是独立过程记录，不隐式推进或终结 Application。
- Offer 为 0..1；创建进入 OFFER，ACCEPTED/REJECTED 与 Application ENDED、History 同事务。
- FinalReview 为 0..1 `GET + PUT upsert`，仅 ENDED 后可写。
- Job 无 Application 历史时可物理删除；存在任意历史时返回 `409 RESOURCE_IN_USE`，ENDED 历史也阻止删除。
- 新增 5 个 Application Controller 与 2 个真实前端路由；Job detail 可选择 FINALIZED ResumeVersion 创建投递，详情页管理完整流程。
- 定向真实 MySQL：`ApplicationIntegrationTests` 8 + `DatabaseSchemaIntegrationTests` 4，共 12 项，Failures 0、Errors 0、Skipped 0。
- 全量 Maven test：90 项，Failures 0、Errors 0、Skipped 0。
- Frontend：`npm run typecheck` PASS；`npm run build` PASS，保留既有约 1.07 MB 主 chunk warning。
- Real HTTP smoke：注册/登录、Job、FINALIZED ResumeVersion、Application 创建与迁移、Assessment、Interview、Offer 接受并结束、FinalReview、Job 历史删除保护、未认证 401，共 12/12 PASS。

代码与迁移脚本扫描得到 28 张业务表（含 `app_user`）、23 个 `@RestController` 和 20 个 routed frontend pages。

## Milestone 6A — AI Foundation + JD Structured Parse

M6A 已完成、冻结，并以 checkpoint `07712a687685e368e35e5c04bb0f294ae218c265` 固化并 push 到 `main`；commit 为 `feat: add AI JD structured parsing`：

- Spring Boot 保持 `3.5.14`；通过 Spring AI BOM 引入稳定版 `1.1.8` 与 OpenAI Chat starter。
- AI 默认禁用；DeepSeek 通过 OpenAI-compatible adapter 接入，endpoint 固定为官方地址，生产模型硬锁为 `deepseek-v4-flash`。只有开关、adapter 与 key 来自本地 `AI_*` 环境变量；不存在 model 环境变量、客户端/runtime model override 或 Pro fallback。无 AI 配置及选择 adapter 但无真实 key 的 Spring Context 均已确认可启动。
- 新增 `AiChatGateway`/`ChatClient` typed structured output、统一 502/503 错误、prompt injection 数据边界与无 tools 配置。
- 新增 JD parse/confirm API。parse 不写数据库；Java 验证 evidence、Skill resolution、duplicate、output limits 与 source fingerprint；confirm 在显式用户操作后锁 Job、复用 Career requirement validation，并在一个事务中追加 selected requirements。
- Job Detail 已加入解析、候选审核/编辑/选择、Skill 重映射、证据与 warnings、确认写入以及 AI 不可用降级；routed pages 仍为 20。
- 离线 AI 定向测试 24 项全部 PASS（service/prompt/gateway/config contract、无 key Context 与无认证 MockMvc）；其中一项使用真实 Spring AI `ChatClient` + fake `ChatModel` 验证 typed conversion，另有断言确认实际 Prompt 强制使用 Flash 且关闭 tool execution。frontend typecheck/build PASS。
- localhost HTTP business smoke 已通过：register/login、未认证 401、owner Job、跨用户 Job 404、人工 JobRequirement CRUD、真实 JD parse、confirm selected candidate 与 stale fingerprint 409 均符合预期。parse 前后 JobRequirement 数量差为 0，confirm 后差为 1；临时 Job、Company 与 Requirement 已清理。
- Live Provider Verification 为 `PASS`：经当前 IDEA 启动的 production application 完成 authenticated DeepSeek Official API structured parse，生产模型确认固定为 `deepseek-v4-flash`，typed result、evidence Java 校验与 Skill resolution 均通过，Pro calls 为 0。
- Spring AI structured-output integration 使用 deterministic `ChatModel` 真实经过 `ChatClient` typed conversion wiring，结果 PASS。
- 最终真实 MySQL full Maven suite：132 项、Failures 0、Errors 0、Skipped 0，`BUILD SUCCESS`。中间阶段的凭据可见性问题已经解决；随后唯一失败用例确认是测试外层事务导致的 isolation bug，并在改用 `Propagation.NOT_SUPPORTED` 与 committed fixture cleanup 后通过单项、整类及 full suite 验证。Production confirmation code 未修改。

因此 M6A 在其冻结节点的结论为 `FROZEN / COMMITTED / PUSHED`，checkpoint 为 `07712a687685e368e35e5c04bb0f294ae218c265`。P0 = 0，P1 = 0；P2 保留 Vite 主 chunk warning 与 Mockito dynamic agent future-JDK warning。M6A 冻结时 AI implementation count 已从 0 增至 1；在该历史节点课程最低 3 个，仍至少缺 2 个。

## Milestone 6B — AI Learning Planning + Weekly Review

M6B 已完成实现与 Closing Verification，并冻结为 **`FROZEN / COMMITTED / PUSHED`**。功能 checkpoint 为 `805a3801af76e4e88434e52e154d2069ad3c4d1b`，commit 为 `feat: add AI learning planning and weekly review`，已 push 到 `origin/main`。

以下测试、构建、真实 MySQL 与 Provider smoke 结果均为 checkpoint 前 Closing 阶段的历史证据；本次 checkpoint 仅进行 Git/diff、提交范围、凭据规则扫描及文档状态检查，未重跑这些验证，未修改生产代码：

- Plan suggestion 读取用户明确时间预算、可选 CareerGoal、最多 5 个 owner-owned Job 的结构化 JobRequirement、最多 30 个 UserSkill 与有限 Learning 历史；不发送 raw JD、Resume、联系方式、secret 或 `userId`。
- Java evidence map 是唯一可信来源；模型只能返回 `evidenceKeys`。unknown key 被丢弃，Plan task 若没有至少一条有效 evidence 则返回 `AI_INVALID_RESPONSE`。Facts/evidence 与 AI rationale/task advice 在 contract 和 UI 中分开。
- Plan candidate 为 ephemeral，Java 校验 2～6 个任务、标题/排序唯一、分钟为正、dueDate 在周期内、总分钟不超过用户预算。用户可编辑、删除和排序；确认后由单一事务创建一个 Plan 与全部 Tasks，confirm 不调用 AI。
- Weekly Review suggestion 先校验 Plan owner，再由 Java 计算 task status、completion rate、planned/actual minutes、record count 与逐任务 metrics。候选不会自动保存或覆盖已有 Review；用户只能先应用到表单，再走既有 `PUT /learning-plans/{planId}/review` 保存。
- Prompt 将 Goal、Requirement、Note、Record、Review 与 focus 全部视为 untrusted data，明确禁止 role/schema override、prompt/secret disclosure、network/tool instructions；生产 Gateway 继续 `toolChoice=none`、无业务 tools。
- `AI_CHAT_ENABLED` 成为维护中的 global Chat AI switch，旧 `AI_JD_PARSE_ENABLED` 仅作兼容 fallback；`AI_CHAT_PROVIDER` 与 `AI_API_KEY` 不变，生产模型继续硬锁 `deepseek-v4-flash`，不存在 `AI_MODEL` 或 Pro fallback。
- Astra/Codex 在 Closing 测试修复阶段实际运行离线定向 **38 项、Failures 0、Errors 0、Skipped 0、BUILD SUCCESS**：M6B/foundation 21、M6A prompt/service 15、Learning AI 未认证 1、MyBatis 故障注入装配检查 1。最终 live 阶段未重复运行这些测试。
- 用户通过 IDEA `Full Maven Test` 配置实际取得 **155 项、Failures 0、Errors 0、Skipped 0、BUILD SUCCESS**；`LearningServiceAiConfirmTransactionIntegrationTests` **3/3 PASS**。六项 Learning AI 集成测试和六表无写入快照断言均包含在全绿 Full Maven 中。该证据来自用户 IDEA，不是 Astra 终端运行。
- Astra/Codex 在 Closing 初始审计阶段实际运行 frontend typecheck/build 均 PASS；此后前端未修改，最终阶段未机械重复构建。保留既有主 chunk warning。M6B 不新增 routed page，仍为 20。
- Astra 在 2026-09-05 对用户 IDEA 启动的 `localhost:8080` 执行真实 DeepSeek Plan/Review smoke，各一次且均 PASS，脚本重试 0、AI confirm 0。Plan 返回 3 tasks、270/300 分钟、buffer 30、13 条可信 evidence；Review 指标为 4 tasks、四种状态各 1、完成率 25%、计划 300/实际 90 分钟、3 条学习记录、9 条可信 evidence。
- 两次 live owner 业务资源快照 delta 均 0，已有 WeeklyReview 保持不变；直接 SQL 六表快照证据来自用户 IDEA 集成测试。测试 Plan/Job/Company/Goal 及子资源已删除；应用无账号删除 API，保留独立空测试账号 `userId=1890` 供后续精准清理。真实用户数据未修改，凭据未打印或落盘。
- 不新增业务表、AI 表或 migration；M6B 冻结时业务表 28、源码 `@RestController` 24、正式 AI 功能 2。生产模型固定 `deepseek-v4-flash`，Pro calls 0，无模型 fallback、动态选模或业务 tools。

M6B 当时将正式 AI 功能数从 1 增至 2；当时距课程最低 3 个仍缺 1 个。本里程碑两项 P1 验证门禁均已关闭，M6B 在该历史节点的结论为 **FROZEN / COMMITTED / PUSHED**；在该历史节点 M6C 尚未开始。

### 2026-09-05 独立 Closing 复核（checkpoint 前历史记录）

- Astra 复核了三个按 Luna Max 参数创建的独立审计 Worker 的后端、测试配置、前端文档结论，未确认生产 P0/P1；未修改 production code。四项 P2 边界问题和既有构建警告记录于[Closing 验证报告](M6B_CLOSING_VERIFICATION.md)。
- 原 confirm 测试只能证明预校验。新测试最初使用 Mapper spy 的 `callRealMethod()`，在用户 IDEA 154 项全量运行中出现唯一 failure；原因是 MyBatis abstract Mapper 没有可调用的方法体，属于测试注入错误。Astra 改为 test-only MyBatis `Executor.update` 拦截器：首条真实 INSERT 后查证 Plan/Task 已存在，第二条在同 Spring 事务连接的 INSERT 入口抛指定异常，事务外验证两行零残留。修正后用户 IDEA 事务类 3/3、Full Maven 155 项均 PASS；production code 保持不动。
- Plan/Review suggestion 集成测试补充六张 Learning 表调用前后的整行快照对比和逐任务 actualMinutes 断言，已由用户 IDEA 真实 MySQL Full Maven 验证通过。
- 全局开关补充加载生产 properties 的实际绑定测试，覆盖默认关闭、legacy true/false、新开关覆盖 legacy 两种冲突组合；离线装配检查确认真实 Mapper、拦截器注册和 INSERT statement。相关证据包含在上述 38 项定向结果及用户 155 项 Full Maven 中。
- Astra/Codex 实际运行 frontend typecheck/build 均 PASS。精确 annotation 扫描得到 **24** 个 `@RestController`，此前 25 的口径混入 `@RestControllerAdvice`；源码/SQL 仍为 28 张业务表、20 个 routed pages，无 M6B migration。
- 历史 Codex Full Maven 151 项中的 97 errors 是本地凭据不可见导致的认证错误，未作为代码缺陷；用户 IDEA 后续 155 项全绿已关闭 MySQL 门禁。live smoke 使用用户持有凭据的 backend，Astra 未读取秘密。最终 secret audit、机械统计、`git diff --check` 均 PASS。

## M7 当前收口证据

- MySQL latest Gate：`target/m7-mysql-confirmed-result.json`，`DatabaseSchemaIntegrationTests` 5、`LearningCoreIntegrationTests` 11、`LearningSupportIntegrationTests` 6、`RagMaterialIntegrationTests` 8，共 30 项，Failures/Errors/Skipped 均为 0，用户 IDEA `BUILD SUCCESS` 46.750s；007 未重复执行。
- M7 deterministic 为 9 类当前去重库存 42 项全绿：`target/m7-deterministic-final.log` 单次 41 项，`target/m7-mapper-final.log` 随后 mapper 2 项（原 1、新增 1）。M6 regression 为 17 类 129 项全绿；不把去重库存或历史 OfflineFlow 4 项冒充单次执行。
- Real Provider Gate：Jina AI `jina-embeddings-v5-text-small` 实际索引 1 Chunk；Query1 Java 21 trusted citation，Query2 insufficient-evidence 且零 citation、plan snapshot unchanged、cleanup=true、retry=0。runtime Query2 Chat calls/provider wire counts 为 `NOT OBSERVABLE`；确定性 no-evidence 分支 skips Chat PASS。首次 503 timeout 结果和 proxy connectivity evidence 均保留，生产 Chat 固定 `deepseek-v4-flash` 且无 fallback。
- 前端 `typecheck`/`build` exit 0，browser QA 11 groups PASS、`pageErrors=[]`、390px PASS；build chunk size warning 记录为 P2。无 AI 配置完整启动证据是 2026-09-05 历史 `AiOpenAiWithoutKeyContextTest`，M7 `RagConfigurationTest` 2 项仅 context slice。
- P0=0、P1=0。P2/边界包括 DOCX 仅校验 Word root namespace、数据库不可用时 FAILED 写入可能失败并保留 retryable UPLOADED、无 delete endpoint 导致 smoke 账号 1976/1977 保留及 bundle warning；这些不阻塞 checkpoint。

## 当前未实现

- Spring Security 完整框架、RBAC、OAuth、Refresh Token、Token 黑名单和复杂 Logout。
- AI 面试与求职复盘、Resume + JD matching、通用 Agent、AI Evaluation 尚未实现。M6C Tool Calling 与 M7 RAG/Embedding 已完成，正式 AI 功能当前为 4 个。
- Redis、MQ、Elasticsearch、管理员后台和 HR 端。

## 已知技术债

- `ProfileService` 与 `CareerService` 已开始变大，后续模块扩展时可按聚合拆分；本轮为保持课程项目简单未提前抽象。
- `UserSkill` 列表响应需要逐项读取 Skill，数据量增大后可改为联表查询以消除 N+1。
- 测试启动时 Mockito 提示未来 JDK 将限制动态加载 agent；当前 Java 21 不影响测试结果，后续升级 JDK/Mockito 时再处理。
- 当前使用轻量 MVC Interceptor 鉴权；出现 RBAC 或更多认证方式时再评估 Spring Security，不在本轮扩张。

## 下一步建议

M7 已完成 checkpoint 并 push，状态为 `FROZEN / COMMITTED / PUSHED`。本次任务结束，不启动新的功能 milestone。
