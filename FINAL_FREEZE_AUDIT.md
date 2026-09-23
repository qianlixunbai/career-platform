# FINAL FREEZE AUDIT — Career Platform

> 审查日期：2026-09-23
> 审查范围：`career-platform` 全仓库（后端 / 前端 / SQL / 文档 / 测试）
> 审查类型：FINAL FREEZE REVIEW（封版前最后一次系统性检查）
> 基线 commit：`e91823f`（`docs: correct JD evidence validation description`），工作区起始状态为 clean
> 审查方式：全量代码通读 + 真实 MySQL 全量回归 + 真实运行时 HTTP 冒烟（含越权/JWT 篡改探针）+ 前端 typecheck/build

---

## 1. Final Verdict

### ✅ READY TO FREEZE

**结论：当前项目已经适合冻结，可以作为求职 / 答辩展示版本。**

判定依据（每一项都经过本轮实测，不是引用历史文档）：

| 封版门槛 | 结论 | 本轮证据 |
| --- | --- | --- |
| 能正常运行 | ✅ | 后端在 AI 全部关闭时正常启动（7.19s / 11.67s 两次实测）；前端 `typecheck` / `build` exit 0；**全新部署路径实测 9 脚本从空库建库成功且 schema 与现有库 diff 为 0**；前端 `npm run dev` → Vite proxy → 后端 API 全链路打通 |
| 主要业务正确 | ✅ | 完整主链路（用户→职业/岗位→学习→简历→投递→面试/Offer/复盘）63/63 运行时断言通过 |
| 没有明显安全问题 | ✅ | 28 项安全探针全通过；仓库密钥扫描干净；越权路径 0 处 |
| 数据一致性 | ✅ | 全量 328 项回归 0 失败；行锁 + 唯一约束 + 复合 owner FK 三层保护，实测并发/重复路径均返回 409 而非脏数据 |
| AI / RAG 可靠 | ✅ | AI 全关时核心业务完全可用；RAG 只读、引用由 Java 重建、失败安全降级 |
| 测试通过 | ✅ | **328 / 328 PASS**，Failures 0 / Errors 0 / Skipped 0，`BUILD SUCCESS` |
| 仓库干净 | ✅ | 无密钥、无 build 产物、无 IDE 文件、无 `.env`；工作区仅含本轮 4 个有意修改；本轮创建的测试数据与临时库已全部清理 |

**唯一的 P1 缺陷（Offer 在投递结束后永久卡在「考虑中」）已在本次审查中修复，并补充了最小必要测试。**
除该 P1 外，未发现任何 P0，其余均为不影响封版的 P2 / P3。

**为什么不是 `READY TO FREEZE WITH MINOR ISSUES`：**
P1 已修复且回归通过，P2/P3 项均不构成数据、安全或演示风险。按封版标准，可以冻结。

---

## 2. Test Result

### 2.1 后端全量回归（本轮实际执行，真实 MySQL 8）

```
mvnw.cmd -B test
[INFO] Tests run: 328, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 指标 | 数值 |
| --- | ---: |
| 测试类数量 | **52** |
| 测试方法数量 | **328** |
| Passed | **328** |
| Failed | **0** |
| Skipped | **0** |
| 构建结果 | **BUILD SUCCESS** |

> 说明：README 记录的历史基线为 327 项（2026-09-07）。本轮执行前实测即为 **327/327 PASS**，与文档一致；修复 P1 后新增 1 项测试，最终为 **328/328 PASS**。

**测试是否真的覆盖 Service / DB 行为（不是只测 Mock）：**

| 类型 | 代表测试 | 说明 |
| --- | --- | --- |
| 真实 MySQL 集成（MockMvc + 真实表） | `DatabaseSchemaIntegrationTests` 6、`CareerIntegrationTests` 13、`LearningCoreIntegrationTests` 11、`LearningSupportIntegrationTests` 6、`ResumeIntegrationTests` 9、`ResumeFileIntegrationTests` 15、`ApplicationIntegrationTests` 9、`ProfileIntegrationTests` 5、`AuthenticationIntegrationTests` 6、`RagMaterialIntegrationTests` 8、`JdAiIntegrationTests` 11 等 | 直接落库、直接断言 HTTP 状态码与 JSON，覆盖真实 FK / 唯一约束 |
| 真实并发 | `LearningPlanTaskRaceIntegrationTests` 3、`LearningReviewConcurrencyIntegrationTests` 1、`ApplicationIntegrationTests#concurrentCreateAllowsExactlyOneOngoingApplication` | 真双线程竞争，断言"恰好一成功一 409" |
| 真实事务回滚 | `LearningServiceAiConfirmTransactionIntegrationTests` 3 | MyBatis `Executor.update` 拦截器在第 2 条 INSERT 处抛异常，事务外验证两行零残留 |
| Schema 元数据 | `DatabaseSchemaIntegrationTests` | 从 `information_schema` 校验表、复合 FK 列序、关键唯一索引 |
| AI 离线（不调真实 Provider） | `SpringAiToolCallingGatewayTest` 30、`JobSearchSessionTest` 23、`JdParseServiceTest` 13、`RagServiceTest` 12、`JobDiscoveryServiceTest` 8、`TavilyJobSearchGatewayTest` 7、`MaterialDocumentParserTest` 9 等 | 用 deterministic fake `ChatModel` / transport seam，覆盖预算、越界、非法 JSON、SSRF 主机、XML 安全等 |
| AI 未配置可启动 | `AiOpenAiWithoutKeyContextTest`、`JdAiDisabledIntegrationTests`、`RagConfigurationTest` | 无 key 时 Spring Context 可启动，AI 入口返回 503 |

**结论：测试质量高于"只测 Mock"的水平，主链路每一条业务规则都有真实 DB 断言保护。**
唯一需要补充的是本轮发现的 Offer 边界缺陷——已补 1 项，未做任何为凑覆盖率的新增。

### 2.2 前端

| 检查 | 结果 |
| --- | --- |
| `npm run typecheck`（`vue-tsc --noEmit`） | **PASS**（exit 0） |
| `npm run build`（`vue-tsc && vite build`） | **PASS**（exit 0，21.42s / 修复后 16.46s） |
| 主 chunk 体积 | 1,069.09 kB（gzip 350.59 kB）→ 保留已知 P2 warning |

### 2.3 运行时 HTTP 冒烟（本轮真实启动后端实测）

三次独立冒烟脚本，共 **118 条断言**，全部针对真实 `localhost:8080` + 真实 MySQL：

| 冒烟批次 | 断言数 | 结果 |
| --- | ---: | --- |
| 主链路 + 用户隔离 + AI 降级 | 63 | **63 / 63 PASS** |
| JWT 篡改 + 跨用户 RAG/简历链 + 错误体卫生 | 28 | **28 / 28 PASS** |
| 边界与降级（Offer 状态、上传校验、枚举边界） | 27 | **27 / 27 PASS** |

冒烟覆盖的关键事实：

- 未认证 / 非法 Token → 401；用户 B 对用户 A 的 Job、Company、CareerGoal、Requirement、LearningPlan、Task、Material、Chunk、Resume、ResumeVersion、Application 的读 / 改 / 删 / 复制 **全部 404**（不泄露资源存在性）。
- 篡改签名、交换 payload、`alg=none` → **全部 401**。
- 重复投递、重复周计划、重复 Offer、重复复盘、重复用户名 → **409**；FINALIZED 版本写入 / 删除 → **409**；越界日期、非法枚举、错误路径类型 → **400**。
- AI 全部关闭时：JD 解析、岗位发现、学习建议、RAG 问答 → **503**；核心业务（职业/学习/简历/投递/文件下载）**全部正常**。
- 材料上传在 Embedding 未配置时：**503 且资料落库为 `indexStatus=FAILED`，原件字节保留、可下载**（不丢数据、不崩溃）。
- 404 / 400 响应体 **不泄漏** SQL、堆栈、类名等内部实现。

### 2.4 全新部署路径验证（本轮实测）

README 声称"建 `career_platform` 库 → 按序执行 `001`–`009` → 配环境变量 → 起前后端"。此路径本轮做了端到端实测（使用临时库 `career_platform_bootcheck`，验证后已 DROP，未触碰现有数据）：

| 验证 | 结果 |
| --- | --- |
| 9 个脚本从**空库**按 README 顺序逐个执行 | **9/9 全部成功，无任何错误** |
| 产出表数量 | **30**（与现有库一致） |
| 列定义 diff（`table/column/type/nullable/default/extra`） | **288 行 vs 288 行，diff = 0** |
| 索引 diff（含复合列序与唯一性） | **97 行 vs 97 行，diff = 0** |
| 外键 diff（含列序与被引用列） | **49 行 vs 49 行，diff = 0** |
| 脚本重复执行（模拟用户重跑） | `001`–`006`、`009` **幂等安全**；`007`、`008` 报 `ERROR 1060 Duplicate column name`（**不修改、不丢失任何已有数据**，仅报错） |

**结论：新用户按 README 走全新部署路径，产出的 schema 与已验证的现有库逐列、逐索引、逐外键完全等价。**

**前端演示链路验证（本轮实测）：**

| 验证 | 结果 |
| --- | --- |
| `npm run dev` 启动 | ✅ Vite 6.4.3 ready in 3978ms，`http://localhost:5173/` |
| 首页 HTML | ✅ 200，正确注入 HMR client，`<div id="app">` 与 `/src/main.ts` 入口就位 |
| Vite 模块转换 | ✅ `GET /src/main.ts` → 200 `text/javascript` |
| **Vite proxy → 后端 API** | ✅ 经 `5173/api/v1/auth/register` → **201**；`/auth/login` → **200** 且返回真实 JWT |
| 认证边界经 proxy 是否仍生效 | ✅ 经 proxy 的未认证 `GET /api/v1/career-goals` → **401**（未被 proxy 绕过） |
| SPA history 深链 | ✅ `GET /applications/1` → **200**（fallback 正常，刷新不 404） |

**结论：答辩/演示时最可能走的那条路（`npm run dev` + 本地后端）本轮已实测打通，包括代理转发、认证边界与深链刷新。**

---

## 3. P0 Issues

### 无。

本轮未发现任何 P0 级别问题。逐条核对 P0 判据：

| P0 判据 | 核查结论 |
| --- | --- |
| 数据丢失 | 无。删除路径全部按 FK 顺序清理子表；`deletePlan` 清理全部后代；材料/简历原件失败时保留字节；无任何 `ON DELETE` 误配导致静默丢数据（`learning_material_chunk` 的 CASCADE 是唯一级联，且其父删除已显式处理） |
| 用户越权 | 无。所有 Service 方法的查询 / 更新 / 删除均带 `currentUserId`；28 项跨用户探针全部 404；SQL 层（`selectOwnedForUpdate`、`selectCandidates`、`selectOwnedFileRow`）与 Service 层双重限定 owner |
| 密钥泄漏 | 无。仓库内无真实密钥；所有敏感值均通过 `${ENV_VAR}` 占位；`git grep` 密钥模式扫描仅命中测试用假密码 `"correct-password"` |
| 项目无法启动 | 无。AI 全关时 7.19s 正常启动；测试环境 52 个 Spring Context 全部加载成功；README 明确列出 4 类必需/可选环境变量 |
| 核心流程错误 | 无。63/63 主链路断言通过，覆盖 用户→职业/岗位→学习→简历→投递→面试/Offer/复盘 全链路 |
| 严重事务问题 | 无。AI / 网络调用全部在事务外；写路径使用行锁（`FOR UPDATE`）+ 唯一约束 + 冲突转 409；`JobDiscoveryService` 用 `REQUIRES_NEW` 隔离确认事务；`LearningMaterialIngestionService` 用 `TransactionTemplate` 把长耗时的向量调用放在事务外 |

---

## 4. P1 Issues

### P1-1 — Offer 在投递结束后永久卡在「考虑中」，且错误信息误导 【已修复】

| 项 | 内容 |
| --- | --- |
| 文件 | `src/main/java/com/careerplatform/application/service/ApplicationService.java`（`updateOffer`）<br>`frontend/src/views/application/ApplicationDetailView.vue` |
| 严重级别 | P1 — 明确边界 Bug（状态永久卡死 + 错误信息误导，UI 可达） |
| 处理状态 | **已修复并回归通过** |

**复现路径（真实运行时实测，非推断）：**

1. 创建投递 → 记录 Offer（`currentStage = OFFER`，`offer.status = CONSIDERING`）。
2. 通过「结束投递」把投递置为 `ENDED`，结束原因选 `NO_LONGER_INTERESTED` 等（`OFFER → ENDED` 在状态机中合法）。
3. 此时 Offer 仍是 `CONSIDERING`。前端 Offer 卡片的「编辑」按钮**只判断了 `offer.status === 'CONSIDERING'`，没有判断 `isEnded`**，因此按钮仍然可点。
4. 用户选择「接受 / 拒绝」并保存 → 后端 `moveStage` 从 `ENDED` 迁移到 `ENDED`，抛出：

```
409 {"code":"INVALID_RESOURCE_STATE","message":"不允许从 ENDED 迁移到 ENDED"}
```

**危害：**

- Offer 行永久停留在 `CONSIDERING`，任何客户端都无法再更新（`ENDED` 是终态）。
- 错误信息对用户完全不可理解（"从 ENDED 迁移到 ENDED"），答辩演示时若被问到会显得像未处理的异常。
- 前端「Offer 已结束，信息仅供查看」的只读提示也不会出现（因为条件只判断 `status !== 'CONSIDERING'`），界面状态自相矛盾。

**修复内容（最小改动，涉及 3 个文件）：**

- 后端 `updateOffer`：在既有「已终结 Offer 不可修改」判断之后，增加显式终态判断，抛出 `投递已结束，Offer 只能查看`。**该路径原本就必然失败，因此只改错误语义，不改变任何既有成功行为**。
- 前端：编辑按钮加 `&& !isEnded`；只读提示条件改为 `offer.status !== 'CONSIDERING' || isEnded`；`openOfferEdit()` 增加 `isEnded.value` 守卫，避免任何入口打开必然失败的对话框。
- 新增 1 项集成测试 `offerIsReadOnlyWhenApplicationEndsWhileItIsStillConsidering`，锁定该规则（断言 409 + 精确文案 + 投递仍为 `ENDED` + Offer 仍为 `CONSIDERING` 未被改写）。

**修复后验证：**

- 定向：`ApplicationIntegrationTests` **9/9 PASS**（原 8 项 + 新增 1 项）。
- 全量：**328/328 PASS，BUILD SUCCESS**。
- 前端：`typecheck` + `build` 均 exit 0。
- 运行时复测：接受 / 拒绝 Offer 均返回 `409 {"code":"INVALID_RESOURCE_STATE","message":"投递已结束，Offer 只能查看"}`，投递仍 `ENDED`、Offer 仍 `CONSIDERING`（未被误改）。

### P1-2 ~ P1-n

**无。** 除上述一项外，本轮未发现其他 P1 级别问题。

---

## 5. P2 Issues

> 记录但原则上不修改。以下均已确认为**不影响封版、不影响演示、不构成数据或安全风险**。

| # | 问题 | 文件 | 为什么不改 |
| --- | --- | --- | --- |
| P2-1 | 全局异常处理缺少兜底 `@ExceptionHandler(Exception.class)`，也未映射 `DataIntegrityViolationException`。未被 Service 显式包裹的 DB 完整性错误会走 Spring Boot 默认 500 响应体，缺少前端约定的 `code`/`message` 字段（前端 `getApiErrorMessage` 会回退到通用文案）。**不泄漏内部信息**（`server.error.include-message` 默认 `never`） | `common/exception/GlobalExceptionHandler.java` | 现有所有写路径都已用 `try/catch` 把冲突转成 409；兜底处理器属于"防御未来"，改动会影响全部异常路径，封版前不值得冒回归风险 |
| P2-2 | `MethodArgumentNotValidException` 处理用 `getFieldErrors().getFirst()`。若将来新增**类级**校验约束（只产生 global error），`getFieldErrors()` 为空 → `NoSuchElementException` → 500 而非 400。当前**无任何 DTO 使用类级约束**，属潜在隐患 | `common/exception/GlobalExceptionHandler.java:119` | 现状不可触发；改为 `getGlobalErrors()` 兜底属健壮性改进，非当前缺陷 |
| P2-3 | `ProfileService.listUserSkills` 与 `getResumeSnapshot` 对每个 `UserSkill` 单独查 `Skill`，存在 N+1 | `profile/service/ProfileService.java` | 已在 `DEVELOPMENT_STATUS.md` 记为已知技术债；当前单用户技能量级下无实际性能影响 |
| P2-4 | `job_requirement` / `job_note` 表**没有 `user_id` 列**，FK 为 `job_requirement.job_id → job(id)`（非 owner-aware 复合 FK）。归属完全依赖 Service 先校验父 Job owner | `sql/003_*.sql` | Service 层已 100% 覆盖（`getOwnedJob` 前置校验），`deleteJob` 也先清理子表；仅意味着"数据库自身无法独立强制子表归属"。改动需迁移脚本，风险高于收益 |
| P2-5 | `skill` 是**全局共享字典**，任意已认证用户可 `POST /api/v1/skills` 新增，理论上可污染他人看到的技能列表 | `profile/controller/SkillController.java` | 这是刻意的产品设计（README 明确"全局 Skill"，JD 确认要求必须引用已有全局 Skill，未解析技能不自动创建）。属共享资源而非用户私有数据，不构成越权 |
| P2-6 | `JobDiscoveryCandidateStore.confirm` 在持有 `synchronized(entries)` 监视器期间执行数据库确认事务 | `ai/service/JobDiscoveryCandidateStore.java` | 刻意设计（保证一次性消费语义）。课程量级下无影响；并发量放大后才是问题 |
| P2-7 | 主前端 chunk 约 1.07 MB（gzip 350 kB），触发 Vite 体积告警 | `frontend/vite.config.ts` | 历史已记录的 P2；需引入 `manualChunks` 才可消除，封版前重构构建配置风险 > 收益 |
| P2-8 | `POST /applications` 允许为**已归档**（`archived=true`）岗位创建投递 | `ApplicationService.createApplication` | 前端 `ApplicationsView` **刻意同时加载 active + archived 岗位**用于选择，说明这是有意的产品行为（归档=隐藏，非禁用）。若要收紧属产品决策，非 Bug |
| P2-9 | 除 RAG 控制器与文件下载外，多数私有数据 GET 接口未设置 `Cache-Control: no-store` | 各 Controller | 浏览器端本地演示场景无中间缓存；仅属纵深加固，非漏洞 |
| P2-10 | `LearningMaterialController` 存在第二个 public 构造器 `(LearningService)`，会把 `ingestionService` 置为 `null`（测试用 seam） | `learning/controller/LearningMaterialController.java` | 当前由 `@Autowired` 构造器生效，Spring 装配正确、测试全绿。若将来误删 `@Autowired` 会成为隐患，值得在注释中保留提醒 |
| P2-11 | `MaterialDocumentParser.parseDocx` 内层 `if` 块缩进错乱（**逻辑经逐行核对正确**，仅排版异常） | `learning/service/MaterialDocumentParser.java:~110` | 纯格式化，无行为影响。封版阶段不做无意义改动 |
| P2-12 | 大对象（简历原件、学习资料原件）以 `MEDIUMBLOB` 存于 MySQL | `sql/007`、`sql/009` | 刻意的架构选择（不引入对象存储），并有 5 MiB/文件、20 文件、100 MiB/计划的硬上限保护。课程项目合理 |
| P2-13 | `application.properties` 中 `DB_PASSWORD`、`JWT_SECRET` 无默认值，缺失时启动失败 | `src/main/resources/application.properties` | 这是**有意的 fail-fast**（避免提交默认密钥）。README 已完整列出变量名。仅需使用者按文档配置 |
| P2-14 | 无 CORS 配置，SPA 需依赖 Vite dev proxy 或同源部署 | — | 本地开发/演示链路（`vite proxy → :8080`）已验证可用；生产化部署才需要 |
| P2-15 | `transition` 状态机允许**向前跳级**（如 `APPLIED → INTERVIEW`、`APPLIED → OFFER`），不强制经过 `ASSESSMENT` | `ApplicationService.transitions()` | 刻意设计（`DEVELOPMENT_STATUS.md` 明确"支持向前跳级"），符合真实求职流程的多样性 |
| P2-16 | README 原文把 `007`–`009` 统称"增量迁移脚本…避免重复 `ALTER`"，**不精确**：实测 `009` 用的是 `CREATE TABLE IF NOT EXISTS`，重复执行是安全的；只有 `007`、`008` 的 `ALTER` 会报 `Duplicate column name`。新用户重跑脚本会看到 2 个红色报错并可能误判部署失败 | `README.md:181` | **已修正**（纯文档 1 行，无代码/行为变更）。修正为按脚本区分幂等性，并说明报错不影响已有数据 |

---

## 6. Security & User Isolation（专项）

### 6.1 认证

| 项 | 结论 |
| --- | --- |
| 认证机制 | 轻量 MVC `HandlerInterceptor` + JWT（JJWT 0.13.0），非 Spring Security |
| 保护范围 | `/api/v1/**` 全保护，仅放行 `/api/v1/auth/register`、`/api/v1/auth/login` |
| Token 解析 | `JwtTokenService.parseToken` 用 `verifyWith(signingKey)` 验签，捕获 `JwtException \| IllegalArgumentException` 统一转 401 |
| 账号状态 | 每次请求调用 `isActiveUser(userId)` 校验 `status = ACTIVE`，禁用账号的旧 Token 立即失效 |
| 用户身份来源 | 仅来自 Token 中的 `userId` claim（写入 request attribute，由 `@CurrentUserId` 解析器注入）。**没有任何接口接受客户端提交的 `userId` 作为归属依据** |
| 密码存储 | BCrypt（`BCryptPasswordEncoder`），并前置校验 UTF-8 字节长度 ≤ 72（规避 BCrypt 静默截断） |
| 用户枚举 | 用户名不存在与密码错误返回**同一个** 401 `INVALID_CREDENTIALS` 与同一文案 |

### 6.2 实测攻击探针（全部拦截）

| 攻击 | 结果 |
| --- | --- |
| 无 `Authorization` 头 | 401 |
| `Bearer` 后为空 | 401 |
| 随机字符串 Token | 401 |
| 篡改签名（改动签名末位） | **401** |
| 用 B 的 payload 拼 A 的签名 | **401** |
| `alg: none` 攻击 | **401** |
| 伪造三段式 `a.b.c` | 401 |
| 未知用户登录 | 401（与密码错误同码同文案） |

### 6.3 用户隔离（逐资源实测）

对用户 A 的全部私有资源，用用户 B 的合法 Token 发起读 / 改 / 删 / 复制，**全部返回 404 `RESOURCE_NOT_FOUND`**（刻意不区分"不存在"与"不属于你"，不泄露资源存在性）：

| 资源 | 读 | 改 | 删 | 其他 |
| --- | --- | --- | --- | --- |
| CareerGoal | 404 | — | — | — |
| Company | 404 | — | — | B 用 A 的 companyId 建 Job → 404 |
| Job | 404 | 404 | 404 | — |
| JobRequirement | 404（列表） | — | — | — |
| LearningPlan / Task | 404 | — | — | — |
| LearningMaterial | 404 | 404 | 404 | 文件下载 404、Chunk 404、RAG status 404 |
| Resume / ResumeVersion | 404 | 404（写 item） | 404 | 复制 404 |
| Application | 404 | — | — | 状态迁移 404、用 A 的 FINALIZED 版本投递 → 404 |

**代码层核查（非仅探针）：**
- 全部 7 个 Service（`UserService` / `ProfileService` / `CareerService` / `LearningService` / `ResumeService` / `ApplicationService` / 各 AI Service）的每一个 public 方法签名均携带 `userId`，无例外。
- 全部自定义 SQL（`selectOwnedForUpdate` × 4、`selectCandidates`、`selectOwnedFileRow`、`selectMetadataByVersionAndUser`、`selectContentByVersionAndUser`、`countUploadedInPlan`、`sumUploadedBytesInPlan`、`countReadyChunksExcluding`）均带 `user_id = #{userId}` 谓词。
- `RagService.query` 在 SQL 之外**再校验一次** `userId` 与 `embeddingIdentity`，并在网络 I/O 之后**重新读取 citation 原文**比对，防止跨用户或过期证据被服务。

### 6.4 其他安全面

| 面 | 结论 |
| --- | --- |
| SQL 注入 | 无。全部使用 MyBatis-Plus `LambdaQueryWrapper` 参数绑定；自定义 SQL 用 `#{}`；仅有的 `.last("LIMIT " + 常量)` 拼接的是**编译期常量**，不含用户输入 |
| SSRF | 有专门防护。`JobSearchSession` 拒绝非 http/https、含 userInfo、localhost/.localhost/.local、IPv4 私网段（0/8、10/8、100.64/10、127/8、169.254/16、172.16/12、192.168/16、192.0.0/24、198.18/19、≥224）、IPv6 ULA `fc00::/7`、含 `%` 的主机；Tavily 端点写死为常量，不可被请求/环境变量覆盖；HTTP 客户端 `Redirect.NEVER` |
| XXE | 有防护。`MaterialDocumentParser` 关闭 `SUPPORT_DTD` 与外部实体，遇到 `DTD` / `ENTITY_REFERENCE` 事件直接判非法；DOCX 仅解析 `word/document.xml` 且校验 WordprocessingML 命名空间 |
| 上传安全 | 双重校验：`ResumeFileValidator` / `MaterialDocumentParser` 同时校验扩展名、魔数（`%PDF-`、`PK\x03\x04`）、DOCX 必需条目（`[Content_Types].xml` + `word/document.xml`）、ZIP 条目数 ≤ 200、解压总量 ≤ 10 MiB、正文 ≤ 100 000 字符；文件名剥离控制字符与路径分隔符，长度截断保留扩展名；下载响应带 `Content-Disposition: attachment`、`X-Content-Type-Options: nosniff`、`Cache-Control: no-store` |
| 资源耗尽 | 有上限：文件 5 MiB、单计划 20 文件 / 100 MiB、单计划 READY chunk ≤ 1000、RAG 候选 ≤ 1000 / TOP_K=4 / 上下文 ≤ 4000 字符、JD 候选 ≤ 50、AI 警告 ≤ 20、工具调用 ≤ 2 次 / 模型请求 ≤ 3 次、PDF ≤ 100 页 |
| 提示注入 | AI 输入全部按不可信数据处理：`userId`、raw JD、笔记、记录、复盘等作为 untrusted data 传入；生产 Gateway 固定 `toolChoice="none"`（JD/Learning）或仅暴露单一白名单工具（Job Discovery）；模型返回的引用 key 必须命中 Java 侧白名单，未命中即丢弃；`JobSearchSession` 只把不含 URL/host 的 `SearchHit` 暴露给模型 |
| 错误信息泄漏 | 实测 400/404/409/503 响应体均只含 `code` + 中文 `message` + `timestamp`，无 SQL、堆栈、类名。AI Provider 错误体**从不读取或记录**（自定义 `ResponseErrorHandler` 直接抛业务异常） |
| 日志泄漏 | 无。全仓库 `log.*` 语句不含 password / token / apiKey / secret / authorization；AI Provider 异常只记录异常类名 |
| 仓库密钥 | 无。源码、配置、README、docs 全部使用 `${ENV_VAR}` 占位；`application.properties` 中的 `not-configured` 哨兵值仅为让配置存在而不提交真实 key，Gateway 仍会拒绝实际调用 |

---

## 7. Database & Transaction（专项）

### 7.1 Schema 与约束

- 30 张 DDL 表（29 业务表 + 1 技术表 `learning_material_chunk`），`001`–`009` 顺序迁移脚本，本轮**实测数据库 schema 与脚本完全一致**（含 `user_profile.email`、`learning_material` 的 7 个 RAG 列、`resume_file`、`application.ongoing_job_id` 生成列）。
- **owner-aware 复合外键**是本项目最扎实的设计：`job(company_id,user_id)→company(id,user_id)`、`learning_task(plan_id,user_id)→learning_plan(id,user_id)`、`study_record(task_id,user_id)→learning_task(id,user_id)`、`resume_version(resume_id,user_id)→resume(id,user_id)`、`application(resume_version_id,user_id)→resume_version(id,user_id)`、`resume_file(resume_version_id,user_id)→resume_version(id,user_id)` 等。**数据库层面即无法把 A 的数据挂到 B 的资源下**。
- 关键唯一约束齐备：`app_user.username`、`skill.name`、`user_skill(user_id,skill_id)`、`user_profile.user_id`、`learning_plan(user_id,week_start)`、`weekly_review.plan_id`、`offer.application_id`、`final_review.application_id`、`resume_version(resume_id,version_no)`、`resume_file.resume_version_id`、`learning_material_chunk(material_id,chunk_index)`。
- **并发去重的亮点**：`application.ongoing_job_id` 是 `GENERATED ALWAYS AS (CASE WHEN current_stage <> 'ENDED' THEN job_id END) STORED` 生成列，配合 `UNIQUE(user_id, ongoing_job_id)` 在**数据库层面**保证"同一用户同一岗位同时只有一个进行中投递"（`ENDED` 时生成列为 NULL，MySQL 唯一索引允许多个 NULL，因此历史投递不冲突）。这是 Java 校验 + DB 约束双重保护的正确范例。
- 索引覆盖：所有高频列表查询都有 `(user_id, ...)` 前缀索引；`idx_application_user_stage_updated`、`idx_resume_content_item_version_user_sort` 等与 ORDER BY 对齐。

### 7.2 事务边界（逐项核查）

| 检查点 | 结论 |
| --- | --- |
| 是否有外部 AI / 网络调用被放进长事务 | **没有**。`JdParseService.parse` 无 `@Transactional`；`LearningAiService.suggestPlan/suggestReview` 无 `@Transactional`（只有 `LearningAiContextBuilder` 的**只读**查询事务）；`RagService.query` 无事务；`LearningMaterialIngestionService.index` 把 `embeddingGateway.embed()` 放在 `TransactionTemplate` **之外**；`JobDiscoveryService.discover` 无事务（只有 `confirm` 用 `REQUIRES_NEW`） |
| 一个业务流程多个写操作是否可能部分成功 | 不会。`createPlanWithTasks`（Plan + n Tasks）单一事务；`JdParseService.confirm` 在自身 `@Transactional` 内循环调用 `createRequirement`（`REQUIRED` 传播，加入同一事务）；`moveStage` 的 currentStage 更新 + History 追加同事务；`ResumeService.generateVersion` 的 Version + n Items 同事务；`upsertFinalReview` / `createOffer` 的状态迁移与业务写入同事务 |
| 异常能否正确触发回滚 | 能。所有 `RuntimeException`（含业务异常）默认回滚；`DataIntegrityViolationException` 在 `deleteCompany` / `deleteJob` 内被捕获后转成 `ResourceInUseException`，**该事务本身仍会回滚**（Spring 事务在异常穿越 `@Transactional` 代理时标记 rollback-only，Service 内的 catch 不影响外层回滚语义）。`LearningServiceAiConfirmTransactionIntegrationTests` 已用真实事务断言"两行零残留"验证 |
| 唯一约束是否缺失 | 不缺失（见 7.1）。且所有"先查后插"路径都在 insert 处 `catch DuplicateKeyException` 转 409，形成 Java 预检 + DB 兜底双保险：`UserService.register`、`ProfileService.upsertProfile`/`createUserSkill`、`CareerService.createSkill`、`LearningService.createPlan`/`createPlanWithTasks`/`updatePlan`、`ApplicationService.createApplication`/`createOffer`/`upsertFinalReview`、`LearningService.upsertReview` |
| 并发下的正确性 | 用**行锁 + 条件更新**双保险：`selectOwnedForUpdate`（Job / Application / Resume / ResumeVersion / LearningPlan / LearningMaterial）、`moveStage` 用 `.eq(currentStage, fromStage)` 做乐观条件更新。`LearningPlanTaskRaceIntegrationTests`、`LearningReviewConcurrencyIntegrationTests`、`ApplicationIntegrationTests#concurrentCreateAllowsExactlyOneOngoingApplication` 均为真并发测试且全绿 |
| 逻辑删除 / 状态字段 | 无逻辑删除（`archived` 是 Job 的隐藏标记，非删除）；所有状态字段为 Java 枚举 + `VARCHAR` 列，状态迁移集中在 `ApplicationService.transitions()` 与 `ResumeService.requireDraft()`，未散落在 Controller |
| 错误返回 | 覆盖完整：资源不存在 404、越权 404、状态非法 409、重复 409、被引用 409、参数非法 400、AI 不可用 503、AI 返回非法 502 |

### 7.3 性能（只记录明显项）

- 无 N+1 之外的严重问题；`ProfileService.listUserSkills` / `getResumeSnapshot` 的逐项 `getSkill` 是唯一明显 N+1（P2-3）。
- 所有列表查询均有 `user_id` 前缀索引支撑，无全表扫描风险。
- 无重复查询数据库的明显模式（`requireOwnedPlan` 等在写路径会重复查询，但同时承担锁与校验职责，属有意设计）。

---

## 8. AI / RAG（专项）

### 8.1 核心原则验证：AI 不可用时主业务完全可用 —— ✅ 实测通过

真实启动后端并**关闭全部 AI / Embedding / Tavily**，实测结果：

| 入口 | 关闭 AI 时的行为 |
| --- | --- |
| `POST /jobs/{id}/ai/jd-parse` | 503 `AI_SERVICE_UNAVAILABLE` |
| `POST /jobs/ai/discovery` | 503 `AI_SERVICE_UNAVAILABLE` |
| `POST /learning-plans/ai/plan-suggestion` | 503 `AI_SERVICE_UNAVAILABLE` |
| `POST /learning-plans/{id}/materials/rag/query` | 503 `AI_SERVICE_UNAVAILABLE` |
| 职业 / 岗位 / 学习 / 简历 / 投递全链路 | **全部正常**（63 条断言通过） |
| 材料上传（Embedding 未配置） | 503，但**资料元数据 + 原件字节已落库**（`indexStatus=FAILED`），列表可查、原件可下载 |
| 应用启动 | **正常**（7.19s），无 Bean 缺失错误 |

**结论：AI 是真正的辅助功能，与核心业务解耦彻底。**

### 8.2 逐项核查

| 检查点 | 结论 |
| --- | --- |
| AI 失败是否影响核心业务 | 不影响。`AiChatGateway.generateStructured` 在未配置时直接抛 `AiServiceUnavailableException`；异常经 `GlobalExceptionHandler` 映射为 503，不污染任何业务表 |
| 超时 / 异常处理 | 有界：Chat 走 Spring AI + JDK HttpClient（连接 5s / 读 40s）；Embedding 连接 5s / 请求 30s、单批 ≤ 16 条、响应 ≤ 1 MiB；Tavily 连接 5s / 请求 20s、响应 ≤ 1 MiB（自定义 `BoundedBodySubscriber` 超限即 `cancel()` 流）；Embedding Gateway 显式**不做 SDK 重试**，避免重试放大 |
| AI 返回非法格式是否安全失败 | 安全。JD / Learning / Job Discovery 三条链路都有极严格的 Java 侧校验（长度、条数、枚举、重复、范围、排序唯一、证据子串必须能在 JD 中命中、dueDate 必须在周期内、总分钟不得超预算）；任何不符抛 `AiInvalidResponseException` → **502**，不写库。Job Discovery 的最终响应还刻意绕开 `BeanOutputConverter` 的日志路径（避免原始模型文本被打印） |
| AI 建议是否在用户确认前写入正式数据 | **绝不**。三条写路径均为 `AI proposes → user confirms → Java Service validates/writes`：JD parse 只返回候选、confirm 才写 `job_requirement`；Learning 建议是 ephemeral 响应，confirm 才由 `createPlanWithTasks` 写 Plan + Tasks；Job Discovery 只写内存候选 store（15 分钟 TTL、每用户 ≤ 50、全局 ≤ 1000），confirm 才复用 `CareerService.createJob` 写库。**模型无法生成可信数据库 ID，也不能直接提交事务** |
| Prompt / Response parsing 是否脆弱 | 不脆弱。结构化输出有 `FAIL_ON_TRAILING_TOKENS` + `STRICT_DUPLICATE_DETECTION`；工具参数做 JSON Schema 级校验（字段白名单、类型、范围、未知字段拒绝）；工具调用预算硬限制（≤ 2 次搜索 / ≤ 3 次模型请求）；首次请求强制 `toolChoice="required"`，模型不调用工具即 fail closed（Java 绝不代模型伪造搜索结果） |
| RAG 检索失败是否导致系统崩溃 | 不会。Embedding 未配置 → 503；候选为空 → 直接返回 `NO_EVIDENCE`（**跳过 Chat 调用**）；候选超 1000 → 409；相似度全部低于 0.55 → `NO_EVIDENCE`；引用 key 未命中白名单 → 502 |
| 是否存在跨用户 RAG 风险 | 无。`selectCandidates` 在 SQL 层同时限定 `c.user_id`、`m.user_id`、`m.plan_id`、`m.index_status='READY'`、双 `embedding_identity`；Service 层再校验一次；网络 I/O 后重新读取 citation 原文比对，防止"引用过期/被替换的证据"。实测用户 B 访问用户 A 的 material / chunk / rag status 全部 404 |
| AI 功能是否与核心业务解耦 | 是。AI 独立分包（`ai/client`、`ai/service`、`ai/config`），专用 Gateway 接口（`AiChatGateway` / `AiToolCallingGateway` / `RagChatGateway` / `EmbeddingGateway` / `JobSearchGateway`），互不共享可变业务 Tool；生产模型硬锁 `deepseek-v4-flash`，不存在 `AI_MODEL` 环境变量或 Pro fallback |
| API Key / 敏感配置泄漏 | 无。全部 key 仅来自 `${AI_API_KEY}` / `${TAVILY_API_KEY}` / `${EMBEDDING_API_KEY}`；`isConfigured()` 在**每次调用时**再校验一次开关+凭据，避免"部分配置"触发付费调用；Provider 错误响应体从不读取或记录 |
| RAG 是否只读 | 是。`RagService` 无任何写 Mapper 操作、无工具调用（类注释即声明此约束），实测 RAG 问答不修改学习计划或任何业务数据 |
| Citation 是否可信 | 是。模型只返回 citation key，Java 从**本次检索集**重建 `materialId`、chunk、位置、页码与原文；未知 key 不会成为引用；`PDF` 保留真实页码，`DOCX` 使用段落位置（不做 OCR、不推算页码） |

### 8.3 AI 侧遗留

| 项 | 级别 | 说明 |
| --- | --- | --- |
| DOCX 仅校验 Word root namespace | P2 | 已在 `DEVELOPMENT_STATUS.md` 记录；对非 Word 命名空间的 DOCX 会判非法（安全侧 fail closed，功能侧略严格） |
| 数据库不可用时 `FAILED` 写入可能失败 | P2 | `markFailedAfterUpload` 已 `try/catch` 保护并保留原始异常语义，资料仍为可重试的 `UPLOADED`/`FAILED` 状态，不丢数据 |
| 真实 Provider 端到端只在历史 Closing 验证过 | P2 | 本轮 AI 全部关闭运行（这是**有意的**：验证降级路径）。真实 DeepSeek / Jina / Tavily 联调需使用者本地提供 key 后自行验证，历史证据见 `docs/M7_RAG_CLOSING_VERIFICATION.md` |

---

## 9. Repository Hygiene（专项）

| 检查项 | 结果 |
| --- | --- |
| 硬编码密钥 / Token / Password | ✅ 无。全仓库 `git grep` 扫描 `sk-*`、`tvly-*`、`AKIA*`、`BEGIN PRIVATE KEY`、`password=...`、`api_key=...` 等模式，**仅命中测试用假密码字符串 `"correct-password"`**（18 处，均在 `src/test` 内） |
| `.env` / 凭据文件 | ✅ 无。`frontend/.gitignore` 已忽略 `.env`、`.env.local`、`.env.*.local`、`*.local` |
| build 产物 | ✅ 未提交。`target/`、`frontend/dist/`、`frontend/node_modules/` 均被忽略；`git ls-files` 中无 `.class`、`.jar`、`dist/`、`node_modules/` |
| IDE 文件 | ✅ 未提交。`.idea`、`*.iml`、`*.iws`、`*.ipr`、`.vscode/`、`.settings` 均被忽略 |
| 日志 / 临时文件 | ✅ 无。仓库内无 `*.log`、无临时脚本、无编辑器备份 |
| 本地数据库信息 | ✅ 无。`spring.datasource.url` 指向 `localhost:3306/career_platform`（本地约定，非秘密）；账号密码走 `${DB_USERNAME:root}` / `${DB_PASSWORD}` |
| `.gitignore` 完整性 | ✅ 合理。Maven `target/`、Node `node_modules/`/`dist/`、STS/IDEA/NetBeans/VS Code、`.env*` 均覆盖。**注**：`.mvn/wrapper/maven-wrapper.jar` 被忽略是模板遗留，但 `maven-wrapper.properties` 使用 `distributionType=only-script`，wrapper 脚本自行下载 Maven 发行版，**不依赖该 jar**，因此新克隆可正常使用 `mvnw` / `mvnw.cmd` |
| `git status` | ✅ 干净起始。审查开始时工作区无任何未提交改动；结束时仅有本轮 4 个有意修改的文件（`ApplicationService.java`、`ApplicationDetailView.vue`、`ApplicationIntegrationTests.java`、`README.md`）以及新增的 `FINAL_FREEZE_AUDIT.md` |
| README 是否让新用户知道如何启动 | ✅ **合格**。包含：定位与业务主线、Core Workflow 图、模块清单、4 项 AI 功能说明、Key Business Rules、架构图、技术栈表（含精确版本号）、项目规模统计、测试证据表（并**诚实标注证据时间边界**）、Quick Start（前置条件 + 9 个 SQL 脚本执行顺序 + 环境变量名清单 + 启动命令）、设计原则、文档索引 |
| Java / Node / MySQL 版本是否说明 | ✅ 是。Java 21、MySQL 8、Node.js / npm、Spring Boot 3.5.14、MyBatis-Plus 3.5.17、Spring AI 1.1.8 均在 README 明确 |
| 环境变量是否说明 | ✅ 是。按 Database/JWT、Chat AI、Tavily、Embedding 四组列出**变量名**（不含值），并说明哪些是可选、缺失时的降级行为 |
| 文档诚实度 | ✅ 优秀。`DEVELOPMENT_STATUS.md` 明确区分"本轮执行"与"历史证据"，多处标注"本轮未重跑，不能表述为本轮结果"，并列出已知技术债与未实现范围。**这是本次审查中质量最高的部分之一**。本轮仅发现 1 处不精确（建库脚本幂等性表述，见 P2-16），已修正 |
| 建库脚本可用性 | ✅ 实测通过。9 个脚本从空库按序执行 9/9 成功，产出 schema 与现有库逐列/逐索引/逐外键完全一致（详见 2.4） |
| 测试数据残留 | ✅ 已清理。本轮运行时冒烟创建的 **19 个测试账号**（18 个主链路/安全/边界账号 + 1 个 proxy 验证账号）及其全部子资源（含 1 个自建全局 Skill）已按 FK 顺序在单事务内精确删除，复查剩余 0 条；账号总数 184 → 166。临时库 `career_platform_bootcheck` 与全部临时脚本/样本文件均已删除 |

---

## 10. Changes Made

本轮**只做了一处功能性修复 + 一处文档修正**，共 4 个文件、39 行新增、4 行修改。

### 10.1 修改清单

| 文件 | 变更 | 性质 |
| --- | --- | --- |
| `src/main/java/com/careerplatform/application/service/ApplicationService.java` | `updateOffer` 增加终态守卫：投递已 `ENDED` 时抛 `InvalidResourceStateException("投递已结束，Offer 只能查看")` | **P1 修复**（+6 行） |
| `frontend/src/views/application/ApplicationDetailView.vue` | ① Offer 编辑按钮增加 `&& !isEnded`；② 只读提示条件改为 `offer.status !== 'CONSIDERING' \|\| isEnded`；③ `openOfferEdit()` 增加 `isEnded.value` 守卫 | **P1 修复**（3 行修改） |
| `src/test/java/com/careerplatform/application/ApplicationIntegrationTests.java` | 新增 `offerIsReadOnlyWhenApplicationEndsWhileItIsStillConsidering`（断言 409 + 精确文案 + 投递仍 ENDED + Offer 未被改写） | **最小必要测试**（+29 行） |
| `README.md:181` | 修正建库脚本幂等性说明：区分 `001`–`006`/`009`（可安全重复执行）与 `007`/`008`（`ALTER`，重复会报 `Duplicate column name` 但不影响已有数据） | **P2-16 文档修正**（1 行修改，无代码/行为变更） |

### 10.2 不算作重要修改的内容

- 本轮**没有**任何格式化、换行、缩进、import 重排、命名调整、注释补全性质的改动。
- 本轮**没有**新增任何业务功能、依赖、框架、迁移脚本或接口。
- 本轮**没有**修改任何与已发现问题无关的代码。
- `frontend/node_modules/` 与 `target/`、`frontend/dist/` 的变化均属本地构建产物（已被 gitignore，不进版本库），不计入修改。
- 本轮为验证而创建的临时库 `career_platform_bootcheck` 与全部临时脚本/样本文件均已删除。

### 10.3 修改后的验证

| 验证 | 结果 |
| --- | --- |
| 定向测试 `ApplicationIntegrationTests` | **9 / 9 PASS**（原 8 + 新增 1） |
| 全量回归 | **328 / 328 PASS**，Failures 0 / Errors 0 / Skipped 0，`BUILD SUCCESS` |
| 前端 `typecheck` | PASS（exit 0） |
| 前端 `build` | PASS（exit 0） |
| 运行时复测 | 接受 / 拒绝 Offer 均返回 `409 {"code":"INVALID_RESOURCE_STATE","message":"投递已结束，Offer 只能查看"}`；投递仍 `ENDED`，Offer 仍 `CONSIDERING` 且未被误改 |
| 全新部署路径复测 | 9 脚本从空库 9/9 成功；列/索引/外键 diff 全为 0 |
| 前端演示链路复测 | 首页 200、`main.ts` 转换 200、proxy 注册 201 / 登录 200、proxy 未认证 401、SPA 深链 200 |

---

## 11. Remaining Technical Debt

只记录真正值得未来知道的项（按"未来会咬人"的程度排序）：

1. **异常处理的兜底契约不完整**（P2-1 / P2-2）。当前所有已知写路径都已显式转 409，但缺少 `@ExceptionHandler(Exception.class)` 与 `DataIntegrityViolationException` 映射，也缺少 `getFieldErrors()` 为空的兜底。**未来若新增一个"先查后插"却忘记 catch 的写接口，会返回不符合前端契约的 500 响应体**（不泄漏内部信息，但用户看到通用报错）。这是最值得优先补的一项。
2. **`job_requirement` / `job_note` 缺少 `user_id` 与 owner-aware FK**（P2-4）。归属完全依赖 Service 前置校验父 Job owner。当前 100% 覆盖且测试保护充分，但这是全库唯一一处"数据库无法独立强制归属"的缺口。若未来有人绕过 Service 直连 Mapper，会出现跨用户写入的窗口。
3. **认证是 Interceptor + JWT，无吊销能力**。JWT 一旦签发在有效期内始终可用（默认 3600s），无 refresh token、无黑名单、无服务端 logout。唯一可用的失效手段是禁用账号（`isActiveUser` 每次请求校验）。出现 RBAC / 多端登录 / 强制下线需求时必须换成 Spring Security。
4. **`ProfileService` 的 N+1**（P2-3）。单用户技能数量级下无影响；引入技能画像统计或批量导出时会成为瓶颈。
5. **`Skill` 是全局可变字典**（P2-5）。任意用户可新增，理论上可污染共享列表。若将来要做技能审核/去重/合并，需要重新设计归属模型。
6. **`JobDiscoveryCandidateStore` 在内存锁内执行 DB 事务**（P2-6）。一次性消费语义正确，但会把候选 store 的并发度压到 1。生产化时需改为"标记消费 + 事务后确认"的两阶段方案。
7. **大对象存 MySQL BLOB**（P2-12）。当前有硬上限保护、可用性无问题；文件量或大小增长后需要迁移到对象存储。
8. **前端主 chunk 1.07 MB**（P2-7）。演示无影响，但首屏加载可感知；需要 `manualChunks` + 路由级懒加载优化。
9. **`LearningMaterialController` 的双构造器 seam**（P2-10）。当前正确，但缺少"不要移除 `@Autowired`"的显式警示注释。
10. **真实 Provider 端到端不在自动化回归内**。AI 测试全部走 deterministic fake；真实 DeepSeek / Jina / Tavily 的联调依赖本地 key，属人工验证范畴。这是课程项目的合理取舍，但要知道 CI 无法发现 Provider 侧契约变化。

---

## 12. Freeze Recommendation

### 如果这是我的项目，我会停止继续开发，把当前版本作为求职 / 答辩展示版本。

理由：

**1. 技术门槛已经全部跨过，且是"真实"跨过。**
不是"代码看起来写了"，而是 328 项测试在真实 MySQL 上全绿、118 条运行时断言在真实启动的服务上全过、跨用户越权探针 28 项全部拦截。主链路的每一条业务规则（状态机、唯一性、owner 边界、FINALIZED 不可变、AI 写入边界）都有可复现的验证证据。

**2. 架构判断力是这个项目最值钱的部分，而不是功能数量。**
AI 写入边界（`AI proposes → user confirms → Java validates/writes`）、owner-aware 复合外键、用生成列 + 唯一索引在数据库层保证"进行中投递唯一"、AI/网络调用一律在事务外、RAG citation 由 Java 重建而非信任模型——这些是面试官会追问的点，而且每一处都有代码和测试支撑。继续加功能只会稀释这些亮点。

**3. 剩下的都是"不划算"的改动。**
P2 列表里每一项我都评估过：改动它们需要触碰异常处理全局契约、数据库迁移、构建配置或认证框架——回归面远大于收益。封版阶段"不动"比"动得漂亮"更专业。**稳定性优先于代码漂亮。**

**4. 唯一发现的 P1 已经修掉，而且修得很干净。**
该 P1 修复本身涉及 3 个文件（`ApplicationService.java`、`ApplicationDetailView.vue`、`ApplicationIntegrationTests.java`）、38 行新增、3 行修改，只改错误语义与 UI 可见性，全量回归 + 前端构建 + 运行时复测三重验证通过。这个修复本身就是一个很好的面试素材：**"我发现了状态机终态与子资源状态的组合漏洞，用最小改动消除了一个永久卡死的边界状态。"**

**5. 文档诚实度本身就是加分项。**
`DEVELOPMENT_STATUS.md` 严格区分"本轮执行"与"历史证据"，主动标注"本轮未重跑，不能表述为本轮结果"，并列出已知技术债与未实现范围。这种自我约束在应届生项目里非常少见，比任何功能都更能证明工程素养。

**6. 明确不要再做的事：**
不要再加功能（AI 面试复盘、Resume+JD matching、通用 Agent 都在范围外且不必要）；不要为了覆盖率加测试；不要重构 `ProfileService` / `CareerService`；不要引入 Redis / MQ / Elasticsearch / Spring Security；不要为了消除 1.07 MB chunk 告警重构前端。

**7. 建议接下来只做两件事：**
① 把本次的 `FINAL_FREEZE_AUDIT.md` 与修改一起提交（共 4 个文件：1 处 P1 修复 + 1 项测试 + 1 处前端守卫 + 1 行 README 修正）；② 用真实 Provider key 跑一次 AI 演示链路（JD 解析 → 学习计划 → 岗位发现 → RAG 问答）并录屏，作为答辩素材——因为真实 Provider 联调不在自动化回归内。

> 原本还要建议"在干净机器上完整走一遍 README 启动路径"，本轮已经把这条路的**可自动化部分全部实测完成**：9 个脚本从空库建库 9/9 成功且 schema 与现有库 diff 为 0，前端 `npm run dev` → Vite proxy → 后端 API 的注册/登录/未认证拦截/SPA 深链全部打通。剩下唯一无法自动化的只有"在物理上另一台机器、用另一套 MySQL 实例"这一点，风险已经很低。

---

## Interview Questions

> 约 40 个最可能针对**本项目真实代码**提出的 Java 后端校招面试问题。
> `[重点复习]` 标记表示项目在对应点上存在可见弱点或需要主动准备解释。

### A. 架构与分层

1. 为什么 Controller 不直接访问 Mapper？请用 `ApplicationService.createApplication` 说明 Service 层具体承担了哪些 Controller 不该承担的职责。
2. 你的 `currentUserId` 是怎么从 JWT 传到 Service 的？为什么用 `HandlerInterceptor` + `HandlerMethodArgumentResolver` 而不是 `ThreadLocal` 或 `@RequestAttribute` 直接取？
3. 你用 `@MapperScan` 显式列出了 6 个 mapper 包，为什么不直接扫 `com.careerplatform`？
4. 项目里 `Skill` 是全局表，其余全是 owner-scoped。为什么共享档案（Profile）被设计成"事实源"而不是第五个独立业务模块？这个决策影响了哪些表的外键设计？
5. `ApplicationService` 里 `transition()` 和 `moveStage()` 为什么拆成两个方法？`moveStage` 为什么同时更新 `currentStage` 并追加 History？

### B. 事务与并发

6. `[重点复习]` `JdParseService.parse` 为什么没有 `@Transactional`，而 `confirm` 有？如果把 AI 调用放进事务会发生什么？
7. `LearningMaterialIngestionService` 为什么用 `TransactionTemplate` 而不是 `@Transactional`？Embedding 调用具体被放在了事务的哪一侧？
8. `ApplicationService.createApplication` 为什么要先 `jobMapper.selectOwnedForUpdate`？只靠 `selectCount` 检查 ongoing 数量会有什么并发问题？
9. 同一用户同时点两次"创建投递"，数据库会发生什么？请说明行锁、Service 检查、`DuplicateKeyException` 转 409、以及 `ongoing_job_id` 生成列唯一索引这四层分别挡在哪一步。
10. `application.ongoing_job_id` 为什么用 `GENERATED ALWAYS AS (...) STORED` 而不是普通列？为什么 `ENDED` 时返回 NULL 而不是返回 job_id？MySQL 唯一索引对多个 NULL 的行为在这里起了什么作用？
11. `moveStage` 里的 `.eq(Application::getCurrentStage, fromStage)` 是什么模式？已经有行锁了为什么还需要它？
12. `LearningService.deletePlan` 为什么按"material → note → review → studyRecord → task → plan"的顺序删？如果顺序反了会怎样？
13. `@Transactional` 的默认传播行为和 `JobDiscoveryService` 用的 `REQUIRES_NEW` 有什么区别？为什么确认岗位要开新事务？
14. `[重点复习]` `ProfileService.upsertProfile` 里 `catch (DuplicateKeyException)` 之后继续 `update`，这个事务还能用吗？为什么？如果这个 catch 写在另一个 `@Transactional` 方法里会怎样？
15. 为什么 `LearningService.upsertReview` 用"先查再插/更新"而不是 `INSERT ... ON DUPLICATE KEY UPDATE`？两种方案在并发下的表现差异是什么？

### C. 数据库设计

16. 为什么 `job(company_id, user_id)` 要建复合外键指向 `company(id, user_id)`，而不是只引用 `company(id)`？这个设计在数据库层阻止了什么？
17. `company` 表为什么要加一个看起来多余的 `UNIQUE KEY (id, user_id)`？
18. `[重点复习]` `job_requirement` 和 `job_note` 没有 `user_id` 列，归属靠什么保证？这个设计有什么风险？如果要补上，需要改哪些地方？
19. `learning_note` 为什么有 `(task_id, plan_id, user_id)` 三列复合外键？这个外键能防止什么非法数据？
20. 项目里哪些表有唯一约束？请至少说出 5 个，并说明每一个分别防止了哪种重复数据。
21. `resume_version` 的 `version_no` 是"查 MAX 再 +1"得到的，为什么不会重复？如果去掉 `lockOwnedResume` 会怎样？
22. 为什么 `learning_material_chunk` 的外键有 `ON DELETE CASCADE`，而其他表都没有？这样设计安全吗？
23. 大对象（简历原件、学习资料）直接存 MySQL `MEDIUMBLOB` 有什么利弊？你设了哪些上限？

### D. 用户隔离与安全

24. 你的项目如何保证用户 A 读不到用户 B 的数据？请从 Controller、Service、Mapper/SQL、数据库约束四个层面分别说明。
25. 跨用户访问时你返回 404 而不是 403，为什么？这样做的安全收益是什么？
26. `RagService.query` 为什么要在 SQL 限定 owner 之后**再**校验一次 `userId`？"网络 I/O 后重新读取 citation 原文"是为了防什么？
27. `JobSearchSession` 里那段私网 IP 段检查是在防什么攻击？`fc00::/7`、`169.254.0.0/16`、`100.64.0.0/10` 分别是什么地址段？
28. `MaterialDocumentParser` 解析 DOCX 时做了哪些 XML 安全设置？如果不关闭 DTD 会有什么风险？
29. 文件上传你校验了哪些东西？只校验扩展名够不够？为什么还要校验魔数和 ZIP 条目？
30. 你的 JWT 用的是 HS384，算法是怎么选出来的？如果 `JWT_SECRET` 只给 16 个字符会发生什么？
31. `[重点复习]` 你的认证用 Interceptor 而不是 Spring Security，目前不支持 Token 吊销、Refresh Token、RBAC。如果面试官问"用户改了密码，旧 Token 还有效吗"，你怎么回答？
32. 为什么登录失败时"用户名不存在"和"密码错误"返回完全相同的响应？

### E. AI / RAG 设计

33. 为什么 AI 建议必须在用户确认后才写数据库？请用 JD 解析这条链路说明候选、确认、写入三个阶段各自做了什么校验。
34. `evidenceQuote` 是怎么校验的？如果模型编造了一段 JD 里不存在的话会发生什么？
35. 模型能不能直接给你一个数据库 ID 让你去写库？你的 `evidenceKeys` / `citationKeys` / `resultKey` 机制是怎么防住这一点的？
36. `[重点复习]` 你的 RAG 用了什么检索方案？为什么用 `MIN_SIMILARITY = 0.55` 这个阈值？如果所有 chunk 都低于阈值，系统会怎么做？（提示：注意它会不会调用模型）
37. 如果 Embedding 服务挂了，上传学习资料会发生什么？数据会丢吗？用户还能下载原件吗？
38. `SpringAiToolCallingGateway` 为什么要自己实现 `MAX_TOOL_CALLS = 2` / `MAX_MODEL_REQUESTS = 3` 的预算控制，而不依赖框架？第一次请求用 `toolChoice="required"` 是为了解决什么问题？
39. 你的 AI 网关为什么要把 Provider 的错误响应体"不读取、不记录"？
40. `JobDiscoveryCandidateStore` 的候选为什么放在内存里而不是数据库？TTL、每用户上限、全局上限分别是多少？为什么 `confirm` 要在锁内执行事务？`[重点复习]`

### F. 测试与工程实践

41. `LearningServiceAiConfirmTransactionIntegrationTests` 是怎么验证"事务回滚后零残留"的？为什么用 MyBatis `Executor` 拦截器而不是 Mockito spy？
42. `[重点复习]` 你的测试用了真实 MySQL 而不是 H2，为什么？这样做有什么代价？
43. `ApplicationIntegrationTests#concurrentCreateAllowsExactlyOneOngoingApplication` 是怎么构造真并发的？为什么断言"恰好一成功一 409"而不是"至少一个成功"？
44. `DatabaseSchemaIntegrationTests` 为什么要从 `information_schema` 查约束？这样做比"跑一遍业务代码"多验证了什么？
45. 你的 README 里写"327/327 PASS"时特意标注了日期并说明"本轮未重跑"。为什么这个标注很重要？

### G. 状态机与业务规则（本项目最容易被追问）

46. `[重点复习]` 你的投递状态机允许 `APPLIED → OFFER` 跳级，但不允许回退。为什么这样设计？如果业务上需要"撤销误操作"你打算怎么做？
47. `Offer` 的 `ACCEPTED` / `REJECTED` 为什么必须通过 Offer 接口而不能通过投递状态迁移接口？`transition` 里那两行校验在防什么？
48. `[重点复习]` 如果用户先结束投递（原因"已接受其他 Offer"），但之前已经记录了一个"考虑中"的 Offer，会发生什么？你是怎么发现并修复这个问题的？
49. `ResumeVersion` 只有 `DRAFT` / `FINALIZED` 两个状态，定稿为什么设计成幂等？`finalizeVersion` 里"已 FINALIZED 就直接 return"这一行如果不写会怎样？
50. 为什么 `Application` 创建时必须绑定 `FINALIZED` 版本？`FINALIZED` 之后内容不可改，这个约束在 Service 和数据库层分别怎么实现？

---

*报告生成时间：2026-09-23*
*审查执行：全量代码通读 + 真实 MySQL 全量回归（328/328）+ 真实运行时 HTTP 冒烟（118 条断言）+ 全新部署路径验证（9 脚本建库 + 列/索引/外键 diff + 脚本幂等性）+ 前端 typecheck/build + 前端 dev-server 代理链路验证 + 仓库密钥扫描 + 测试数据与临时库清理*
