# 项目规划

## 文档定位与基线

### 当前推进：Milestone 6C

本轮实现 AI Job Discovery，作为 AI 功能 #3 的验收目标：职业目标与技能 → Spring AI 调用只读 Tavily Search Tool → 原始搜索结果与 opaque resultKey → AI 排序建议 → Java 来源重建 → 用户检查 → 显式确认 → 现有 Job Service。该受限 Tool Calling 用例提前进入 M6C；通用 Agent、RAG、搜索历史和 crawler 仍不在本轮范围。

M6C 不增加业务表或 migration；独立岗位发现页面区分临时候选与正式岗位。没有自动创建 Company，jobType 由用户确认，snippet 不作为完整 rawJd。内存候选 TTL 15 分钟且有容量上限；确认以提交后消费保证同一候选一次性保存。

模型固定 DeepSeek Flash，无 Pro、动态模型、fallback 或自动重试。一次流程通常需要两次模型 HTTP 往返、最多三次，搜索最多两次；离线测试使用 fake；独立 Smoke #4 已通过，本轮 Closing 不追加真实 Provider 调用。M6A/M6B 的 no-tools contract 保持独立。

M6A/M6B 保留既有 checkpoint；M6C 已通过 [Final Closing](M6C_CLOSING_VERIFICATION.md)，正式完成 AI 功能达到 3 个，满足课程最低数量。M6C 当前 READY FOR CHECKPOINT / NOT COMMITTED / NOT PUSHED，等待外部 Tech Lead 审查。

### 既有规划基线

本文件是《大学生职业发展与求职管理平台的设计与实现》的当前维护版项目规划。业务设计主要继承[原始开工方案](reference/大学生职业发展与求职管理平台_项目开工方案_v1.md)，并结合后续冻结的业务规则整理。

基线发生冲突时按以下原则处理：

1. 当前仓库代码、配置和真实数据库结构决定“已经实现什么”。
2. 后续已冻结的业务规则决定当前业务设计。
3. 原始开工方案提供项目最初的范围、演示链路和课程规模基线。

原始开工方案用于保留项目最初的完整范围和初始设计基线，不作为当前实时维护文档。当前实际状态以真实代码、[开发状态](DEVELOPMENT_STATUS.md)、[系统架构](ARCHITECTURE.md)和[数据库说明](DATABASE.md)等持续维护内容为准。特别是原始方案中的 JPA / Repository 等待定或旧技术描述，已被当前的 MyBatis-Plus + Mapper 基线取代。

本文件描述的是规划。除非在[开发状态](DEVELOPMENT_STATUS.md)中列入“已完成”，否则不应理解为已实现。

## 项目背景

大学生在职业准备和求职过程中，常从企业公众号、招聘网站等多个渠道搜集岗位，再通过备忘录、文档和表格分别维护技能、学习计划、简历版本与投递进度。这种分散方式难以建立连续关联，也不便追溯“当时使用了哪版简历、经历了哪些测评和面试、最终为何结束”。

本项目计划将职业目标、学习执行、简历版本、岗位信息和实际求职历史集中到一个平台中，形成可持续复盘的职业发展闭环。系统定位是“完整传统业务系统 + AI 增强能力”，不是围绕聊天机器人搭建的系统。

## 目标用户

第一版只面向处于职业准备和实际求职阶段的大三、大四学生，不强行引入企业 HR 或就业指导教师等额外角色。

- 大三学生主要使用职业目标、技能档案、学习计划、简历准备和岗位收集能力。
- 大四学生在此基础上重点管理投递、测评、多轮面试、Offer 和最终复盘。

## 核心业务闭环

```text
确定求职方向
→ 学习提升
→ 准备简历
→ 收集并分析岗位
→ 投递
→ 测评 / 笔试
→ 多轮面试
→ Offer / 公司拒绝 / 主动结束
→ 最终复盘
→ 反馈到职业方向、学习和简历
```

该流程允许反馈和回退：岗位要求可以影响职业目标与学习计划，面试和最终复盘也可以产生新的学习或简历调整建议。

## 四个核心业务模块

### 1. 职业探索与目标管理

规划维护职业目标、公司、岗位、原始 JD、结构化岗位要求和岗位备注，用于回答“准备找什么工作、市场需要什么、个人还欠缺什么”。

关键规则：

- `Company` 独立存在，`Job` 引用 `Company`。
- 岗位来源第一版作为 `Job` 属性保存。
- 收藏或记录 `Job` 不等于产生真实投递。
- 已有 `Application` 历史的岗位不得物理删除，只能归档或隐藏。

### 2. 学习提升管理

规划将职业目标和能力差距转换为周学习计划、任务和实际执行记录，并通过周复盘反馈到下一阶段。

关键关系和规则：

- `LearningPlan` 1 → n `LearningTask`。
- `LearningTask` 1 → n `StudyRecord`。
- `LearningPlan` 1 → 0..1 `WeeklyReview`。
- `LearningNote` 与 `LearningMaterial` 分离，分别承载用户笔记和 PDF、Word 等资料。

### 3. 简历管理

规划通过简历、版本和内容项维护面向不同岗位的求职材料，同时保留真实投递版本。

```text
Resume
└─ ResumeVersion 0..n
   └─ ResumeContentItem 0..n
```

关键规则：

- `DRAFT` 简历版本可编辑，`FINALIZED` 版本锁定。
- 历史定稿版本不随共享基础档案的后续变化而改变。
- 实际投递必须绑定 `FINALIZED ResumeVersion`。
- AI 可以提出优化建议，但不得直接覆盖正式简历版本。

### 4. 求职过程管理

规划记录一次实际投递从创建到结束的完整过程。

```text
Application
├─ ApplicationStageHistory 0..n
├─ Assessment 0..n
├─ Interview 0..n
├─ Offer 0..1
└─ FinalReview 0..1
```

关键规则：

- 真正投递时才创建 `Application`。
- 同一 `Job` 可以有多次历史 `Application`，但同一时间只能有一条进行中的 `Application`。
- `Application` 保存当前粗粒度阶段，同时保存 `ApplicationStageHistory`。
- 粗粒度阶段为：已投递、测评中、面试中、Offer 处理中、已结束，允许按实际流程跳过部分阶段。
- `Assessment` 和 `Interview` 独立，其局部结果不自动修改 `Application` 的全局阶段。
- 一个 `Application` 最多有一个 `Offer` 和一个 `FinalReview`。
- `FinalReview` 只能在 `Application` 真正结束后产生，可以稍后补写，不强制结束时立即填写。
- `Application` 历史数据原则上不物理删除。

## 共享基础档案

共享基础档案是四个业务模块共同依赖的事实层，不作为第五个核心业务模块。规划包括：

- 个人基本信息
- 教育经历
- 技能
- 项目经历
- 实习经历
- 证书 / 获奖经历

`Skill` 保存统一技能定义，`UserSkill` 保存用户与技能的关系及掌握情况，两者分离。职业、学习、简历、求职复盘和 AI 都可以读取共享事实或提出更新建议，但正式修改必须经过用户明确操作或确认。

## AI 设计原则

1. 删除 AI 后，核心业务闭环仍然完整可用。
2. 确定性业务由 Java 代码负责，概率性分析和建议交给 AI。
3. 模型不负责正式业务状态流转，Java Service 是正式操作入口。
4. AI 涉及写操作时统一遵守：

```text
AI 分析 / 建议
→ 用户确认
→ Java Service 校验
→ 数据库写入
```

5. AI 分析尽量展示依据；文档引用应能定位来源、页码或位置和原文片段。
6. 界面和数据结构必须明确区分“来源事实”与“AI 建议”，没有可靠依据时不得伪造引用。

## 版本路线

### P0：核心可交付版

目标是完成不依赖高级 AI 的传统业务闭环，包括共享档案、职业目标、岗位、学习、简历版本和完整求职历史。P0 是业务闭环里程碑，不等于课程最终验收版本：其 1 个 AI 功能尚未达到课程至少 3 个 AI 功能的要求。

P0 唯一必做 AI 功能为 **AI JD 结构化解析**：

```text
原始 JD
→ AI 提取候选岗位要求
→ 用户检查 / 修改
→ 用户确认
→ Java Service 保存
```

AI API 不可用时，用户仍可手工维护岗位要求。

### P1：智能增强版

规划增加：

1. AI 学习规划与周复盘（M6B 已冻结，`FROZEN / COMMITTED / PUSHED`；功能 checkpoint `805a3801af76e4e88434e52e154d2069ad3c4d1b` 已 push 到 `origin/main`）
2. AI 面试 / 求职复盘
3. 简历 + JD 匹配分析

与 P0 的 JD 解析合计至少四个规划 AI 功能；当前已完成 JD Structured Parse、AI Learning Planning + Weekly Review、AI Job Discovery / Tool Calling，共 3 个正式 AI 功能，已达到课程最低要求。

### P2：技术深化版

按以下优先级推进：

1. RAG 学习资料问答 + 来源 / 原文追溯：P2 内的固定必做目标。
2. Agent + Tool Calling：第二优先级。
3. AI Evaluation / A-B 实验：有余力再做。

“P2 必做”表示 RAG 不是 P2 内的可选项；它不表示必须先完成 RAG 才能验收 P0 / P1 里程碑，也不改变 P0 → P1 → P2 的实施顺序。

## 预计数据表

初始设计基线规划约 28 张核心业务表；P2 可能增加技术表。当前真实状态为 28 张业务表，已经落地 `app_user`、共享基础档案、职业探索、Learning、Resume 和 Application Management。M6A candidate 是 ephemeral suggestion，确认后写既有 `job_requirement`，因此没有新增 AI 技术表或 migration。

| 模块 | 候选表 |
|---|---|
| 共享基础档案 | `app_user`、`user_profile`、`education_experience`、`skill`、`user_skill`、`project_experience`、`internship_experience`、`certificate_award` |
| 职业探索 | `career_goal`、`company`、`job`、`job_requirement`、`job_note` |
| 学习提升 | `learning_plan`、`learning_task`、`study_record`、`weekly_review`、`learning_note`、`learning_material` |
| 简历管理 | `resume`、`resume_version`、`resume_content_item` |
| 求职过程 | `application`、`application_stage_history`、`assessment`、`interview`、`offer`、`final_review` |
| P2 技术数据（可能增加） | `document_chunk` 等检索相关结构 |

除 P2 技术数据外，上述核心业务表均已建表；P2 候选表仍只表示规划方向。

## 预计 Controller

课程最终要求至少 18 个 Controller。初始设计基线按业务职责规划的 Controller 数量超过 18 个，覆盖身份与基础档案、职业探索、学习、简历、求职流程和 AI 能力。

Controller 不机械地与表一一对应，最终会按用例和 API 职责合理合并或拆分。当前仓库中实际有 **25** 个精确 `@RestController`（不计 Advice），M6A 新增 `JdAiController`，M6B 新增 `LearningAiController`，M6C 新增 `JobDiscoveryController`；课程最低数量指标已达到。

## 预计前端页面

课程最终要求至少 15 个前端页面。初始设计基线预计约 18～20 个主要业务页面，覆盖登录注册、首页、共享档案、技能、职业目标、公司岗位、学习计划与复盘、简历版本、求职申请详情、测评面试、Offer 和最终复盘；P2 再增加 RAG 问答界面。

部分能力可以用 Tab、弹窗或详情区域组合，但最终课程统计方式需要与老师要求保持一致。当前已完成 **21** 个 routed frontend pages，包括 Application 列表、聚合详情页和 M6C Job Discovery；后续 AI 页面按业务需要增加，不再为了数量硬扩张。

## 核心演示链路

答辩优先展示一条连贯业务链，而不是孤立 CRUD：

```text
登录并维护共享档案
→ 确定职业目标
→ 录入公司和岗位
→ AI 解析 JD，用户确认
→ 制定并执行学习计划
→ 创建、定稿简历版本
→ 创建 Application 并绑定定稿版本
→ 记录测评与多轮面试
→ 处理 Offer 或其他终局
→ 结束 Application
→ 完成最终复盘并反馈到下一轮准备
```

高级 AI 功能没有完成时，这条链路仍应通过人工操作成立。

## 风险与降级方案

| 风险 | 降级或控制方案 |
|---|---|
| AI API 不可用、限流或成本超预期 | 保留人工录入和编辑能力，AI 故障不阻塞传统业务 |
| AI 结果错误或幻觉 | 仅保存候选结果；用户确认后再由 Service 校验 |
| AI 分析缺乏依据 | 展示业务数据或原文来源；无法确认时明确标注不确定性 |
| RAG 时间不足 | P2 不阻塞 P0 / P1；优先保证核心闭环 |
| Agent 复杂度过高 | 保持为 P2 第二优先级，写操作继续走确认和 Service 边界 |
| 业务范围过大 | 严格按 P0 → P1 → P2 推进，先完成可演示闭环 |
| 表、Controller 或页面设计机械扩张 | 以真实业务职责为依据，同时满足课程最低规模 |
| 历史数据被当前事实覆盖 | 定稿简历、投递快照和阶段历史使用不可变或追加式设计 |
| 凭证泄露 | 所有密码和密钥使用环境变量或本地配置，不提交 Git |

## 课程硬性约束

| 指标 | 课程最终最低要求 | 当前真实完成 | 当前规划 |
|---|---:|---:|---:|
| 数据表 | 18 | 28 | 28 张核心业务表，P2 可能增加技术表 |
| Controller | 18 | 25 | 超过 18 个，按业务职责划分 |
| 前端页面 | 15 | 21 | 21 个 routed view pages，不计 layout/redirect |
| AI 功能 | 3 | 3 | JD Structured Parse、AI Learning Planning + Weekly Review、AI Job Discovery / Tool Calling |

数据表、精确 @RestController 和 routed view pages 当前分别为 28、25、21，正式完成 AI 功能为 3 个，均满足课程最低数量要求；RAG 仍未实现。当前状态以[开发状态](DEVELOPMENT_STATUS.md)为准。
