# Tasks · lambda-fusion-ai AgentScope 2.0 原生重建

> 关联方案：`docs/refactor/ai-agentscope-refactor.md`（v3）　spike 结论：`docs/refactor/ai-agentscope-spike.md`
> 决策：D1 推倒重来 / D2 砍图层 / D3 RAG 路径 2 + middleware（砍 forRemoval 编排层，保留未废弃底层，agentic @Tool + static `RagMiddleware` hybrid） / D5 会话后端 PG（`agentscope-extensions-postgresql`，已核实存在）/ D6 apps 升格 agent 模板 / D9 业务表库随 S8。
> 已核实 API 见 spike 文档；本文件任务据此拆解，可照此实施。

## Phase 0 — 前置探查（spike）· 已完成

- [x] S1 核实 `HarnessAgent.call()`/`streamEvents()` API 形状与 28 种 `AgentEventType`（见 spike §1）
- [x] S2 核实 DB 驱动 model client 构造：`LlmModelEntity` -> `OpenAIChatModel`/`OllamaChatModel` builder（见 spike §2）
- [x] S3 核实 MCP：AgentScope core `Toolkit.registerMcpClient(McpClientWrapper)`（见 spike §3）
- [x] S4 核实 `Flux<AgentEvent>` -> SSE 桥接事件访问器（`TextBlockDeltaEvent.getDelta()` / `AgentResultEvent.getResult()` / `ChatUsage`）（见 spike §4）
- [x] S6 核实分布式会话：`PostgresAgentStateStore(DataSource)` + `RuntimeContext.builder().sessionId().userId().put()`（见 spike §6；**修正方案：PG 是一等后端，非"经 mysql 方言"**）
- [x] S8 核实 `-rag-simple` 适配器清单（见 spike §8，待落地时确认 pgvector 适配器）
- [x] S9 核实 `@Tool` 注解迁移：AgentScope `io.agentscope.core.tool.Tool`（见 spike §9）
- [x] 核实 BOM 真实 artifact 清单（见 spike 附录 A；**修正方案：`agentscope-extensions-postgresql` 存在**）

## Phase 1 — AgentScope 运行时层 + 资源适配 + agent 模板 · ~1.5–2 周

### 1.1 依赖与装配
- [x] `lambda-fusion-ai/pom.xml` 引入 `agentscope-harness` + `agentscope-extensions-model-openai` + `-model-ollama` + `-model-dashscope` + `-postgresql` + `-rag-simple`（**不删**旧 langgraph4j/langchain4j 依赖，待 Phase 3）
- [ ] `AiConfigure`：新增 `@ConditionalOnClass(HarnessAgent.class)` 的 `AgentScopeRuntimeConfiguration`，装配 `AgentRuntimeService` / `ModelClientFactory` / `EventToSseAdapter` / `AgentStateStore` Bean
- [ ] `AgentScopeRuntimeProperties`（`lambda.fusion.ai.agentscope.*`）：会话后端选择（pg/redis）、默认 maxIters、stateStore schema/table、是否启用 plan mode

### 1.2 资源适配层（`agent/runtime/`）
- [x] `ModelClientFactory`：`LlmModelEntity`（解密 apiKey + baseUrl + modelName）-> AgentScope `Model`；provider 映射 OPENAI->`OpenAIChatModel`/OLLAMA->`OllamaChatModel`/DASHSCOPE->`DashScopeChatModel`（三者 spike javap 核实；GEMINI/ANTHROPIC 待跟进）；Caffeine 缓存（沿用 `ChatModelFactory` 的 1h/100 策略）；保留 `invalidateModelCache`
- [x] `ToolToolkitAdapter`：扫描 Spring 容器 `@Tool`（AgentScope 注解 `io.agentscope.core.tool.Tool`）Bean -> `Toolkit.registerTool(Object)`；`buildToolkit()` 产出注册了所有本地 @Tool 的 Toolkit，已接入 `AgentRuntimeServiceImpl.buildAgent`（`.toolkit(...)`）。**按 app `toolIds`/`toolGroups` 过滤/激活推迟 Phase 2**（待 `AppEntity.toolIds`/`toolGroups` 字段落地）。MCP server -> `Toolkit.registerMcpClient(...)` 的 per-agent 装配同 Phase 2（待 `AppEntity.mcpServerIds`）。
- [x] `McpClientAdapter`：`McpServerEntity` -> AgentScope `McpClientWrapper`（`McpClientBuilder.create().stdioTransport(cmd,args,env)/streamableHttpTransport(url).timeout(d).buildSync()`，取代 langchain4j-mcp `DefaultMcpClient`）；Caffeine 缓存 + 自动关闭；`get(serverId)`/`invalidateCache`/`invalidateAll`。
- [x] `EventToSseAdapter`：`Flux<AgentEvent>` -> `SseEmitterManager.sendEvent`；`TEXT_BLOCK_DELTA`->`message` token、`AGENT_RESULT`->`finish`、`TOOL_CALL_*`/`TOOL_RESULT_*`->`tool`、`HINT_BLOCK`->`hitl`、错误经 Reactor `onError`->`error`
- [ ] `DistributedSessionConfig` / `AgentStateStoreConfig`：`PostgresAgentStateStore(ai-postgres DataSource, schema, table, createIfNotExist=true)`（`@ConditionalOnClass` + `@ConditionalOnProperty`）；Redis 备选（`-extensions-redis`）留 D5

### 1.3 Agent 运行服务
- [x] `AgentRuntimeService` 接口：`run(ChatSessionEntity, SendMessage) -> Flux<AgentEvent>`（session-centric 流式）/ `call(String appId, input) -> Mono<Msg>`（app-centric one-shot，可选）/ `resume(String sessionId, input) -> Flux<AgentEvent>`（HITL）
- [x] `AgentRuntimeServiceImpl`：按 session（`robotId`->`AppEntity` 模板 + 执行参数快照）+ 请求 override 合并 -> 构造 `HarnessAgent.builder().name().sysPrompt().model(ModelClientFactory).toolkit(ToolToolkitAdapter).stateStore(AgentStateStore).generateOptions(temperature/maxTokens).build()`；`RuntimeContext.builder().sessionId().userId().put("tenantId", tenantId).build()`；`agent.streamEvents(Msg, ctx)`
- [ ] 运行时参数覆盖规则单测：`请求参数 > session 快照`（SendMessage override 字段合并）
- [ ] 快照稳定性单测：session 创建钉住执行参数，robot 后续编辑不影响在途会话

### 1.4 agent 模板（`apps` / `ai_robot`）扩展
- [x] `AppEntity` 移除 `workflowId`（字段 + `@Schema`，2026-07-19 落地）
- [x] `AppEntity` `kbId`->`kbIds`（`List<String>` + `@TableField(typeHandler=JacksonTypeHandler.class)` + `@TableName(autoResultMap=true)`，2026-07-19 落地）
- [ ] `AppEntity` 新增列：`subagentSpec`/`toolIds`/`toolGroups`/`mcpServerIds`/`middlewareConfig`（Phase 2/3 robot-as-template，DB 列待建）
- [x] `ChatSessionEntity` / `ai_chat_session` 快照扩展（**additive 部分，已落地**）：新增 `retrieval_top_k`/`similarity_threshold` 列（Liquibase `lambda-fusion-ai-20260719100001-pg`）；`createSession` 补拷贝 `temperature`/`maxTokens`/`retrievalTopK`/`similarityThreshold`（旧实现仅拷 4 项）。**硬破坏部分**：drop `workflowId` 拷贝 **已落地**（2026-07-19：workflow 域已删、`AiErrorCode.WORKFLOW_*` 9 个错误码已删、`AiBusinessException.workflowNotFound` 已删、`ChatMessageServiceImpl` 单链路走 `agentRuntimeService.run`）；`kbId`->`kbIds` **已落地**（2026-07-19，`List<String>` + `JacksonTypeHandler`，多 KB 经 `FederatedKnowledge` 联邦检索，见下）。展示字段留 app-only。
- [x] DTO `CreateApp`/`UpdateApp`/`Robot`/`ChatSession` drop `workflowId`（+`@Schema`，2026-07-19）
- [x] DTO `CreateApp`/`UpdateApp`/`CreateSession`/`Robot`/`ChatSession` 的 `kbId`(String)->`kbIds`(List)（2026-07-19）
- [ ] DTO 新增 `subagentSpec`/`toolIds`/`toolGroups`/`mcpServerIds`/`middlewareConfig`；`SendMessage` 新增可选 override（`temperature`/`maxTokens`/`kbIds`/`llmModelId`）（Phase 2/3）

### 1.5 文档与门禁
- [ ] 新建 `docs/skills/lambda-fusion-ai/SKILL.md`（该模块尚无 SKILL.md）
- [ ] 单测：model/tool/MCP/event->SSE/agent 构造各一条 happy path
- [ ] 里程碑：一个 app 模板能构造 AgentScope agent 并经 `Flux<AgentEvent>` -> SSE 流式回答

## Phase 2 — 聊天层重接 + RAG 接入 + 记账 · ~1–1.5 周

- [x] `ChatMessageServiceImpl.doSendMessageStream`：两条旧路（RAG `streamChat` + `executeWorkflowStream`）统一为 `AgentRuntimeService.run(session, input)` 订阅 `Flux<AgentEvent>` -> `EventToSseAdapter` -> SSE；本文件清零 langchain4j（6 处耦合之一），移除 `buildChatHistory`/`applyTokenUsage`/`createAssistantMessageEntity`/`executeWorkflowStream`/`buildWorkflowExecutionRequest` 及 RagService/WorkflowExecutionService 字段。
- [x] RAG 引擎（**路径 2 半迁移，2026-07-18**）：`KnowledgeFactory`（`KnowledgeBaseEntity` -> `SimpleKnowledge`：embedding 模型按 provider 选 OpenAI/DashScope/Ollama `TextEmbedding` + `PgVectorStore` 复用 ai-postgres JDBC 凭据，Caffeine 缓存）返回 `SimpleKnowledge`/`List<SimpleKnowledge>`（不再产出 forRemoval 的 `Knowledge` 接口）。检索工具为**应用层自写 `@Tool`** `KnowledgeRetrievalTool`（取代 AgentScope 2.0 forRemoval 的 `KnowledgeRetrievalTools`）：per-agent 构造期绑定 `List<SimpleKnowledge>`，`retrieve_knowledge` 跨 KB fan-out + 按 `Document.score` 合并取 topK（沿用旧 `FederatedKnowledge` 策略），单 KB 失败 `onErrorResume` 跳过；`AgentRuntimeServiceImpl.buildToolkit` 按 session `kbIds` + `retrievalTopK` 装配。**删 `FederatedKnowledge`**（implements forRemoval `Knowledge`，多 KB 合并改入 `KnowledgeRetrievalTool`）。**背景**：AgentScope 2.0 将 `io.agentscope.core.rag.Knowledge`/`KnowledgeRetrievalTools`/`Document`/`DocumentMetadata`/`RetrieveConfig`/`RAGMode`/`GenericRAGHook` 标 `@Deprecated(forRemoval=true)`，指引"integrate retrieval at the application layer"；底层 `SimpleKnowledge`/`PgVectorStore`/`VDBStoreBase`/各 `Reader`/`EmbeddingModel` 未废弃（保留）。`Document`/`RetrieveConfig` 仍接触（底层 Store 依赖 deprecated `Document`，AgentScope 过渡债，等上游改完再说）。
- [x] RAG middleware（**官方推荐 middleware 方式，2026-07-18**）：官方沟通推荐用 middleware 做 RAG（Python `RAGMiddleware` 有 static+agentic 两模式）。Java 2.0.0 无内置 `RAGMiddleware`，且 `MiddlewareBase` 无 Python 的 `list_tools` hook（核实：仅 5 个 hook onAgent/onReasoning/onActing/onModelCall/onSystemPrompt；`listTools` 只在 MCP 客户端，与 middleware 无关）。落地：**static 模式**自写 `RagMiddleware`（`onReasoning` 首次推理前用最后一条 user 消息检索，结果作 system context message prepend 注入，跨 KB 合并取 topK，`RuntimeContext` 标记避免每轮 ReAct 重复检索）；**agentic 模式**沿用 `KnowledgeRetrievalTool` @Tool（Java 无 list_tools，agentic 标准即 @Tool，等价 Python agentic 的 `search_knowledge`）。`AgentRuntimeServiceImpl.buildAgent` hybrid 装配（kbs 非空时同装 `RagMiddleware` + `KnowledgeRetrievalTool`，共享 `resolveKnowledgeBases` 一次解析）= Python hybrid 模式。
- [x] ragMode 配置化（2026-07-18）：`AppEntity.ragMode`（STATIC/AGENTIC/HYBRID，默认 HYBRID）+ DTO（CreateApp/UpdateApp/Robot）+ `ai_robot.rag_mode` 列（changelog `lambda-fusion-ai-20260718130001-pg`，defaultValue=HYBRID）。`AgentRuntimeServiceImpl.buildAgent` 按 ragMode 选择性装配：STATIC（仅 `RagMiddleware`）/AGENTIC（仅 `KnowledgeRetrievalTool` @Tool）/HYBRID（两者）；`isStaticRag`/`isAgenticRag` 解析（null 默认 HYBRID，保持原 hybrid 行为）。ragMode 从 robot 实时读（不入 session 快照，与 subagentSpec/middlewareConfig 同类结构化配置）。
- [x] 命名统一 robot->app（2026-07-18，彻底）：`Robot` DTO -> `App`（文件改名）；`robotId` 字段 -> `appId`（ChatSessionEntity/ChatSession/CreateSession）；`deleteRobot` -> `deleteApp`；`robotNotFound` -> `appNotFound`；`ROBOT_NOT_FOUND`/`ROBOT_DISABLED` -> `APP_NOT_FOUND`/`APP_DISABLED`；`@TableName("ai_robot")` -> `"ai_app"`；`AppsController` 删 `/v1/ai/robots` 别名（只留 `/v1/ai/apps`）；`ChatSessionMapper.xml` `robot_id`->`app_id`（result + 5 select）；局部变量 `robot`->`app` + javadoc。DB changelog `lambda-fusion-ai-20260718140001-pg`：`ALTER TABLE ai_robot RENAME TO ai_app` + `ai_chat_session.robot_id`->`app_id` + 索引 `idx_robot_*`->`idx_app_*`（IF EXISTS 幂等，tableExists ai_robot 保证仅存量库执行）。Java 层全统一；changelog 历史 changeset（createTable ai_robot 等）保留不改（已执行，新 RENAME changeset 处理迁移）。
- [x] ChatSessionMapper.xml 既有清理（2026-07-18，顺手）：select/result map/update 删已 drop 的 `kb_id`/`workflow_id` 列引用（`kb_id`->`kb_ids`、`workflow_id` 随 workflow 域 drop 时 XML 未同步）。活方法 `selectByIdWithVersion`/`listByUserId`/`updateByIdWithVersion`（被 ChatSessionServiceImpl/AtomicSessionUpdateServiceImpl 调用）之前运行时会报"列不存在"。注：自定义 select 现不返回 `kb_ids`/`retrieval_top_k`/`similarity_threshold`（这些字段在自定义 select 结果中为 null，与既有行为一致；MP 默认 CRUD 走 `autoResultMap` 不受影响；若自定义 select 需返回这些字段再补 select 列 + `kb_ids` jsonb 的 JacksonTypeHandler 映射）。
- [x] AppEntity 字段扩展 阶段 A（2026-07-18）：`AppEntity`/`App`/`CreateApp`/`UpdateApp` 加 5 字段（`toolIds`/`mcpServerIds` `List<String>`+`JacksonTypeHandler`；`subagentSpec`/`toolGroups`/`middlewareConfig` `String`/JSON）；DB `ai_app` 加 `tool_ids`/`mcp_server_ids`(jsonb) + `subagent_spec`/`tool_groups`/`middleware_config`(text)（changelog `lambda-fusion-ai-20260718150001-pg`）；`SendMessage` 加 override（`temperature`/`maxTokens`/`kbIds`/`llmModelId`）；`AgentRuntimeServiceImpl.run` 经 `mergeOverride` 合并（请求参数 > session 快照 > app 模板，仅执行参数可 override，结构化配置从 robot 实时读）。**消费逻辑分阶段**：阶段 B（toolIds 过滤 + mcpServerIds 注册）/ C（subagentSpec -> `SubAgentTool` + 递归子 agent）/ D（toolGroups + middlewareConfig）后续，见 `.claude/plans/ai-app-template-expansion.md`。
- [x] AppEntity 字段扩展 阶段 B（2026-07-18）：**toolIds 消费**--`ToolToolkitAdapter.buildToolkit(toolIds)` 按 @Tool name 过滤（null/空=全部，`hasMatchingTool` 按 `Tool.name()`/方法名匹配，bean 级过滤）；**mcpServerIds 消费**--`AgentRuntimeServiceImpl.buildToolkit` 按 mcpServerIds for-each `mcpClientAdapter.get(id)` -> `toolkit.registerMcpClient(...).block()`（per-app，单 server 失败跳过不阻断）；`AgentTemplate` 加 `toolIds`/`mcpServerIds`（从 app 实时读，不入快照，不 override）。阶段 C（subagentSpec）/ D（toolGroups + middlewareConfig）待做。
- [x] 记账：`AgentRunOutcome.inputTokens/outputTokens`（源自 `AgentResultEvent.getResult().getChatUsage()`，经 `EventToSseAdapter` 聚合）-> `persistStreamMessages` -> `CostCalculator`/`AtomicSessionUpdateService`（沿用旧记账路径，输入改为事件流 usage）。
- [ ] 新测试套件：`AgentRuntimeService` + 各适配器 + 多智能体 subagent + 租户隔离
- [ ] 里程碑：聊天经 AgentScope agent 端到端跑通（单 agent + RAG + 多智能体 subagent）

## Phase 3 — 拆除与清理（单次 cutover）· ~1 周

**进度（2026-07-19，live 路径迁移）**：旧适配器的活路径引用已全部切到新适配器——`LlmModelServiceImpl`（`ChatModelFactory`->`ModelClientFactory.invalidateModelCache`）、`McpServerServiceImpl`+`McpServerController`（`McpClientManager`/`AgentToolProvider`->`McpClientAdapter`/`ToolToolkitAdapter`；`/tools` 返回 `ToolSchema` 硬破坏、`/tools/refresh` 改 `refresh()+invalidateAll()`）；删除死代码 `RagService`/`RagServiceImpl`；`McpClientAdapter` 加 `checkConnection`、`ToolToolkitAdapter` 加 `getToolSchemas`。旧 `ChatModelFactory`/`AgentToolProvider`/`McpClientManager` 现仅被注定删除的子树（graph 节点/workflow/`AgentUtils`）引用，活引用=0。仅剩 3 处活 langchain4j 耦合：`PromptTemplateServiceImpl`（渲染）、`DocumentProcessor`（文档解析）、`EmbeddingModelManager`（embedding 模型管理）。注：graph 层+workflow 域+旧适配器+examples+20 测试已删，langgraph4j 已全清零（代码+AI pom+父 POM）。

- [x] cutover commit：`AgentRuntimeService` 成为唯一路径，旧引擎装配已移除（`AiConfigure` 的 `MemorySaver` Bean + `LlmResilienceConfig` 6 Bean 已删）
- [x] 删除 §4 全部：`agent/AgentGraph`/9 节点/`AgentNode`/`AgentState`/`GraphDefinition`/evaluator/图工厂/workflow 域/`examples/workflows/*.json`（graph 层+workflow 域+旧适配器+examples+20 测试已删）
- [x] `pom.xml` 移除 `langgraph4j-*`、`langchain4j-spring-boot-starter`/`-ollama`/`-open-ai`/`-mcp`/`-document-parser-pdfbox`/`-poi`（全删）+ `resilience4j-spring-boot4`
- [x] 父 POM 移除 `langgraph4j.version`/`langchain4j*` 版本属性与 BOM（`_lambda-cloud-parent/pom.xml`）
- [x] Resilience4j **整包删**：核实 AgentScope `ExecutionConfig`（maxAttempts+指数退避 initialBackoff/backoffMultiplier+retryOn 内置可重试异常+timeout）+ `maxIters` + `fallbackModel` 原生覆盖 Retry/Timeout/Fallback；CircuitBreaker/RateLimiter AgentScope 无原生支持,但 maxIters 兜底失控+旧配置为旧 langchain4j 量身,故弃用。删 `AiConfigure.LlmResilienceConfig`(6 Bean+常量)+resilience4j imports+`resilience4j-spring-boot4` pom 依赖。`AgentRuntimeServiceImpl.buildAgent` 接 `.modelExecutionConfig(...)`（超时60s/重试3/退避1s*2,经 `AgentScopeRuntimeProperties.modelTimeoutSeconds/modelMaxAttempts` 可调）。
- [x] DB 迁移 · **workflow 硬废弃部分已落地**（2026-07-19）：
    - schema changelog `lambda-fusion-ai-20260719110001-pg`：`DROP TABLE` `ai_agent_workflow`/`ai_workflow_template`/`ai_workflow_template_version`/`ai_workflow_execution`（CASCADE）+ `DROP COLUMN workflow_id` on `ai_robot`/`ai_chat_session`（均 `IF EXISTS` 幂等）。
    - vector changelog `lambda-fusion-ai-20260719001001-pg`：Path 2 弃用自研维度分表管线--移除原 `create_vector_table` 存储过程 + `ai_vector_store_*` 建表/回填 changeset，新增 cleanup `DROP TABLE ai_vector_store_{768,1536,2048,4096}` + `DROP FUNCTION create_vector_table(INTEGER)`；保留 PG 扩展（`vector` 仍为 AgentScope `PgVectorStore` 所需）。
    - `AiErrorCode` 删 9 个 `WORKFLOW_*` 错误码（30760-30779）+ 段头；`AiBusinessException.workflowNotFound` 删。
- [x] DB 迁移 · `kb_id`->`kb_ids` 已落地（2026-07-19）：schema changelog `lambda-fusion-ai-20260719120001-pg`--`ai_robot`/`ai_chat_session` `kb_id`(varchar)->`kb_ids`(jsonb)，存量 `jsonb_build_array` 迁移，IF EXISTS 幂等。
- [ ] DB 迁移 · **推迟部分**：`ai_robot` 新增 `subagent_spec`/`tool_ids`/`tool_groups`/`mcp_server_ids`/`middleware_config` 列（Phase 2/3 robot-as-template）；`ai_workflow_execution`->`ai_agent_run` 重命名/改语义（待 `AgentRunEntity` 建表）；`ai_knowledge_base.backend_type`/`store_config`（Phase 4 外部型 RAG，实体未含字段，本期 `vectorTableName` 已够）。
- [x] 门禁（已核验 2026-07-19）：`grep org.bsc.langgraph4j` 在 `src/main` = 0；`grep dev.langchain4j` 在 `src/main` = 0（ToolToolkitAdapter javadoc 已去 FQN）；pom.xml langchain4j/langgraph4j `<dependency>` = 0；`resilience4j` = 0。

## Phase 4 — 产品演进 · 持续

- [ ] AgentScope 权限系统（工具审批）、HITL、沙箱（coding agent）
- [ ] Plan Mode 深度利用（长任务自规划）
- [ ] A2A + Nacos 跨服务智能体（与现有 Dubbo/Nacos 融合）
- [ ] OpenTelemetry 全链路 trace（`OtelTracingMiddleware`）
- [ ] 评估 AgentScope Studio/AG-UI 做**运行时观测**（非编排）；前端聊天侧渲染事件流
- [ ] 外部型 RAG（百炼/Dify/RAGFlow，retrieval-only）
