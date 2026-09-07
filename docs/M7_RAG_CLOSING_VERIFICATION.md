# Milestone 7 / P2-A RAG Final Closing Report

当前结论：**GO — M7 READY FOR CHECKPOINT**。所有 required gates 已通过；Real Provider Gate 已由外部 TechLead 接受，等待其最终 GO。当前仍未 commit/push，M6A/M6B/M6C checkpoint 与旧 smoke 证据保持不动。

## 1. Preflight

- 起始与当前 branch：`main`。
- Starting HEAD：`a94c14bcc8afec01323211ce67641d8bb4ec262d`，开始时等于本地 `origin/main`，working tree clean、staged empty。
- 本轮复核 HEAD 和本地 origin/main 均仍为该提交；当前 working tree 为本轮 M7 实现及文档同步，staged empty，未 fetch、未重置、未建分支、未 commit/push。

## 2. Architecture

- 复用 LearningMaterial，保留 Plan 必填与 Task 可选关系。文件采用既有 MySQL `MEDIUMBLOB`，普通查询排除 BLOB，解决磁盘与数据库双写清理问题；没有独立 RagDocument 系统或额外存储基础设施。
- PDF：PDFBox 3.0.8，真实 page number。DOCX：JDK ZIP/StAX，Word 正文 namespace/root 检查、段落序号，禁 DTD/外部实体，不伪造页码，不抓取外部关系。
- 5 MiB/file、100 PDF 页、10 MiB ZIP 展开、200 entries、100000 文本字符、1000 字符/Chunk、128 Chunk/file。解析过程中限制文本，不等到全部文本累积后才检查。
- 独立 EmbeddingGateway，默认关闭；服务端配置 HTTPS endpoint/model/version，身份为三者的 SHA-256；OpenAI-compatible float protocol，每批最多 16 段、30 秒、1 MiB 响应、无 retry/redirect。不是 DeepSeek Embedding。
- SQL owner + plan + READY + parent/chunk embedding identity，硬限 1000 行；Java cosine、threshold 0.55、Top-K 4、context 4000 字符。发布索引时限制每计划 1000 READY Chunk。
- 模型只返回 answer/citationKeys/evidenceInsufficient；Java 从本次检索集重建来源 metadata。未知、重复、空引用键或非法结果严格拒绝，返回前再次检查 owner 与 Chunk 存在。
- 文档和问题均 UNTRUSTED DATA，RAG 无 tools、无业务写入。独立 Spring AI Chat 实例复用既有固定 Flash model factory，不修改旧 gateway；`deepseek-v4-flash`、no-tools、retry=0。解析不走会记录原文的 BeanOutputConverter 转换路径。

## 3. Persistence

- 新增一次性 `sql/007_add_learning_material_rag.sql`；用户 IDEA 本轮 RAG SQL 与新增 Schema 测试已确认相应表/列可用。含 ALTER，不能盲目重复运行；最新 MySQL Gate 已通过，007 本轮未重复执行。
- LearningMaterial 增加原件/文件信息/索引状态；新增 learning_material_chunk，包含顺序、位置、原文、JSON 向量及 identity。
- `(material_id,user_id)` owner-aware FK + ON DELETE CASCADE；`UNIQUE(material_id,chunk_index)`。原件与 metadata 同事务保存，Chunk 与 READY 同事务替换。
- 每计划最多 20 份、100 MiB 原件。删除 Plan/Material 和上传/发布索引统一先锁 Plan；删除资料/计划同时移除原件和 Chunk，Task 删除继续解绑资料关联。
- 首次索引失败保留原件并标 FAILED 供重试；重建失败保留原有 READY 和旧 Chunk。成功重建生成新 ID。网络调用不持数据库行锁；数据库宕机导致失败状态写入也失败时可保留 UPLOADED，不能称所有失败均已持久化 FAILED。
- BLOB Mapper 使用单行对象返回后解包 byte[]；已基于本地 MyBatis 源码确认直接数组返回会走 returnsMany，离线映射测试已覆盖修正。真实 SQL/事务结果已由最新 MySQL Gate（30/30，全 0）确认。

## 4. Backend

沿用 `/api/v1/learning-plans/{planId}/materials` 的 metadata CRUD/list/detail，并增加：

| 方法 | 后缀 | 行为 |
|---|---|---|
| POST | `/upload` | multipart PDF/DOCX、可选 taskId，保存原件并同步索引 |
| POST | `/{materialId}/reindex` | 从数据库原件重建 |
| GET | `/{materialId}/file` | owner-checked attachment、no-store、nosniff |
| GET | `/rag/status` | owner-checked 可用性 |
| POST | `/rag/query` | 问题最长 1000 字符，read-only RAG |
| GET | `/{materialId}/chunks/{chunkId}` | owner-checked 可信原文，no-store |

已有 GlobalExceptionHandler/ApiErrorResponse 处理安全错误；补充 multipart 大小/格式/缺少文件错误。低于检索阈值/空语料直接返回“当前学习资料中没有找到足够依据。”且不调用 Chat；若相似片段仍缺少问题所需事实，真实 Provider smoke 已返回不足依据且 citation count=0。该次 runtime Query2 Chat calls 仍为 `NOT OBSERVABLE`，不能据此推断实际 wire 次数。

## 5. Frontend

融入现有 LearningPlanDetailView 的学习资料 Tab，不新增 route/layout。上传、索引状态/片段数、重建、下载、提问、AI 回答与来源依据分栏、重新获取原文片段、不可用/失败/无依据状态均已实现。来源和回答纯文本渲染；请求版本防路由切换和原文请求乱序覆盖。前端验证最终结果见第 8 节。

## 6. Deterministic Verification

主控实际执行并读取 Surefire XML。以下为每类最近有效结果，不把重复重跑累计为新增测试：

| Test class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| EmbeddingVectorsTest | 3 | 0 | 0 | 0 |
| OpenAiCompatibleEmbeddingGatewayTest | 5 | 0 | 0 | 0 |
| SpringAiRagChatGatewayTest | 3 | 0 | 0 | 0 |
| RagServiceTest | 12 | 0 | 0 | 0 |
| RagConfigurationTest | 2 | 0 | 0 | 0 |
| RagControllerTest | 4 | 0 | 0 | 0 |
| LearningMaterialIngestionServiceTest | 2 | 0 | 0 | 0 |
| LearningMaterialMapperTest | 2 | 0 | 0 | 0 |
| MaterialDocumentParserTest | 9 | 0 | 0 | 0 |
| **总计** | **42** | **0** | **0** | **0** |

执行日志：`target/m7-deterministic-final.log` 为 9 类单次 41 项全绿；随后仅补充硬行数 SQL 断言并定向执行 `LearningMaterialMapperTest` 2 项，`target/m7-mapper-final.log` 为 23:16 全绿（原 mapper 1 项、新增 1 项）。42 是当前 9 类去重测试库存，不是单次 Maven 42 项输出。

覆盖 PDF/DOCX、小自制 fixture、页码/段落顺序、类型/格式/文本/ZIP/page/chunk 限制、真实 DTD 拒绝、CDATA/namespace、零/NaN/Infinity/畸形向量、维度、排名/Top-K/context/threshold、owner/key reconstruction、未知键/陈旧 Chunk、JSON 类型、Provider 错误、无依据不调用 Chat、Prompt 注入边界。真实持久化和回滚单独列于 MySQL Gate，不能用 mock 代替。

首轮核心 23 项曾 1 failure：Jackson 数字到字符串隐式转换；修正严格 JSON 类型后 23/23 通过，旧 `target/m7-core-test.log` 保留。后续曾有 35 项阶段 PASS；不将其与最后库存重复累计。

## 7. MySQL Integration

当前 agent process 的 `DB_PASSWORD`/`JWT_SECRET` 不可见；本轮未读取或打印值。MySQL Gate 采用用户 IDEA 实际执行与本地 Surefire XML 核验。

最新证据 `target/m7-mysql-confirmed-result.json` 为 **30 tests / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS / 46.750s**：`DatabaseSchemaIntegrationTests` 5、`LearningCoreIntegrationTests` 11、`LearningSupportIntegrationTests` 6、`RagMaterialIntegrationTests` 8。无外层 test transaction，使用真实 MyBatis/MySQL、测试专用 fake gateways、唯一测试账号及精确 cleanup；覆盖 BLOB/chunks、全部 owner 端点、删除/级联、重建新 ID、Provider 失败保留、真实第二个 INSERT 故障回滚、query no-write 与 embedding 期间删除。

最新通过结果逐类列示如下：

| Class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| RagMaterialIntegrationTests | 8 | 0 | 0 | 0 |
| DatabaseSchemaIntegrationTests | 5 | 0 | 0 | 0 |
| LearningSupportIntegrationTests | 6 | 0 | 0 | 0 |
| LearningCoreIntegrationTests | 11 | 0 | 0 | 0 |
| **总计** | **30** | **0** | **0** | **0** |

`DatabaseSchemaIntegrationTests` 新增 M7 BLOB/JSON/FK 列序/CASCADE/unique/Mapper 断言，并将新增 parent owner unique 加入已有完整集合断言，未弱化旧断言。第一次完整指定集结果仍保存在 `target/m7-mysql-first-result.json`，为 30 tests / 1 failure / 0 errors / 0 skipped / BUILD FAILURE：

| Class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| RagMaterialIntegrationTests | 8 | 0 | 0 | 0 |
| DatabaseSchemaIntegrationTests | 5 | 1 | 0 | 0 |
| LearningSupportIntegrationTests | 6 | 0 | 0 | 0 |
| LearningCoreIntegrationTests | 11 | 0 | 0 | 0 |

唯一失败为旧 M5B 方法内的全局 count expected 28 / actual 29。主控先机械确认 001～006 的 28 表 + 007 的 `learning_material_chunk` = 29，才将计数改为 29，方法改名 `applicationSchemaAndCurrentGlobalTableCountShouldExistInRealMySql`，注释区分历史/当前；没有修改数据库结构或删除/弱化断言。随后同一 30 项测试集在用户 IDEA 重跑全绿，当前 MySQL Gate 已关闭；不再重复执行 007 或测试。

## 8. Frontend Verification

- `target/m7-typecheck-closing.log` 与 `target/m7-build-closing.log` 的 `npm run typecheck`、`npm run build` 均 exit 0；build 保留主 chunk 大于 500 kB 的 P2 warning。
- 完整 browser fixture QA：`target/m7-browser-qa-final.json` 为 **11 组 PASS，pageErrors=[]**，并通过 390px 场景。覆盖 READY/PROCESSING、重建失败与重试、上传 loading、回答/来源、引用请求乱序、恶意原文纯文本、DOCX 无伪造页码、下载、no-evidence、error/unavailable。这些是 mocked HTTP 浏览器验证，不是 backend/Provider 端到端成功。
- 该 QA 中 fixture HTTP 计数：upload=1、reindex=2、query=3、source=2、file=1，均不是真实 Provider 调用。初步 6 组 `target/m7-browser-qa.json` 保留未覆盖。

## 9. Real Provider Gate

**PASS**。MySQL 前置 Gate 已关闭，外部 TechLead 已接受该 Real Provider Gate；其最终 GO 仍待确认。

- `target/m7-real-provider-proxy-result.json` 记录真实 Jina AI Embedding：`jina-embeddings-v5-text-small`，索引 1 个 Chunk。Query 1 返回 Java 21，可信引用为 material 146 / chunk 33 / `Paragraph 1` / `page=null`；Query 2 返回 insufficient-evidence，citation count=0，plan HTTP snapshot unchanged，fixture cleanup=true，automaticRetries=0。
- 生产 Chat 模型固定为 `deepseek-v4-flash`，无 fallback。运行时 Query2 Chat calls 与实际 provider wire counts 均为 `NOT OBSERVABLE`；仅记录到应用层尝试，不以本地 HTTP 数量推断 Provider wire 次数。确定性 no-evidence 分支另有 `RagServiceTest` 的 skips-Chat PASS 证据，不能与运行时观测混称。
- 首次 `target/m7-real-provider-result.json` 的 503 `AI_PROVIDER_UNAVAILABLE` / connect timeout 失败继续保留。`target/m7-connectivity-evidence.json` 记录默认 DIRECT 的 `HttpConnectTimeoutException`，以及 `-Djava.net.useSystemProxies=true` 下 HTTP 系统代理公共 HEAD=404；用户以该 IDEA VM 配置重启 backend 后取得上述 PASS。该过程未修改生产配置。

## 10. Regression

主控本轮实际 17 类 **129 tests / 0 failures / 0 errors / 0 skipped**，`target/m7-regression.log`，23:08 BUILD SUCCESS。其中 M6A/M6B 共享 no-tools 路径 31 项，M6C dedicated tool gateway 98 项；历史 OfflineFlow 4 项不计入本轮。RAG 仅复用现有无回调模型构造和有界响应工具函数，不调用 search/tool。

| Test class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| DeepSeekFlashConfigurationTest | 2 | 0 | 0 | 0 |
| SpringAiChatGatewayTest | 5 | 0 | 0 | 0 |
| JdParsePromptFactoryTest | 2 | 0 | 0 | 0 |
| JdParseServiceTest | 13 | 0 | 0 | 0 |
| LearningAiContextBuilderTest | 3 | 0 | 0 | 0 |
| LearningAiPromptFactoryTest | 2 | 0 | 0 | 0 |
| LearningAiServiceTest | 4 | 0 | 0 | 0 |
| JobDiscoveryConfigurationTest | 2 | 0 | 0 | 0 |
| ToolCallingCompatibilityTest | 1 | 0 | 0 | 0 |
| SpringAiToolCallingGatewayTest | 30 | 0 | 0 | 0 |
| TavilyJobSearchGatewayTest | 7 | 0 | 0 | 0 |
| JobSearchSessionTest | 23 | 0 | 0 | 0 |
| JobSearchToolTest | 5 | 0 | 0 | 0 |
| JobDiscoveryCandidateStoreTest | 5 | 0 | 0 | 0 |
| JobDiscoveryContextBuilderTest | 3 | 0 | 0 | 0 |
| JobDiscoveryServiceTest | 8 | 0 | 0 | 0 |
| JobDiscoveryControllerTest | 14 | 0 | 0 | 0 |

无 AI 配置完整启动证据为 2026-09-05 历史 `AiOpenAiWithoutKeyContextTest`；本轮 `RagConfigurationTest` 仅 2 项 context slice。两者均不被表述为本轮 full startup；传统业务全量启动/SQL 回归以第 7 节最新 MySQL Gate 为准。

## 11. Security

- owner 检查贯穿上传、检索、来源、下载、重建、删除；SQL 和最终重建双层检查，真实 MySQL owner 证据已包含在 30 项 PASS 集成套件中。
- BLOB 不写磁盘路径，filename 仅显示/下载，去目录和控制字符；ZIP 不解压到文件系统；下载 no-store/nosniff/attachment。
- 所有外部内容不可信，来源 metadata 仅由 Java 构建；AI 输出严格 JSON 类型，错误不返回 Provider 原文、栈、Prompt 或 Secret。RAG query 无业务写操作，无 global tools、动态模型或 Pro fallback。
- 全量 working changes 模式扫描无真实 key/JWT/private key/Authorization/credential path 命中，人工复核配置仅变量、测试仅 synthetic 值；结果见 `target/m7-security-audit.json`。规则扫描不等于形式化证明。

## 12. Current Project Counts

本轮从 migration 的 unique `CREATE TABLE`、exact `@RestController`（排除 `@RestControllerAdvice`）和 router view import 实际统计：**29 / 26 / 21**。29 含 28 既有业务表 + 1 个 `learning_material_chunk` 技术表；该源码统计与 MySQL 30 项 Schema/集成 Gate 的 PASS 证据分开记录。最终统计保存于 `target/m7-counts-final.json`；旧 `target/m7-counts.json` 的 AI=3 是 Provider Gate 关闭前的阶段记录。

正式完成 AI 功能为 **4**：M6A JD Structured Parse、M6B AI Learning Planning + Weekly Review、M6C AI Job Discovery / Tool Calling，以及 M7 RAG。M7 的计入依据为用户 required gates 已接受通过。

## 13. Docs

已同步 README.md、docs/ARCHITECTURE.md、docs/DEVELOPMENT_STATUS.md、docs/PROJECT_PLAN.md、docs/DATABASE.md，并维护本 Closing。各文档统一记录 M7 `GO — READY FOR CHECKPOINT`、未 commit/push、等待外部 TechLead 最终 GO；M6 历史证据、M6C checkpoint、首次 503 失败和旧 smoke 记录保留。

## 14. Git

本轮未 stage/commit/push，HEAD 未改变。`git diff --check` 与 `git diff --cached` 已执行、无差异错误且 staged empty；最终规则扫描结果与文件范围保存在 `target/m7-security-audit.json`。**NOT COMMITTED / NOT PUSHED**。

## 15. Findings

- P0：0。当前静态、集成和 Provider 证据未发现已确认 P0。
- P1：0。MySQL、Real Provider、frontend typecheck/build、完整 fixture QA 与回归门禁均已关闭；不把历史首次 503 结果冒充当前状态。
- 已修正：严格 JSON 字段类型、BLOB 单行映射、解析增量限制/DTD/namespace、删除锁顺序、最终来源存在性检查。
- P2/边界：course-scale 1000 Chunk/Plan；无 OCR、复杂 Word 版式/页码推算、向量模型自动迁移、语义逐句验证或跨实例异步索引。DOCX 当前验证 Word root namespace，未验证 unique body；受限 malformed body 可能被接受。数据库不可用时 FAILED 状态写入本身可能失败并保留 retryable UPLOADED。Smoke 账户 1976/1977 因无 delete endpoint 保留，相关计划已清理。重复 empty ZIP 最终被拒绝，未确认为 bug。阈值是默认策略，不能把来源可追溯等同于答案语义必然正确；既有构建 chunk 与 Mockito 动态 agent 警告及 bundle size warning 按实际日志保留。

## 16. Final Decision

**GO — M7 READY FOR CHECKPOINT**。required gates 已完成，Real Provider Gate 已获外部 TechLead 接受；等待其最终 GO。当前仍 **NOT COMMITTED / NOT PUSHED**，本报告不执行 checkpoint、commit 或 push。
