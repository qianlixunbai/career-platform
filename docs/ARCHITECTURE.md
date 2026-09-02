# 系统架构

## 文档状态

本文描述当前已经确定的技术基线和目标架构。除“当前仓库结构”外，其余模块、包和 AI 能力均是规划，不代表已经创建或实现。

原始开工方案中的 JPA / Repository 是历史待定方案。当前仓库已经正式采用 **MyBatis-Plus + Mapper**，所有最新维护文档均以此为准。原始开工方案只保留项目最初的完整范围和初始设计基线，不覆盖真实代码和本文持续维护的当前技术基线。

## 当前技术基线

- Java 21
- Spring Boot 3.5.14
- MyBatis-Plus 3.5.17
- MySQL
- Spring Web
- Bean Validation
- Maven
- Vue 3（规划）
- Spring AI（规划）

## 当前仓库结构

当前实际存在的主要代码只有：

```text
com.careerplatform
├─ CareerPlatformApplication
└─ user
   ├─ entity
   │  └─ AppUser
   └─ enums
      └─ UserStatus
```

当前没有 Mapper、Service、Controller、前端工程或 AI 模块。

## 目标总体架构

```text
Vue 3
  ↓ REST API / SSE
Controller
  ↓
Service
  ↓
Mapper
  ↓
MyBatis-Plus
  ↓
MySQL
```

- REST API 用于常规业务交互。
- SSE 只在后续 AI 流式响应等确有需要的场景使用。
- Controller 负责协议适配、参数接收和响应。
- Service 负责权限之外的核心业务校验、事务和状态流转。
- Mapper 负责数据访问，MyBatis-Plus 提供基础映射和查询能力。
- MySQL 保存正式业务数据和历史记录。

## 业务模块边界

```text
职业探索与目标管理 ─┐
学习提升管理 ───────┼─→ 共享基础档案
简历管理 ───────────┤
求职过程管理 ───────┘
```

四个核心业务模块共享个人、教育、技能、项目、实习和证书/获奖等事实。共享基础档案不是第五个核心业务模块，也不在不同模块中重复保存。

历史信息与当前事实分离：共享档案可以更新，但已经定稿的简历版本、实际投递绑定的简历、岗位快照和阶段历史不得被后续变更反向覆盖。

## 规划包结构

```text
com.careerplatform
├── common
├── user
├── career
├── learning
├── resume
├── application
└── ai
```

各业务模块可按需要包含 `controller`、`dto`、`service`、`mapper`、`entity` 等子包。具体目录会随着详细设计调整，目前除 `user/entity` 和 `user/enums` 外并未全部创建。

## AI 侧架构

```text
业务数据 / 用户输入
  ↓
AI Application
  ↓
Spring AI
  ↓
LLM / RAG / Agent
```

AI Application 负责组织提示词、上下文、结构化输出和工具调用，但不取代业务 Service。模型可以读取经授权的业务数据、抽取信息和生成建议；确定性的校验、状态流转和持久化仍由 Java 业务代码负责。

## AI 写操作边界

```text
AI 分析 / 建议
  ↓
展示候选结果
  ↓
用户检查、修改和确认
  ↓
Service 校验与事务处理
  ↓
Mapper
  ↓
Database
```

AI 或 Agent 不允许绕过 Service 直接修改正式业务数据。共享基础档案、学习计划、简历、岗位要求和求职状态等正式信息都遵守这一边界。

## 关键业务一致性边界

### 简历版本

- `DRAFT ResumeVersion` 可以编辑。
- `FINALIZED ResumeVersion` 锁定，并保留当时内容。
- `Application` 只能绑定已定稿版本。

### 求职阶段

- `Application.currentStage` 提供当前粗粒度状态。
- `ApplicationStageHistory` 追加保存阶段变化历史。
- `Assessment` 和 `Interview` 的局部结果不自动推动全局阶段，阶段变更由明确的业务操作完成。

### 删除与归档

- 有投递历史的 `Job` 只能归档或隐藏。
- `Application` 及其历史记录原则上不物理删除。

### AI 可追溯性

- AI 输出应区分来源事实与模型建议。
- RAG 文档引用应尽可能携带来源、页码或位置和原文片段。
- 找不到可靠证据时不得生成伪造引用。

## 降级原则

系统必须在 AI 服务不可用时保留核心业务能力：JD 要求可以手工录入，学习计划和复盘可以由用户填写，简历可以人工编辑，求职流程可以正常记录。P2 的 RAG、Agent 和评估能力不得成为 P0 业务运行的前置条件。
