# 系统架构

## 文档状态

本文记录截至 2026-09-06 的实际架构状态。Milestone 2、3、4、5A 与 5B 均保留既有冻结 checkpoint。Milestone 6A AI Foundation + JD Structured Parse 已完成、冻结，并以 checkpoint `07712a687685e368e35e5c04bb0f294ae218c265` 固化并 push 到 `main`。Milestone 6B AI Learning Planning + Weekly Review 已完成、冻结，状态为 `FROZEN / COMMITTED / PUSHED`，功能 checkpoint 为 `805a3801af76e4e88434e52e154d2069ad3c4d1b`，已 push 到 `origin/main`。Closing 历史证据：用户通过 IDEA Full Maven Test 取得 155 项全绿、事务集成类 3/3 PASS；Astra 对用户启动的 localhost backend 实际执行 DeepSeek Flash Plan/Review smoke，各一次且均 PASS。以上为 M6B checkpoint 历史记录；当前 M6C Closing 的实际重跑结果见下文及专项报告。

## Milestone 6C — Job Discovery 架构

M6C 在独立路径引入 Spring AI Tool Calling；下文 M6A/M6B 的 no-tools 说明继续适用于原有两个功能。Real Provider Gate 与本轮真实 MySQL、deterministic、前端构建均已通过，当前 READY FOR CHECKPOINT / NOT COMMITTED / NOT PUSHED，详见 [M6C Closing](M6C_CLOSING_VERIFICATION.md)。

```text
CareerService.getGoal(owner) + ProfileService.listUserSkills(owner)
  → bounded context + canonical skill keys
  → AiToolCallingGateway (Flash, thinking disabled, retry 0)
  → Spring AI ToolCallingManager → request-local searchJobs
  → JobSearchGateway → fixed Tavily Search endpoint
  → request-local JobSearchSession (opaque resultKey → provider result)
  → AI resultKey + advice → Java reconstruction / validation
  → bounded in-memory CandidateStore → user review
  → explicit confirm → transaction → CareerService.createJob → commit → consume
```

- API：`POST /api/v1/jobs/ai/discovery`、`POST /api/v1/jobs/ai/discovery/confirm`。所有权取认证上下文，不接受客户端 userId。
- 专用 gateway 固定 `deepseek-v4-flash` 与官方 DeepSeek endpoint，不复用带可变 default tools 的全局 ChatClient，不注册业务 Tool bean。原 `AiChatGateway`、`SpringAiChatGateway` 继续强制 `toolChoice=none`。
- 一次 discovery 是一个逻辑 AI 流程。工具请求和工具结果后的模型分析需要 HTTP 往返：正常两次 completion，最多三次；Tavily 最多两次、每次最多十条，应用 Provider retry 为零。不会将流程数冒充实际 HTTP 调用次数。
- `searchJobs(query, location, maxResults)` 只读。Java 限制 query 400、location 100 字符及 results 1～10；每个请求独立创建 Tool/session，不共享 resultKey map。
- Tavily 只使用固定 `https://api.tavily.com/search`，关闭自动参数、answer 和 raw content；无客户端 endpoint、URL fetch、crawler 或额外 Provider SDK。只提供 `TAVILY_SEARCH_ENABLED`（默认 false）和 `TAVILY_API_KEY`。
- 来源事实由 Java 从 Provider 结果重建：原始 sourceUrl、网页标题、host、摘要和明确提供的日期。URL 仅 http/https，拒绝本地地址和不安全形式，本次按 canonical URL 去重。Tavily 是 discoveredBy，sourceName 取目标 host。
- AI 输出只含 resultKey、rank、fitSummary、strengths、gaps、uncertainty、matchedSkillKeys 和 warnings。未知 resultKey 不进入候选，技能名由 Java canonical map 还原。外部标题、摘要、Goal、Skill 和用户文本都属于不可信数据。
- 返回的 `sourceFacts`、`extractedFields`、`aiAdvice` 明确分层。候选标题是网页标题的待审核截取；公司/岗位类型可能未知；地点候选来自搜索条件，不能称为网页事实。
- CandidateStore 的设计限额为 TTL 15 分钟、每用户 50 条、全局 1000 条。确认在存储锁内校验 owner/TTL/consumed，并在独立事务提交成功后消费；失败不消费。短暂的数据库确认串行执行，网络搜索不持锁。重启会丢失临时候选。
- confirm 只接受 candidateId、用户选择的 companyId/jobType、title/city 和可选完整 rawJd。Java 从 store 填写 sourceUrl/sourceName/publishDate，复用现有 `CareerService.createJob()`。没有自动 Company 创建，也没有发现即保存。
- snippet 不写入 rawJd；未粘贴完整 JD 时 rawJd 为 null，搜索摘要只在临时候选中展示，后续补全 JD 才能使用 M6A。无 migration、Redis、搜索历史、RAG、通用 Agent 或动态模型。

D1 诊断仅按异常类型细分 FINAL_RESPONSE_PARSE 下的 cleaner、syntax、unknown property、mapping 与其他 deserialize failure；不修改 cleaner、DTO、ObjectMapper、Prompt 或 Service 校验契约。Handler 仅记录固定 stage/rule，对外仍为 502 / AI_INVALID_RESPONSE 和固定安全消息。package-private cleaner seam 仅用于离线故障注入，生产构造器固定使用默认 cleaner，不增加 HTTP/配置入口。

官方兼容性依据：[Spring AI 1.1 Tool Calling](https://docs.spring.io/spring-ai/reference/1.1/api/tools.html)、[DeepSeek 模型能力](https://api-docs.deepseek.com/quick_start/pricing)、[thinking 参数](https://api-docs.deepseek.com/guides/thinking_mode/)、[Tavily Search](https://docs.tavily.com/documentation/api-reference/endpoint/search)。本地 `ToolCallingCompatibilityTest` 已实际使用解析的 Spring AI 1.1.8 callback/manager 执行离线 spike；这不替代真实 Provider smoke。

## 通用技术基线

- Java 21、Spring Boot 3.5.14
- MyBatis-Plus 3.5.17、MySQL
- Spring Web、Bean Validation、BCrypt
- JJWT 0.13.0
- Maven Wrapper

## 当前分层与模块

### Frontend

当前前端为 Vue 3、TypeScript、Vite、Vue Router、Axios 和 Element Plus，已落地 20 个 routed frontend pages。前端通过 HTTP / JSON REST API 调用 Spring Boot backend。

```text
HTTP / JSON
  ↓
BearerTokenInterceptor → JwtTokenService → request.currentUserId
  ↓
Controller + Request/Response DTO
  ↓
Service（业务规则、归属校验、事务）
  ↓
MyBatis-Plus Mapper
  ↓
MySQL
```

```text
com.careerplatform
├─ auth       JWT、Bearer 拦截、currentUserId 参数解析
├─ common     ApiErrorResponse、统一异常映射
├─ config     PasswordEncoder、WebMvc 配置
├─ user       注册、登录、用户数据访问
├─ profile    共享基础档案
├─ career     职业目标、公司、岗位、要求与笔记
├─ learning   周计划、任务、学习记录、周复盘、笔记与资料元数据
├─ resume     简历、版本、内容快照
├─ application 投递、阶段历史、测评、面试、Offer、最终复盘
└─ ai          可选 Chat foundation、JD parse 与 Learning AI 候选边界
```

当前 backend package 实际包含 `auth`、`common`、`config`、`user`、`profile`、`career`、`learning`、`resume`、`application` 和 `ai`。

当前源码扫描实际包含 24 个 `@RestController`；M6A 的 `JdAiController` 承载 JD parse/confirm，M6B 的 `LearningAiController` 承载 Plan/Review suggestion 与 Plan confirm。Controller 不接受客户端提供的 `userId` 作为资源归属。公开端点只有 `POST /api/v1/auth/register` 和 `POST /api/v1/auth/login`；其余 `/api/v1/**` 端点都要求合法 Bearer Token。

## AI Foundation 与结构化候选边界

`com.careerplatform.ai` 提供最小 AI 基础设施：配置、provider-neutral `AiChatGateway`、Spring AI `ChatClient` 实现、typed DTO、统一异常以及 JD/Learning 专用 service/controller。生产依赖使用 Spring AI BOM `1.1.8` 与 `spring-ai-starter-model-openai`；Spring Boot 保持 `3.5.14`。DeepSeek Official API 通过 OpenAI-compatible adapter 接入；endpoint 固定为 `https://api.deepseek.com`，生产模型硬锁为 `deepseek-v4-flash`。维护中的全局开关、adapter 与 key 分别来自 `AI_CHAT_ENABLED`、`AI_CHAT_PROVIDER`、`AI_API_KEY`；旧 `AI_JD_PARSE_ENABLED` 只在新开关缺失时作为兼容 fallback。客户端、`AI_MODEL`、runtime options 和自动 fallback 都不能改变模型。Gateway 在每次 Prompt 上再次施加内部 Flash 常量并禁用 tool choice/internal tool execution。

所有 AI 模型默认 `none`，非 Chat 模型固定禁用；`AI_CHAT_ENABLED` 默认 `false`。无 provider 或 API key 时 gateway 返回 `AI_SERVICE_UNAVAILABLE`，不会阻止 Spring Context 或传统业务启动。Provider failure 映射为 503 `AI_PROVIDER_UNAVAILABLE`，structured output/conversion failure 映射为 502 `AI_INVALID_RESPONSE`，响应不透出 provider 原始认证错误、secret 或 stack trace。

JD parse 使用 `POST /api/v1/jobs/{jobId}/ai/jd-parse`。后端按 `currentUserId` 读取 owner-owned Job，只把当前 `rawJd` 发送给模型。模型 schema 只能产生 `type`、`description`、`skillName`、`evidenceQuote` 和 warnings，不能产生任何可信数据库 ID。Java 随后执行输出数量/长度/enum/nullability 校验、空白和大小写归一化 evidence 子串校验、AI 内部去重、global Skill 名称匹配、现有 requirement 重复检测，并生成 `SHA-256(rawJd)` fingerprint。unsupported evidence 直接丢弃并产生 warning；不存在的 Skill 标记 `UNRESOLVED`，绝不自动创建。

JD 属于不可信用户内容。System instruction 明确禁止执行 JD 中的角色切换、prompt 泄露、secret 请求、schema 修改、联网或工具调用指令，只允许提取显式事实；user prompt 使用清晰 untrusted delimiter。本功能的 `ChatClient` 未注册 tools，因此没有 tool capability。Spring AI OpenAI ChatModel 启动所需的 `ToolCallingManager` 使用空 resolver，生产路径仍没有任何可发现或可调用的 tool。

parse 返回 ephemeral candidate，绝不修改 `job_requirement`。只有用户在 Job Detail 审核、编辑、选择后调用 `POST /api/v1/jobs/{jobId}/ai/jd-parse/confirm` 才会写库。confirm 不调用 AI；它先锁定 owner-owned Job，比较当前 JD fingerprint，再执行 DTO、Skill、重复和既有 `CareerService` requirement 规则，最后在单一事务中追加 selected requirements。旧 fingerprint 返回 409 `INVALID_RESOURCE_STATE`，任一非法项使整批回滚，既有人工 requirement 永不被删除或覆盖。

## 身份与安全边界

登录由 `UserService.login()` 查询用户、检查 `ACTIVE` 状态并使用 `PasswordEncoder.matches(raw, hash)` 验证密码。用户名不存在、密码错误和用户不可登录统一返回 `401 INVALID_CREDENTIALS`。成功后由 `JwtTokenService` 生成包含 `userId` 和 `username` 的短期 JWT。

`BearerTokenInterceptor` 统一验证 `Authorization: Bearer ...`，将 `currentUserId` 写入 request attribute；`CurrentUserIdArgumentResolver` 为 Controller 的 `@CurrentUserId Long` 参数提供身份。没有使用 ThreadLocal。

所有私有资源的 Service 查询、更新和删除条件都包含资源主键与 `currentUserId`。资源存在但属于其他用户时，对外返回 `404 RESOURCE_NOT_FOUND`，不泄露资源存在性。

## 共享基础档案边界

共享档案是后续 Career、Learning、Resume、Application Review 和 AI 的事实源：

- `UserProfile`：每用户最多一条，`PUT` 为 upsert。
- `EducationExperience`、`ProjectExperience`、`InternshipExperience`、`CertificateAward`：用户私有 CRUD。
- `Skill`：全局标准技能字典，只提供创建和查询。
- `UserSkill`：用户与技能的关联，熟练度限定为 `BEGINNER`、`FAMILIAR`、`PROFICIENT`。

其他模块应经正式 Service 读取或修改共享档案，不能绕过业务边界直接写表。

## 职业探索边界

- `CareerGoal`：用户可维护多个目标与历史方向。
- `Company`：当前是用户私有收集的信息，不是全站企业库。
- `Job`：同时属于用户并引用该用户自己的 Company；用 `archived` 保留历史岗位。没有 Application 历史时允许物理删除；一旦存在任意历史，即使全部 ENDED，也返回 `409 RESOURCE_IN_USE`，只能归档或隐藏。
- `JobRequirement`：挂在 Job 下；`SKILL` 类型必须引用已存在的全局 Skill，其他类型不得携带 `skillId`。
- `JobNote`：挂在 Job 下，保存用户自己的岗位观察。

创建或更新 Job 前，Service 验证 `company.id = companyId AND company.user_id = currentUserId`。访问 JobRequirement 或 JobNote 前，Service 先验证父 Job 属于当前用户，再以 `child.id + job.id` 操作子资源。数据库还用 `(company_id, user_id) → company(id, user_id)` 复合外键形成最终一致性防线。

## Learning 与 AI Learning 边界

Learning 的传统业务与 AI 候选功能共享既有六个资源；M6B 不增加 AI 表、RAG、文件上传、向量化或新页面。资源关系固定为：

- `LearningPlan 1 -> n LearningTask`；`LearningTask 1 -> n StudyRecord`。
- `LearningPlan 1 -> 0..1 WeeklyReview`，通过 `PUT /api/v1/learning-plans/{planId}/review` 创建或更新同一条复盘。
- `LearningNote` 与 `LearningMaterial` 独立属于 Plan，可选关联同一 Plan 下的 Task。

所有 Learning 私有表都保存 `user_id`。每个 Learning API 从 `@CurrentUserId Long` 获取 owner；Service 的详情、更新、删除查询同时带资源 ID 与 owner，子资源还验证路径父级。Note/Material 的 `taskId` 使用同一 Plan、同一 owner 的任务校验，跨用户或错误父子组合统一返回 `404 RESOURCE_NOT_FOUND`。

Learning 的 Plan 周起止日期要求 `weekEnd >= weekStart`，同一用户同一 `weekStart` 唯一；Task 截止日期必须位于 Plan 闭区间，计划用时为正、排序值不小于零；StudyRecord 时长为正且不会隐式改变 Task 状态。删除 Task 时事务内先解绑 Note/Material 的可选 `taskId`，再删除 Records 和 Task；删除 Plan 时按 Material、Note、Review、Record、Task、Plan 顺序清理全部后代。

M6B 提供 `POST /api/v1/learning-plans/ai/plan-suggestion`、`POST /api/v1/learning-plans/ai/plan-suggestion/confirm` 和 `POST /api/v1/learning-plans/{planId}/ai/review-suggestion`。Suggestion 只读；confirm 不调用 AI，固定创建 `PLANNED` Plan 与 `TODO` Tasks，并在 `LearningService.createPlanWithTasks` 的一个事务中先全量验证再写入。Review candidate 只能由用户应用到现有表单，正式保存继续复用 `PUT /api/v1/learning-plans/{planId}/review`。

`LearningAiContextBuilder` 只读取 current user 的数据，优先使用用户明确时间约束、focus、CareerGoal 与选中的结构化 JobRequirement；不发送 raw JD、Resume、联系方式、secret 或 `userId`。预算固定为技能 30、岗位 5、每岗要求 10/总计 30、最近 Plan 2、Task 20、StudyRecord 30、Note 12、Review 2、单段文本 300、上下文文本 12000；截断必须返回 warning。Java 构造 evidence map，模型只能返回 `evidenceKeys`，Java 再反查可信 label/excerpt；unknown key 不成为证据，每个 Plan task 必须至少有一条有效 evidence。Review 的状态计数、完成率、计划/实际分钟与逐任务时长全部由 Java 计算。UI 将来源事实与 AI 建议分区展示。

## Resume 边界

Resume 后端包含三个持久化层级：`Resume 1 -> n ResumeVersion 1 -> n ResumeContentItem`。所有三张表保留 `user_id`，Service 对每个详情、更新和删除操作都验证完整的 owner 与路径父级；错误 owner、错误 Resume/Version 父子组合统一返回 `404 RESOURCE_NOT_FOUND`。数据库使用 `(resume_id, user_id) -> resume(id, user_id)` 与 `(version_id, user_id) -> resume_version(id, user_id)` 复合外键，`(resume_id, version_no)` 保证同一 Resume 的版本号唯一。

Version 只有 `DRAFT` 与 `FINALIZED` 两种状态。DRAFT 可编辑、可删除；FINALIZED 的版本和内容均只读，重复 finalize 保持第一次 `finalizedAt`。生成接口经 ProfileService 读取共享档案，并把 Profile、Education、Skill、Project、Internship、Certificate 内容写入持久化快照；之后修改共享档案不会回写旧版本。复制 FINALIZED 版本会创建新的 DRAFT 版本和新的内容行，目标版本与源版本互不共享行。Resume 含 FINALIZED 版本时拒绝删除并返回 `RESOURCE_IN_USE`。

## Application 边界

创建 Application 必须绑定当前用户所属 Job 与 `FINALIZED ResumeVersion`，DRAFT 返回 `409 INVALID_RESOURCE_STATE`。创建时冻结岗位标题、公司名称、地点和原始 JD，并原子写入初始 `APPLIED` History。状态机固定为：`APPLIED -> ASSESSMENT/INTERVIEW/OFFER/ENDED`、`ASSESSMENT -> INTERVIEW/OFFER/ENDED`、`INTERVIEW -> OFFER/ENDED`、`OFFER -> ENDED`、`ENDED -> none`。

同一 `user_id + job_id` 的创建先锁定 Job 行，Service 在事务中检查现有 ongoing Application；数据库以生成列 `ongoing_job_id = CASE WHEN current_stage <> 'ENDED' THEN job_id ELSE NULL END` 和 `UNIQUE(user_id, ongoing_job_id)` 提供最终并发防线。转为 ENDED 后生成列为 NULL，允许以后再次投递。Job 创建投递与物理删除共用同一父行锁，避免“删除检查”和“创建投递”竞态。

所有显式迁移都先 `FOR UPDATE` 锁定 owner 匹配的 Application，并在同一事务内更新 `currentStage`、终局字段和追加 History。Assessment、Interview 的创建或局部结果更新不触发全局状态。Offer 创建固定为 CONSIDERING 并推进至 OFFER；ACCEPTED/REJECTED 会在同一事务内终结 Offer、Application 并追加 History。FinalReview 采用 `GET + PUT upsert`，仅 ENDED 后可写，数据库 `UNIQUE(application_id)` 保证 0..1。

## Mapper 注册与异常

应用入口通过显式 `@MapperScan` 注册：

- `com.careerplatform.user.mapper`
- `com.careerplatform.profile.mapper`
- `com.careerplatform.career.mapper`
- `com.careerplatform.learning.mapper`
- `com.careerplatform.resume.mapper`
- `com.careerplatform.application.mapper`

异常继续统一为 `ApiErrorResponse(code, message, timestamp)`，当前覆盖参数校验、非法 JSON/枚举/路径类型、未认证、无效凭证、资源不存在、重复资源、资源被引用和资源状态冲突等场景。Request DTO 与 Entity 分离，TEXT 请求字段统一限制为 16000 字符，VARCHAR 上限与 SQL 列宽一致；Resume 请求 DTO 不接受 `userId`、`status`、`finalizedAt`、`sourceType` 或 `sourceId`，Response DTO 不包含 `userId` 或 `passwordHash`。

## 后续规划边界

JD Structured Parse 是第 1 个正式 AI 功能，AI Learning Planning + Weekly Review 是第 2 个，M6C AI Job Discovery / Tool Calling 是第 3 个。AI 面试与求职复盘、Resume + JD matching、RAG、Embedding、通用 Agent 和 AI Evaluation 仍未实现。后续 AI 输出继续遵循“候选结果 → 用户确认 → Java Service 校验与持久化”，不得直接写正式业务数据。
