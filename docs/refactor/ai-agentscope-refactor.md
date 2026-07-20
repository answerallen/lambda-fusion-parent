# lambda-fusion-ai 基于 AgentScope 2.0 原生重建方案

> 状态：v3 / 定稿方向　　分支：`2026.2`　　日期：2026-07-19
> 范围：`lambda-fusion-ai` 智能体编排与运行时**推倒重来**，基于 AgentScope 2.0 原生重建
> 关联：父 POM 已引入 `io.agentscope:agentscope-bom:2.0.0`（`_lambda-cloud-parent/pom.xml` L27、L146-148）

---

## 0. 方案演进

| 版本 | 方向 | 问题 |
|---|---|---|
| v1（2026-07-18） | 绞杀者模式：引入 AgentScope 作第二运行时，`engine` 开关切换，旧引擎并存 | 双引擎复杂；保签名与去 langchain4j 互斥 |
| v2（2026-07-19） | 全量替换：单一 AgentScope 引擎，重设计 `WorkflowExecutionService`，取消兼容 spike | 仍保留图层（`GraphDefinition`/9 节点/拓扑编译器）作"产品资产"，把图执行范式硬套到 agent 调用范式上 |
| **v3（本方案）** | **推倒重来：砍掉整个图层，按 AgentScope 原生重建** | 开发阶段、无在产下游，不必硬保留/适配 |
| **v3.1（2026-07-18）** | **RAG 路径 2 + middleware 修正**：核实 AgentScope 2.0 `io.agentscope.core.rag` 编排层（`Knowledge`/`KnowledgeRetrievalTools`/`Document`/`RetrieveConfig`/`RAGMode`）已标 `@Deprecated(forRemoval=true)`，官方指引"integrate retrieval at the application layer"且推荐 middleware。砍 forRemoval 编排层（删 `FederatedKnowledge`/`KnowledgeRetrievalTools`），保留未废弃底层（`SimpleKnowledge`/`PgVectorStore`/`Reader`/`EmbeddingModel`）；agentic `KnowledgeRetrievalTool` @Tool + static `RagMiddleware`（`onReasoning` 检索注入）hybrid | v3 假设 AgentScope RAG 抽象稳定，实际 2.0.0 已标 forRemoval（过渡版本）；Java `MiddlewareBase` 无 `list_tools`，agentic 标准即 @Tool |

**v3 的两个决定性判断**（已核实）：

1. **AgentScope 2.0 不提供"前端画工作流 → 存库 → 运行时跑"**。其编排模型是：Java 代码 `HarnessAgent.builder()` + Markdown 声明子 agent + Plan Mode 自规划 + middleware，持久化只覆盖会话/记忆状态（`AgentStateStore`）。Studio/AG-UI 是事件流渲染协议（前端实时画执行过程），不是端用户拖拽画图工具；v1 基于 Spring AI Alibaba StateGraph 的“自定义工作流”在 v2 已弃用，改用 subagent + Plan Mode + middleware（见 §2）。
2. **保住可视化/存库工作流就得自写 `graphJson → AgentScope` 拓扑编译器**，而 9 节点类型/条件边/循环/并行/子图本是 AgentScope 原生 agent/subagent/middleware 概念--硬编译就是"图执行"范式套"agent 调用"范式的硬适配，与 langgraph4j 同病。

结论：**不保留图层、不复刻图执行语义、不为适配而保留任何中间抽象**。`apps`（`ai_robot`）域升格为 agent 模板载体，运行时按模板构造 AgentScope agent。

---

## 1. 背景与目标

### 1.1 为什么推倒重来

**根因：图编排是弱模型时代的脚手架，模型能力升级后已成多余。** 2023–2024 初，前沿模型多步推理与工具调用不够稳，框架用预定义图（langgraph4j、v1 AgentScope 的 StateGraph“自定义工作流”、CrewAI flows）把流程钉死，补偿模型不可靠。2024–2026 前沿模型（Claude 3.5→4→5、GPT-4o→5 等）在长程规划、ReAct 工具循环、委派时机、条件/循环推理上已足够稳，图脚手架从“必要”变“多余开销”，范式转向模型自驱动：模型自己 Plan + `agent_spawn` subagent + 用工具 + 运行时自适应（Claude Code、OpenAI Agents SDK、Manus、AgentScope v2 Harness 同此路线）。AgentScope v2 自述从“构建智能体的工具箱”转向“面向生产环境运行智能体”，Plan Mode“让意图与动作解耦”，并弃用 v1 的 StateGraph 图 DSL——框架自身已顺此曲线砍图。本方案砍图层，正是顺着同一方向。

当前 `lambda-fusion-ai` 编排以 langgraph4j（图引擎）+ langchain4j（运行时）为底座，`agent` 子树约 **5983 行**。这套实现：

- 把前端画布的节点/边直接编译成 langgraph4j `StateGraph`，路由/条件/循环/并行/子图全手搓，与 langgraph4j 内部 API 深度耦合；
- 多智能体用 `SUPERVISOR_AGENT`/`PARALLEL`/`SUBGRAPH`/`AGENT_AGGREGATOR` 图节点**模拟**，非原生语义；
- 可观测性手搓（`_executionTrace`），会话靠 `MemorySaver` 内存 checkpoint，重启即丢；
- langgraph4j（编排）与 langchain4j（运行时）双框架张力，抽象层叠。

模块仍在开发、无在产下游消费方。底层换 AgentScope 2.0 时，不值得为保留旧图执行抽象付翻译税。

### 1.2 目标

- 以 AgentScope 2.0 为**唯一**智能体运行时，按其原生模型（`HarnessAgent` + subagent + Plan Mode + middleware + 分布式会话）重建。
- 移除 langgraph4j；编排/智能体路径移除 langchain4j。
- **砍掉整个图层**：`AgentGraph`/9 节点/`GraphDefinition`/`AgentNode` SPI/`AgentState`/拓扑编译器/`examples/workflows/*.json` 图模板/前端画布集成。
- `apps`（`ai_robot`）升格为 **agent 模板载体**：DB 存储、运行时可编辑、多租户隔离；运行时按模板 `HarnessAgent.builder()` 构造。
- 保留真正有价值且与编排解耦的能力：DB 驱动的模型/工具/MCP 管理与密钥加密、Resilience4j、token 记账/结算、知识库/RAG（`SimpleKnowledge` + middleware hybrid，多 KB）、聊天集成。

### 1.3 非目标

- 不接入第三方 RAG 平台（百炼/Dify/RAGFlow）本期，用 `-rag-simple` 自托管（D3 路径 2：保留未废弃 `SimpleKnowledge`/`PgVectorStore`，砍 forRemoval 编排层，RAG 经 `RagMiddleware` + `KnowledgeRetrievalTool` @Tool hybrid）。
- 不替换 `lambda-fusion-*` 其它业务模块。
- 前端可视化编辑器：本期**放弃**自研画布；是否引入 AgentScope Studio/AG-UI 做**运行时观测**（非编排）留 Phase 4。

---

## 2. AgentScope 2.0 编排模型（已核实 README + release notes）

> 来源：`github.com/agentscope-ai/agentscope-java` 官方 README + release notes。`java.version=17`（本仓库 21，前向兼容）。Apache-2.0。

> 设计传承：Harness 层源自 v1.1.0 引入的 `agentscope-harness`，AgentScope 官方在 [v1.1.0-RC1 release notes](https://github.com/agentscope-ai/agentscope-java/releases/tag/v1.1.0-RC1) 中将其定位为“Build your own OpenClaw-style agent”（workspace 内持续演进 + 企业级分布式扩展 + 隔离执行）。v2 在此基础上全面演进（Plan Mode/middleware/权限/分布式会话）并弃用 v1 的 StateGraph 图 DSL。**故 v2 模型驱动理念的源头是 OpenClaw**--本方案采用 v2 Harness，即间接继承 OpenClaw-style（OpenClaw → AgentScope v1.1.0 Harness → v2 → v3）。

AgentScope 2.0 是"面向生产环境运行智能体"的平台，**不是可视化工作流编排器**。其编排模型：

- **智能体在代码里构造**：`HarnessAgent.builder().name().sysPrompt().model().workspace().build()`，model 可字符串解析（`openai:gpt-4.1`/`dashscope:qwen-plus`）或传 `ChatModel` 对象。
- **执行核心**：`agent.call(msg, ctx)`（同步）/ `agent.streamEvents(msg, ctx)`（流式 `Flux<Event>`，28 种类型化事件）。
- **多智能体**：**Markdown 声明子 agent 规格** + `agent_spawn`/`agent_send` 按需派生（同步/后台委派）。不是画图。
- **复杂长任务**：**Plan Mode**--agent 只读规划、计划文件持久化、驱动执行（运行时自决策，非预画图）。
- **条件/循环/并行**：**middleware**（`onAgent`/`onReasoning`/`onActing`/`onModelCall`/`onSystemPrompt` 五阶段 AOP hook）+ subagent 并发。不是图节点/边。
- **权限/HITL**：工具三态决策（允许/审批/拒绝）+ `HintBlockEvent` 精确恢复，一等公民。
- **分布式会话/记忆**：`AgentStateStore` 后端：`InMemory`/`JsonFile`（HarnessAgent 默认，单机）/`Redis`（`-extensions-redis`）/`PostgresAgentStateStore`（`-extensions-postgresql`，**PG 一等后端**，spike 已核实）/`MysqlAgentStateStore`（`-extensions-mysql`，自动识别 MySQL/SQLite/H2 方言）。`RuntimeContext`（session/user/agent/org）做多租户状态隔离。
- **Workspace/沙箱**：本地/Docker/K8s/AgentRun 云沙箱。
- **协议/UI**：A2A、AG-UI（事件流渲染协议，给前端实时画执行过程，**非端用户编排工具**）；`agentscope-extensions-studio` 是 **dev-time 调试/可观测工具**（消息推送 + 链路追踪 trace 树 + HITL via `StudioUserAgent`，生产关闭、`agentscope.studio.enabled` 开关），非端用户编排工具。**v1 曾有“自定义工作流”基于 Spring AI Alibaba StateGraph（代码级图 DSL，与 langgraph4j 同范式），v2 已弃用该页改用 subagent + Plan Mode + middleware**——AgentScope 自身在 v2 已放弃图 DSL 路线。

> 关键：AgentScope 的"编排"= 代码定义 agent + Markdown subagent + 自规划 + middleware，**没有"用户画图存库运行"这一层**。本方案据此原生重建，不自建图层。

---

## 3. 基础能力集（模块定位）

`lambda-fusion-ai` 作为下游可依赖的 AI 基础组件，提供以下 DB 驱动、多租户、可管理、喂给 AgentScope 运行时的基础能力。每项含现状耦合、AgentScope 化方向、扩展点：

| # | 能力 | 现状（耦合） | AgentScope 化 | 扩展点 |
|---|---|---|---|---|
| 1 | Agent 运行 | `AgentGraph`+9 节点（langgraph4j） | `AgentRuntimeService`(call/run/resume) on `HarnessAgent`；app 模板 → agent | `AgentTemplateFactory` |
| 2 | 模型管理 | `ChatModelFactory` 产出 langchain4j `ChatModel` | 产出 `Credential`+`ChatModelBase`（5 provider 扩展）；`listModels()` 拉模型列表 | `ModelProvider` SPI |
| 3 | 工具管理 | `AgentToolProvider` 扫 langchain4j `@Tool` | AgentScope `Toolkit`（自有 `@Tool`）+ **Tool Group** 动态激活 | `ToolRegistry` |
| 4 | MCP 管理 | `McpClientManager` 用 langchain4j-mcp | AgentScope core MCP（内建），注入 `Toolkit` | transport 适配 |
| 5 | 知识库 RAG | 自研 pgvector 全栈 + langchain4j 解析 | **路径 2 半迁移**：保留未废弃 `SimpleKnowledge`/`PgVectorStore`/`Reader`/`EmbeddingModel`，砍 forRemoval 编排层；agentic `KnowledgeRetrievalTool` @Tool + static `RagMiddleware`（`onReasoning` 检索注入）hybrid（对齐官方 middleware 推荐）；外部型（百炼/Dify/RAGFlow，retrieval-only）Phase 4；app 多 KB 绑定 | `KnowledgeFactory` |
| 6 | 提示词管理 | `PromptTemplateServiceImpl` 用 langchain4j `PromptTemplate` 渲染 | 渲染换中性引擎，喂 `sysPrompt`+`onSystemPrompt` middleware | `PromptResolver` |
| 7 | 会话/聊天 | `ChatMessageServiceImpl` 两路（RAG+workflow）langchain4j 回调 | 统一 `run(→Flux<Event>)`→SSE；RAG 作 agent 工具，二路合一 | - |
| 8 | 记账/结算 | `CostCalculator`（纯）+ usage 取自 langchain4j `tokenUsage` | `CostCalculator` 原样保留；`CostCaptureMiddleware`(onModelCall) 取事件 usage | middleware SPI |
| 9 | 容错 | `LlmResilienceConfig` 装饰 langchain4j model | Resilience4j 配置保留，接入点改 `onModelCall`/`onActing` middleware | middleware SPI |
| 10 | 多租户隔离 | `tenant_id` 字段级 | `tenant_id` 映射 `RuntimeContext`，会话/记忆/工作区按租户隔离，叠加字段级 | - |
| 11 | 可观测 | 手搓 `_executionTrace` | 事件流聚合 + OTel + Studio(dev-time)，经 middleware 串 trace | middleware SPI |

**两个跨能力结论：**

1. **langchain4j 彻底清零（6 处全替换）**：`ChatModelFactory`、`AgentToolProvider`、`McpClientManager`、`ChatMessageServiceImpl`、`PromptTemplateServiceImpl`（新发现）、`DocumentProcessor`（RAG 解析）。Phase 3 门禁 `grep dev.langchain4j` 在 `src/main` = **0**（无例外--RAG 转 AgentScope readers 后文档解析也清）。
2. **middleware 是横切能力单一集成点**：`onModelCall` 装熔断/限流/重试 + 记账；`onActing` 装工具级容错；`onAgent`/`onReasoning` 串 trace；`onSystemPrompt` 做 prompt 变换。Resilience4j、CostCalculator、OTel、prompt 渲染全收敛到 middleware，不再散落各 Node/Factory/ServiceImpl。

---
## 4. 现状拆除清单（推倒重来）

以下全部**删除**，不保留、不作迁移桥梁：

| 类别 | 文件/概念 | 行数 |
|---|---|---|
| 图引擎 | `agent/AgentGraph.java` | 829 |
| 节点 SPI/状态 | `agent/AgentNode.java`、`agent/AgentState.java` | 52+183 |
| 图 Schema | `agent/model/GraphDefinition`/`NodeDefinition`/`EdgeDefinition` | - |
| 9 节点实现 | `agent/node/*`（`ReactAgentNode`/`SupervisorAgentNode`/`ParallelNode`/`SubgraphNode`/`AgentAggregatorNode`/`LlmProcessingNode`/`ToolExecutingNode`/`ConditionalNode`/`LoopNode`） | - |
| 条件评估器 | `agent/evaluator/*`（`ConditionEvaluator` SPI + 5 实现 spel/js/confidence/retrieval_relevance/tool_result） | - |
| 图工厂 | `agent/factory/AgentGraphFactory`/`AgentGraphBuildOptions`/`AgentGraphProvider` | - |
| 工作流域 | `workflow/model/entity/WorkflowEntity`（`graphJson`）/`WorkflowTemplateEntity`/`WorkflowTemplateVersionEntity`；`WorkflowExecutionService` + Impl（图执行语义）；`WorkflowController`/`WorkflowTemplateController` | 1067(Impl) |
| 聊天侧 workflow 耦合 | `ChatMessageServiceImpl.doSendMessageStream` 的 `workflowId` 分支 + `executeWorkflowStream`；`ChatSessionServiceImpl.createSession` 复制 `workflowId` 到 session；`ChatSessionEntity.workflowId` 字段 | - |
| 图模板 | `src/main/resources/examples/workflows/*.json` | - |
| langgraph4j 依赖 | `langgraph4j-core`/`-langchain4j`/`-agent-executor` | - |
| langchain4j 依赖 | `langchain4j-spring-boot-starter`/`-ollama`/`-open-ai`/`-mcp`/`-document-parser-pdfbox`/`-poi`（全删） | - |
| 提示词渲染 | `PromptTemplateServiceImpl` 的 langchain4j `PromptTemplate`/`Prompt`（换中性引擎） | - |
| RAG 自研引擎 | `knowledge/VectorRepository`/`PgVectorTypeHandler`/`VectorDimensionProcessor`/`DocumentProcessor`（langchain4j 解析） | - |
| 装配 | `AiConfigure.workflowCheckpointSaver`（`MemorySaver` Bean） | - |
| 测试 | `AgentGraphTest`/`AgentGraphMultiAgentFlowTest`/`AgentGraphVerificationTest`/`AgentStateTest`/各 Node 测试/各 Evaluator 测试/`AgentGraphFactoryTest`/`RagAgentGraphProviderTest`/`WorkflowExecutionServiceImplTest`（约 19 个） | - |

`agent` 子树 5983 行预计砍掉 **70%+**。

---

## 5. 新架构

### 5.1 分层

```
┌─────────────────────────────────────────────────────────────┐
│  产品接入层                                                   │
│  ChatSessionController / ChatMessageController              │
│  AppsController (AI 应用/机器人 = agent 模板管理, /v1/ai/apps)│
│  KnowledgeBaseController / LlmController / McpController    │
├─────────────────────────────────────────────────────────────┤
│  Agent 运行服务（新，取代 WorkflowExecutionService）            │
│  AgentRuntimeService:                                       │
│    run(session, input) → Flux<Event>  (聊天主路,session)    │
│    call(appId, input) → AgentRunResult (可选 one-shot)      │
│    resume(sessionId, ...) (HITL 恢复)                       │
│  ChatMessageServiceImpl 订阅 Flux<Event> → SseEmitter       │
├─────────────────────────────────────────────────────────────┤
│  智能体运行时（AgentScope 2.0，唯一引擎）                       │
│  HarnessAgent · call()/streamEvents() · Middleware(五阶段)   │
│  Subagent(Markdown 规格 + agent_spawn/agent_send)            │
│  Plan Mode(agent 自规划长任务) · Permission/HITL             │
│  Distributed Session/Memory(Redis/Mysql, 租户隔离)          │
├─────────────────────────────────────────────────────────────┤
│  资源适配层（保留 DB 驱动管理，重写客户端绑定）                   │
│  ChatModelFactory → AgentScope Model Client(DB+加密+缓存)   │
│  AgentToolProvider → AgentScope Service Toolkit(本地@Tool+MCP)│
│  McpClientManager → AgentScope core MCP                     │
│  PromptTemplateService(保留) · Resilience4j → Middleware    │
│  CostCalculator/AtomicSessionUpdateService(保留,从事件流汇)   │
├─────────────────────────────────────────────────────────────┤
│  存储层（保留）                                                │
│  MyBatis-Plus + 动态数据源(ai-postgres/pgvector) + Redis     │
└─────────────────────────────────────────────────────────────┘
```

无拓扑层、无图、无节点、无 `nextNode`/图快照。

### 5.2 agent 模板（`apps` / `ai_robot` 升格）

`AppEntity` 字段虽多（`llmModelId`/`systemPrompt`/`kbId`/`temperature`/`maxTokens`/`retrievalTopK`/`similarityThreshold`/`welcomeMessage`/`suggestedQuestions`/`publishChannels`/`tenantId`/`enabled`/...），但当前运行时（`ChatSessionServiceImpl.createSession`）建会话仅从 robot 拷贝 **4 项**（`llmModelId`/`systemPrompt`/`kbId`/`workflowId`）：`temperature`/`maxTokens` 虽在 `ChatSessionEntity` 上却未拷贝，`retrievalTopK`/`similarityThreshold`/`welcomeMessage`/`publishChannels` 不在 session。**apps 现为“配置展示对象”，非稳定 agent 模板运行时契约**。Phase 1 不只补列，要补三件事--session 快照扩展、运行时参数覆盖规则、快照稳定性：

- **新增**：`subagentSpec`（多智能体场景，对应 AgentScope `workspace/subagents/<id>.md` Markdown 规格 + 可选编程式 `builder.subagent(...)`）、`toolIds`（绑定本地 `@Tool`）、`toolGroups`（AgentScope Tool Group 配置，运行时动态激活）、`mcpServerIds`（绑定 MCP server）、`kbIds`（多知识库绑定，取代 `kbId`）、`middlewareConfig`（Resilience4j/审计等中间件配置）。
- **移除**：`workflowId`（"关联工作流"图概念）--多智能体改由 `subagentSpec` 表达。
- **session 快照扩展**：`ChatSessionEntity`/`ai_chat_session` 快照全部**执行参数**（`llmModelId`/`systemPrompt`/`kbIds`/`temperature`/`maxTokens`/`retrievalTopK`/`similarityThreshold`）。现运行时仅拷贝 4 项，`temperature`/`maxTokens` 虽在 session 却未拷贝、`retrievalTopK`/`similarityThreshold` 不在 session--Phase 1 补：拷贝 `temperature`/`maxTokens` + 新增 `retrieval_top_k`/`similarity_threshold` 列并拷贝；`welcomeMessage`/`suggestedQuestions`/`publishChannels`/`showCitation` 等展示/发布字段留 app-only（不入 session 运行时）；结构化（`subagentSpec`/`toolGroups`/`mcpServerIds`/`middlewareConfig`）运行时从 robot 实时读（不入快照）。
- **快照稳定性**：session 创建时钉住执行参数，robot 后续编辑**不影响在途会话**（快照即稳定性，本期不做 app 版本表）。
- **运行时参数覆盖规则**：优先级 `请求参数 > session 快照`（= 创建时 robot 配置）；robot 默认仅在 session 创建时经快照生效。`AgentRuntimeService.run` 按此合并构造 agent。
- **app 版本化**（Phase 4）：快照已满足稳定性；若需审计/回滚，Phase 4 加 app 版本表。
- 运行时：`AgentRuntimeService` 读 `AppEntity` → 解密模型密钥 → 构造 AgentScope model client + Service Toolkit + MCP + middleware → `HarnessAgent.builder()` → `call()`/`streamEvents()`。

### 5.3 执行模型

**契约边界（session-centric vs app-centric）**：公开入口是 session-centric（`ChatMessageController` `/v1/chat/sessions/{sessionId}/messages`），故流式走 session；仅可选的编程式 one-shot 走 app。

- **流式（聊天主路，session-centric）** `run(ChatSessionEntity session, input)` → `Flux<Event>`：session 提供 `robotId`（→ AppEntity 模板）+ 执行参数快照 + `sessionId`/`tenantId`（→ `RuntimeContext`）；`agent.streamEvents()` 直接返回 `Flux<Event>`，`ChatMessageServiceImpl` 订阅经 `EventToSseAdapter` 桥接 `SseEmitter`。
- **同步 one-shot（可选，app-centric）** `call(String appId, input)` → `AgentRunResult`：无 session，按 AppEntity 模板构造 agent，`agent.call()` 聚合 `AgentResultEvent`；供可选 `POST /v1/ai/apps/{id}/run`。
- **Resume/HITL（session-centric）** `resume(String sessionId, input)`：AgentScope 分布式会话恢复 + `HintBlockEvent`/权限审批。
- **多智能体**：agent 按 `subagentSpec` 用 `agent_spawn`/`agent_send` 派生子 agent，事件流转发；不再有 `SUPERVISOR`/`PARALLEL` 图节点。
- **复杂长任务**：agent 进 Plan Mode 自规划执行；不再有 `LOOP`/`CONDITIONAL` 图节点。
- **RAG**（路径 2 + middleware，2026-07-18）：知识库检索双模式 hybrid--**static** `RagMiddleware`（`onReasoning` 首次推理前用 user 消息检索，结果作 system context 注入，跨 KB 合并）+ **agentic** `KnowledgeRetrievalTool` @Tool（agent 自主按需检索）；`kbIds` 多 KB 绑定，引擎走未废弃的 `SimpleKnowledge`/`PgVectorStore`（托管型，本期；外部型百炼/Dify retrieval-only 留 Phase 4）；forRemoval 编排层（`Knowledge`/`KnowledgeRetrievalTools`/`FederatedKnowledge`）已清零，`Document`/`RetrieveConfig` 过渡债（底层 Store 依赖，等上游剥离）。

### 5.4 可观测与记账

- 执行轨迹/统计：从 AgentScope 事件流聚合（+ OpenTelemetry），写入 agent run 记录（旧 `WorkflowExecutionEntity` 改用途）。手搓 `_executionTrace` 删除。
- token 记账：事件流中的 usage 事件汇入 `CostCalculator` + `AtomicSessionUpdateService`。

### 5.5 会话与租户

- AgentScope 分布式会话后端（`AgentStateStore`）：`PostgresAgentStateStore`（`agentscope-extensions-postgresql`，**PG 一等后端**，spike 已核实）复用 **ai-postgres `DataSource`**，与 AI 业务表同库；或 Redis（`lambda-cloud-starter-redis`，低延迟）。执行轨迹走 `ai_agent_run`（ai-postgres PG，与现 `ai_workflow_execution` 同库），不在 `AgentStateStore`。D5 定。
- **数据源方案（闭合）**：PG（ai-postgres）**原本只为 pgvector**--AI 业务表同库是历史副作用，非有意选型。RAG 转 AgentScope 后，向量库后端由 S8 定（pgvector/Milvus/Qdrant/Redis/in-memory），**AI 业务表的库选择随 S8**（D9）：
  - **S8=pgvector**：向量留 PG；业务表留 ai-postgres（现状，最小改动）。
  - **S8=非 PG 向量库**：向量离开 PG；业务表宜迁 **主库 MySQL**（对齐主应用、弃用 PG），但需重写 PG 专属 changelog（15 个 `jsonb` 列等）+ 调整 `AiDataSourceAspect`/`AiDataSourceInterceptor`/`AiConfigure` schema 初始化（不再切 ai-postgres）--较大迁移，Phase 3/4 评估。
  - **本期默认**：业务表留 ai-postgres（现状，最小改动，无需绕过现有切面/拦截器）；S8 出结论后定 D9。
  - `AgentStateStore` 注入 ai-postgres `DataSource`（`PostgresAgentStateStore`，PG 一等后端）或 Redis 客户端（D5；若 D9 迁 MySQL主库，AgentStateStore 可随之用 `MysqlAgentStateStore` + 主库 `DataSource`）。
- `RuntimeContext`（session/user/agent/org）承载 `tenant_id`，会话/记忆/工作区按租户隔离，叠加于现有字段级 `tenant_id` 隔离。

### 5.6 契约破坏与迁移策略（workflow 退出）

砍 workflow 域不是内部替换，是显式的 API/数据契约破坏：聊天主链路现按 `session.workflowId` 二分运行，`/v1/ai/workflows` 是公开 API，`ai_robot.workflow_id`/`ai_chat_session.workflow_id` 是持久化字段。开发期、无在产下游（D1），采取**硬废弃、不留兼容桥**，但明确迁移映射：

- **运行时分支坍缩**：`ChatMessageServiceImpl.doSendMessageStream` 现按 `session.workflowId != null` 二分（workflow 路径 vs RAG `streamChat` 路径）。砍后**单一路径** `AgentRuntimeService.run(session, input)` → `Flux<Event>` → SSE。agent 由"robot 模板（若 `robotId`）+ session 内联快照（`llmModelId`/`systemPrompt`/`kbIds`/`temperature`/`maxTokens`）"构造--无 robot 的会话仍可用内联配置作最小模板（保留现 RAG 路径无 robot 能力）。删 `executeWorkflowStream` + `ragService.streamChat` 二分。
- **会话字段**（`ChatSessionEntity`/`ai_chat_session`）：**移除** `workflowId`（列 `workflow_id`）；`kbId` → `kbIds`（列 `kb_id` → `kb_ids`，JSON 数组，pin 会话 KB 集）；保留 `robotId`；执行参数全量快照（`temperature`/`maxTokens` 补拷贝 + `retrievalTopK`/`similarityThreshold` 新增列）见 §5.2；`subagentSpec`/`toolGroups`/`mcpServerIds`/`middlewareConfig` 运行时从 robot 实时读（不入 session 快照）。`ChatSessionServiceImpl.createSession` 复制 `robot.kbIds`（非 `kbId`），删 `workflowId` 复制。
- **API 废弃**：删 `WorkflowController`（`/v1/ai/workflows`：CRUD + `execute`/`execute/stream`/`resume`/`executions`）+ `WorkflowTemplateController`。**硬移除，无 shim**（D1）。执行入口收敛到聊天（`ChatMessageController` 经 `AgentRuntimeService`）；若需非聊天编程式执行，新增 `POST /v1/ai/apps/{id}/run`（直连 `AgentRuntimeService`，Phase 2 评估）。
- **数据迁移**（Liquibase，见 §8）：drop `ai_robot.workflow_id`、`ai_chat_session.workflow_id`；`ai_chat_session.kb_id` → `kb_ids`；drop `ai_agent_workflow`/`ai_workflow_template`/`ai_workflow_template_version`；`ai_workflow_execution` → `ai_agent_run`。**无数据迁移**：现有 workflow 定义与会话 `workflow_id` 引用直接丢弃（开发期）；会话 drop `workflow_id` 后仍经 `robotId` 运行（`workflow_id` 仅在 `robotId` 存在时复制，drop 安全）。

### 5.7 API/DTO 迁移（硬破坏）

`kbId` → `kbIds` 与“请求参数覆盖 session 快照”涉及请求体改名 + endpoint schema 换。开发期无下游（D1）**硬破坏、无 shim**：

- **`CreateApp`/`UpdateApp`**（`POST`/`PUT /v1/ai/apps`，别名 `/v1/ai/robots`）：`kbId`(String) → `kbIds`(List<String>)；**移除** `workflowId`；新增 `subagentSpec`/`toolIds`/`toolGroups`/`mcpServerIds`/`middlewareConfig`。
- **`CreateSession`**（`POST /v1/chat/sessions`）：`kbId`(String) → `kbIds`(List<String>)（无 `workflowId`，仅改名）。
- **`SendMessage`**（`POST /v1/chat/sessions/{sessionId}/messages/stream`）：现仅 `content`；**新增可选 override 字段**（`temperature`/`maxTokens`/`kbIds`/`llmModelId`，或嵌套 `overrides` 对象）实现“请求参数 > session 快照”覆盖；`AgentRuntimeService.run` 按此合并。
- **旧客户端**：硬失效--`kbId`/`workflowId` 字段移除，旧请求体发这些字段被忽略（Jackson 默认）或拒收；无兼容 shim（D1）。`/v1/ai/workflows` 整组 endpoint 删（见 §5.6）。

---

## 6. 保留与重写（非"为适配而保留"，是真实价值）

| 组件 | 处理 | 理由 |
|---|---|---|
| `ChatModelFactory`（DB 模型表+密钥加密+Caffeine 缓存） | 保留管理面，模型客户端改产出 AgentScope model client | DB 驱动多提供方+密钥加密是产品价值，AgentScope model client 需运行时构造 |
| `AgentToolProvider`（本地 `@Tool`+MCP 表） | 保留管理面，工具绑定改 AgentScope Service Toolkit | 工具 DB 管理是产品价值 |
| `McpClientManager` | 改 AgentScope core MCP | MCP server DB 管理+密钥 |
| `PromptTemplateService` | 保留管理面，渲染换中性引擎（现用 langchain4j `PromptTemplate`） | 提示词管理 |
| `AiConfigure.LlmResilienceConfig` | 保留配置，接入点改 middleware | Resilience4j 调参保留 |
| `CostCalculator`/`AtomicSessionUpdateService` | 保留，输入改事件流 usage | 记账/结算 |
| `knowledge/*` | **两类产品模型**：托管型（`SimpleKnowledge`+`PgVectorStore`，全生命周期：上传/重处理/分块/向量删除，本期）+ 外部型（百炼/Dify/RAGFlow，retrieval-only，文档归外部平台，Phase 4）；`DocumentController`/`DocumentServiceImpl`/`DocumentProcessor` **仅作用于托管型**；砍 forRemoval 编排层（`Knowledge`/`KnowledgeRetrievalTools`/`FederatedKnowledge`），保留未废弃底层；RAG 接入 = `KnowledgeRetrievalTool` @Tool + `RagMiddleware` hybrid | D3 路径 2 |
| `chat/*`（`ChatMessageServiceImpl`/`ChatSessionServiceImpl`/`ChatSessionEntity`） | 坍缩 `workflowId` 分支为单一 `AgentRuntimeService.run` 路径；session 移除 `workflowId`、`kbId`→`kbIds`；订阅 `Flux<Event>`→SSE | 聊天集成 + 会话契约 |
| `AiProperties`/`AiConstants`/`exception/*` | 保留 | 基础设施 |
| DB Schema | 见 §8 迁移 | - |

---

## 7. 分阶段实施计划

### Phase 0 - 前置探查（spike，~3–5 天，非门禁）

| # | 探查项 | 通过标准 |
|---|---|---|
| S1 | `call()`/`streamEvents()` API 形状 + 事件类型清单 | 最小 agent 跑通同步+流式 |
| S2 | DB 驱动 model client 动态构造 | `LlmModelEntity`（解密 apiKey/baseUrl/modelName）→ AgentScope model client |
| S3 | MCP 接入 | `McpServerEntity` → AgentScope core MCP client，列工具+执行 |
| S4 | `Flux<Event>` → SSE 桥接 | `EventToSseAdapter` 样板，token/finish/error 正确推送 |
| S5 | subagent + Plan Mode | Markdown 规格 → `agent_spawn`；Plan Mode 自规划跑通 |
| S6 | 分布式会话 + 租户隔离 | `PostgresAgentStateStore`（`-extensions-postgresql`，ai-postgres PG 一等后端，spike 已核实）或 Redis + `RuntimeContext` `tenant_id` 隔离可落地；定 D5 |
| S7 | HITL/resume | `HintBlockEvent` + 会话恢复，"中断-保存-恢复"走通 |
| S8 | rag-simple 向量库适配器 | 确认 5 个适配器是否含 pgvector（决定能否复用 `ai-postgres`） |
| S9 | `@Tool` 注解迁移 | AgentScope `@Tool` vs langchain4j `@Tool` 扫描迁移成本（注解包名不同） |

> 产出：`docs/refactor/ai-agentscope-spike.md` + 选定模块清单（`agentscope-spring-boot-starter` + `-extensions-model-*` + `-extensions-rag-simple` + 会话后端）。

### Phase 1 - AgentScope 运行时层 + 资源适配 + agent 模板 · ~1.5–2 周

- [ ] `lambda-fusion-ai/pom.xml`：引入 `agentscope-spring-boot-starter` + 选定扩展（**不删**旧依赖，待 Phase 3）。
- [ ] 新增 `agent/runtime/` 子包：
  - `AgentScopeRuntimeProperties`（`lambda.fusion.ai.agentscope.*`）
  - `ModelClientFactory`（`ChatModelFactory` DB/加密/缓存 → AgentScope model client）
  - `ToolToolkitAdapter`（`AgentToolProvider` → AgentScope Service Toolkit）
  - `McpClientAdapter`（`McpServerService` → AgentScope core MCP）
  - `EventToSseAdapter`（`Flux<Event>` → `SseEmitter`，唯一边界适配）
  - `DistributedSessionConfig`（Redis/Mysql + `tenant_id` 隔离）
- [ ] `AgentRuntimeService` + Impl：`call`/`run(→Flux<Event>)`/`resume`；按 session（`robotId`→AppEntity 模板 + 快照）构造 `HarnessAgent`。
- [ ] 扩展 `AppEntity` → agent 模板：新增 `subagentSpec`/`toolIds`/`toolGroups`/`mcpServerIds`/`kbIds`/`middlewareConfig` 列，移除 `workflowId`，`kbId`→`kbIds`。
- [ ] **session 快照扩展**：`ChatSessionEntity`/`ai_chat_session` 新增 `retrieval_top_k`/`similarity_threshold` 列；`createSession` 拷贝全量执行参数（`llmModelId`/`systemPrompt`/`kbIds`/`temperature`/`maxTokens`/`retrievalTopK`/`similarityThreshold`），drop `workflowId` 拷贝；展示字段留 app-only。
- [ ] **运行时参数覆盖规则**：`AgentRuntimeService.run` 按 `请求参数 > session 快照` 合并构造 agent；结构化配置（`subagentSpec`/`toolGroups`/`mcpServerIds`/`middlewareConfig`）从 robot 实时读。
- [ ] **快照稳定性**：session 创建钉住执行参数（robot 编辑不影响在途会话）；单测覆盖。
- [ ] `AiConfigure`：条件装配 AgentScope Bean（`@ConditionalOnClass`）。
- [ ] 单测：model/tool/MCP/event→SSE/agent 构造各一条 happy path。
- [ ] 文档：补 `docs/skills/lambda-fusion-ai/SKILL.md`（该模块尚无 SKILL.md）。

**里程碑**：一个 app 模板能构造 AgentScope agent 并经 `Flux<Event>` → SSE 流式回答。

### Phase 2 - 聊天层重接 + RAG 接入 + 记账 · ~1–1.5 周

- [ ] `ChatMessageServiceImpl`：两条旧路（RAG `streamChat` + `executeWorkflowStream`）统一为 `AgentRuntimeService.run(session, input)` 订阅 `Flux<Event>` → SSE。
- [ ] RAG 引擎（路径 2 + middleware）：`KnowledgeFactory`（DB KB 元数据 -> `SimpleKnowledge`+`PgVectorStore`，保留未废弃底层）-> agentic `KnowledgeRetrievalTool` @Tool + static `RagMiddleware`（`onReasoning` 检索注入）hybrid 装配；删自研 pgvector 管线 + `AgentGraphProvider` + forRemoval 编排层（`Knowledge`/`KnowledgeRetrievalTools`/`FederatedKnowledge`）。
- [ ] 记账：事件流 usage → `CostCalculator`/`AtomicSessionUpdateService`。
- [ ] 新测试套件：`AgentRuntimeService` + 各适配器 + 多智能体/subagent + 租户隔离。

**里程碑**：聊天经 AgentScope agent 端到端跑通（单 agent + RAG + 多智能体 subagent）。

### Phase 3 - 拆除与清理（单次 cutover）· ~1 周

- [ ] cutover commit：`AgentRuntimeService` 成为唯一路径，移除旧引擎装配。
- [ ] 删除 §4 全部（`AgentGraph`/9 节点/`AgentNode`/`AgentState`/`GraphDefinition`/evaluator/图工厂/workflow 域/图模板）。
- [ ] `pom.xml` 移除 `langgraph4j-*`、`langchain4j-spring-boot-starter`/`-ollama`/`-open-ai`/`-mcp`/`-document-parser-pdfbox`/`-poi`（全删，RAG 转 AgentScope readers）。
- [ ] 父 POM：移除 `langgraph4j.version`/`langchain4j*` 版本属性与 BOM。
- [ ] Resilience4j 接入点改 middleware。
- [ ] DB 迁移（§8）。

**里程碑**：`grep org.bsc.langgraph4j` 在 `src/main` 为 0；`grep dev.langchain4j` 在 `src/main` 为 **0**（无例外）。

### Phase 4 - 产品演进 · 持续

- [ ] AgentScope 权限系统（工具审批）、HITL、沙箱（coding agent）。
- [ ] Plan Mode 深度利用（长任务自规划）。
- [ ] A2A + Nacos 跨服务智能体（与现有 Dubbo/Nacos 融合）。
- [ ] OpenTelemetry 全链路 trace。
- [ ] 评估 AgentScope Studio/AG-UI 做**运行时观测**（非编排）；前端聊天侧渲染事件流。
- [ ] 更新 `AGENTS.md`/`docs/skills/lambda-fusion-ai/SKILL.md`/`docs/CODE_WIKI.md`。

---

## 8. DB 迁移（Liquibase）

- **删除图专属表/字段**：`ai_agent_workflow`（`graph_json` 列）、`ai_workflow_template`/`ai_workflow_template_version`（`definition` 图定义）。对应 changelog 加 drop。
- **`ai_robot`（agent 模板）扩展**：新增 `subagent_spec`/`tool_ids`/`tool_groups`/`mcp_server_ids`/`kb_ids`/`middleware_config` 列；移除 `workflow_id`；`kb_id` 改 `kb_ids`（多 KB）。
- **`ai_chat_session` 迁移**：drop `workflow_id` 列；`kb_id` → `kb_ids`（JSON 数组，pin 会话 KB 集）；保留 `robot_id` + 标量快照。
- **RAG 引擎迁移**：`ai_knowledge_base` 增加 `backend_type`（`simple` 托管型，本期；`bailian`/`dify`/`ragflow`/`haystack` 外部型，Phase 4）/`store_config` 列；自研向量分表 `ai_vector_store_*` 弃用（若 S8 确认 `-rag-simple` 支持 pgvector，复用 `ai-postgres` 作 `VDBStore`，否则迁到选定向量库）；`ai_document`/`ai_document_chunk` 元数据保留（**仅托管型 KB**），分块由 AgentScope 生成。
- **执行记录改用途**：`ai_workflow_execution` → `ai_agent_run`（ai-postgres PG，或保留表名改语义）：移除图步字段 `pipeline_id`/`pipeline_version`/`current_step`/`progress`；保留 `user_id`/`input_params`/`output_result`/`status`/`duration_ms`/`tenant_id`/`execution_log`（轨迹）/`started_at`/`completed_at`；`thread_id`/`checkpoint_id` 改映射 AgentScope `session_id`。
- **新增**（如 D5 选 Redis/Mysql 会话）：AgentScope 会话存储表（由扩展管理，不破坏现有表）。

迁移走**现有拆分文件** `META-INF/db/changelogs/lambda-ai-schema-changelog.xml`（业务表）+ `lambda-ai-vector-changelog.xml`（向量表），均匹配 `lambda-\w*-changelog.xml` 由 liquibase starter 聚合；**不新建聚合文件**（仓库无 `lambda-ai-changelog.xml`）。

---

## 9. 测试与回滚

- **无黄金对拍**：推倒重来，不保留旧引擎行为作基线。新测试套件直接基于 AgentScope（Phase 1–2 建）。
- **保留测试**：`ServiceRegressionTest`（知识库/RAG 服务回归）。`VectorDimensionProcessorTest` 随自研 pgvector 删除。
- **新建测试**：`AgentRuntimeService`（call/run/resume）、`ModelClientFactory`/`ToolToolkitAdapter`/`McpClientAdapter`/`EventToSseAdapter`、subagent/Plan Mode、租户隔离。
- **构建门禁**：`mvn -pl lambda-fusion-ai test` 全绿；`mvn -pl lambda-fusion-ai compile`（Spotless Palantir + SpotBugs，本模块 `spotbugs.skip=true`）通过。
- **回滚**：无运行时开关。git revert 对应 commit（Phase 3 cutover commit 是关键回滚点）。Phase 1 只增不删依赖，回滚仅移除新增 jar。

---

## 10. 决策清单

| # | 决策项 | 结论 | 依据 |
|---|---|---|---|
| D1 | 重建方式 | **推倒重来，AgentScope 原生** | 开发期无下游；保图层是硬适配 |
| D2 | 图编排/前端画布 | **砍掉** | AgentScope 不原生支持；硬编译=翻译税 |
| D3 | RAG | **路径 2 半迁移 + middleware**（2026-07-18 修正）：砍 forRemoval 编排层（`Knowledge`/`KnowledgeRetrievalTools`/`FederatedKnowledge`），保留未废弃底层（`SimpleKnowledge`/`PgVectorStore`/`Reader`/`EmbeddingModel`）；agentic `KnowledgeRetrievalTool` @Tool + static `RagMiddleware`（`onReasoning` 检索注入）hybrid（对齐官方推荐 middleware）。`Document`/`RetrieveConfig` 过渡债（底层 Store 依赖，等上游剥离） | v3"改用 AgentScope `Knowledge`"假设 RAG 抽象稳定，核实 2.0.0 已标 forRemoval；Java `MiddlewareBase` 无 `list_tools`，agentic 标准即 @Tool |
| D4 | Resilience4j | **保留作 middleware 实现** | 配置/调参保留 |
| D5 | 会话后端 | spike S6 定：`PostgresAgentStateStore`（`-extensions-postgresql`，**PG 一等后端**，spike 已核实）复用 ai-postgres `DataSource`（与 AI 业务表同库）vs Redis（低延迟）；业务表库见 D9 | - |
| D6 | agent 模板载体 | **`apps`（`ai_robot`）升格为 agent 模板** | 字段已有但运行时仅快照 4 项；Phase 1 须补 session 快照扩展 + 覆盖规则 + 快照稳定性（非仅加列） |
| D7 | 运行时观测 UI | Phase 4 评估 Studio/AG-UI | 非编排，非阻塞 |
| D8 | 版本兼容 spike | **取消** | AgentScope core Java 17（本仓库 21）；fasterxml Jackson 已因 langchain4j 在 classpath |
| D9 | AI 业务表库 | spike S8 后定：S8=pgvector 则留 ai-postgres（现状）；S8=非 PG 则评估迁主库 MySQL（弃用 PG，需重写 15 个 `jsonb` 列 + PG changelog + 调整切面/拦截器/schema 初始化） | PG 原本只为 pgvector，业务表同库是副作用；迁 MySQL 对齐主应用但成本不低 |

---

## 11. 立即可执行的下一步

1. 评审本 v3 方案，确认 D1–D9。
2. 启动 Phase 0 spike（§7），产出 `docs/refactor/ai-agentscope-spike.md`。
3. 若采用 OpenSpec：拆为 `openspec/changes/ai-agentscope-rebuild/`（`proposal.md`/`design.md`/`tasks.md`/`specs/`）。
4. Phase 0 完成后按 Phase 1 落地运行时层 + `apps` 模板扩展 + SKILL.md。

---

### 附录 A：关键文件清单

**拆除**（§4）：`agent/AgentGraph.java`（829）、`agent/AgentNode.java`（52）、`agent/AgentState.java`（183）、`agent/model/*`（图 Schema）、`agent/evaluator/*`（6）、`agent/node/*`（9）、`agent/factory/AgentGraph*`、`workflow/**`（entity/service/controller，Impl 1067）、`examples/workflows/*.json`、`knowledge/VectorRepository`/`PgVectorTypeHandler`/`VectorDimensionProcessor`/`DocumentProcessor`（自研 RAG 引擎）、`PromptTemplateServiceImpl` 的 langchain4j 渲染、相关测试约 19 个。agent 子树 5983 行砍 70%+。

**保留重写**：`agent/factory/ChatModelFactory.java`（260）、`agent/tools/AgentToolProvider.java`（319）、`mcp/manager/McpClientManager.java`（233）、`chat/service/impl/ChatMessageServiceImpl.java`（424）、`AiConfigure.java`（228）、`AiProperties.java`（244）、`apps/**`（agent 模板载体）、`knowledge/**`（管理面保留、引擎保留未废弃 `SimpleKnowledge`/`PgVectorStore`，RAG 经 `KnowledgeRetrievalTool` + `RagMiddleware` hybrid）、`llm/**`（管理面）、`prompt/**`（渲染换中性引擎）、`chat/suooprt/CostCalculator`。

**装配**：`AiConfigure.java`、`AiProperties.java`、`autoconfig/AiAutoConfiguration.java`。

### 附录 B：AgentScope 2.0 参考

- Maven Central：<https://repo1.maven.org/maven2/io/agentscope/agentscope-bom/2.0.0/>
- 仓库/README：<https://github.com/agentscope-ai/agentscope-java>
- 文档：<https://java.agentscope.io/>
- DeepWiki：<https://deepwiki.com/agentscope-ai/agentscope-java>
