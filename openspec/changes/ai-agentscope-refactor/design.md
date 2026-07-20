# Design · lambda-fusion-ai AgentScope 2.0 原生重建

> 方案权威来源：`docs/refactor/ai-agentscope-refactor.md`（v3）。本文件仅记录落地设计要点与已核实 API，不重复方案论证。

## 1. 已核实 AgentScope 2.0 API（spike 结论，见 `docs/refactor/ai-agentscope-spike.md`）

### 1.1 智能体构造与执行
```java
HarnessAgent agent = HarnessAgent.builder()
    .name("...").description("...").sysPrompt("...")
    .model(ModelClientFactory.get(modelId))   // io.agentscope.core.model.Model
    .toolkit(toolkit)                          // io.agentscope.core.tool.Toolkit
    .stateStore(agentStateStore)               // io.agentscope.core.state.AgentStateStore
    .generateOptions(GenerateOptions.builder()
        .temperature(t).maxTokens(m).build())  // 运行参数在 agent，不在 model
    .maxIters(20)
    // .middleware(...) / .middlewares(...)    // 五阶段 AOP hook
    // .subagents(List<SubagentDeclaration>)   // 多智能体
    // .enablePlanMode()
    .build();

RuntimeContext ctx = RuntimeContext.builder()
    .sessionId(sessionId).userId(userId)
    .put("tenantId", tenantId)                 // 多租户隔离
    .build();

Mono<Msg>          result = agent.call(List<Msg>, ctx);     // 同步 one-shot
Flux<AgentEvent>   events = agent.streamEvents(Msg, ctx);   // 流式（聊天主路）
```
- `streamEvents` 返回 `Flux<io.agentscope.core.event.AgentEvent>`（**非** `Flux<Event>`，方案文档措辞需以此为准）。
- `call` 返回 `Mono<io.agentscope.core.message.Msg>`。

### 1.2 模型适配（`ModelClientFactory`）
- `OpenAIChatModel.builder().apiKey().baseUrl().modelName().stream(true).build()`（`agentscope-extensions-model-openai`）。
- `OllamaChatModel.builder().baseUrl().modelName().build()`（`-model-ollama`，温度经 `OllamaOptions`/`GenerateOptions`）。
- `DashScopeChatModel`（`-model-dashscope`）。
- provider 映射自 `LlmModelEntity.provider`（OPENAI/OLLAMA/DASHSCOPE）；apiKey 经 `KeyEncryptionService.decrypt`；Caffeine 缓存沿用 `ChatModelFactory` 的 1h/100 策略。

### 1.3 事件 -> SSE（`EventToSseAdapter`）
| `AgentEventType` | SSE event | data |
|---|---|---|
| `TEXT_BLOCK_DELTA` | `message` | `TextBlockDeltaEvent.getDelta()` |
| `TOOL_CALL_START`/`TOOL_RESULT_END` | `tool` | 工具名/状态 JSON |
| `HINT_BLOCK` | `hitl` | `HintBlockEvent.getHint()`（HITL 恢复点） |
| `AGENT_RESULT` | `finish` | `AgentResultEvent.getResult().getId()`；usage 经 `getResult().getChatUsage()` |
| `EXCEED_MAX_ITERS`/`ALL_TOOLS_DENIED` | `error` | 提示 |
| Reactor `onError` | `error` | "系统异常，请稍后重试" |

`SseEmitterManager.sendEvent(clientId, eventName, data)` 沿用现有 `ChatMessageServiceImpl` 的 `clientId = "chat_" + sessionId` 约定。

### 1.4 分布式会话（`AgentStateStoreConfig`）
- **PG 是一等后端**（spike 修正方案）：`new PostgresAgentStateStore(aiPostgresDataSource)` 或 `PostgresAgentStateStore.builder(dataSource).schemaName().tableName().createIfNotExist(true).build()`（`agentscope-extensions-postgresql`）。
- 复用 AI 模块现有 ai-postgres `DataSource`（经 `DynamicDataSourceService.getDataSource("ai-postgres")`），与 AI 业务表同库。
- Redis 备选（`-extensions-redis`）留 D5。

### 1.5 工具与 MCP
- `Toolkit toolkit = new Toolkit(); toolkit.registerTool(bean);`（扫描 `io.agentscope.core.tool.Tool` 注解，**非** langchain4j 的 `dev.langchain4j.agent.tool.Tool`）。
- Tool Group：`toolkit.createToolGroup(name, description, activateOnDemand)` + `updateToolGroups(ids, true)`。
- MCP：`toolkit.registerMcpClient(McpClientWrapper)`（AgentScope core MCP，取代 langchain4j-mcp）。

## 2. 契约边界

- **流式（聊天主路，session-centric）** `run(ChatSessionEntity session, SendMessage input) -> Flux<AgentEvent>`：session 提供 `robotId`（-> AppEntity 模板）+ 执行参数快照 + `sessionId`/`tenantId`。
- **同步 one-shot（可选，app-centric）** `call(String appId, input) -> Mono<Msg>`：无 session，按 AppEntity 模板构造。
- **Resume/HITL（session-centric）** `resume(String sessionId, input) -> Flux<AgentEvent>`。
- 运行时参数覆盖规则：`请求参数（SendMessage override） > session 快照`；结构化配置（`subagentSpec`/`toolGroups`/`mcpServerIds`/`middlewareConfig`）从 robot 实时读。

## 3. API/DTO 迁移（硬破坏，D1）

- `CreateApp`/`UpdateApp`（`/v1/ai/apps`）：`kbId`(String)->`kbIds`(List)；drop `workflowId`；新增 `subagentSpec`/`toolIds`/`toolGroups`/`mcpServerIds`/`middlewareConfig`。
- `CreateSession`（`/v1/chat/sessions`）：`kbId`->`kbIds`。
- `SendMessage`（`/v1/chat/sessions/{sessionId}/messages/stream`）：新增可选 override（`temperature`/`maxTokens`/`kbIds`/`llmModelId`）。
- 删 `WorkflowController`/`WorkflowTemplateController`（`/v1/ai/workflows` 整组），无 shim。

## 4. DB 迁移（Liquibase）

走现有拆分文件 `META-INF/db/changelogs/lambda-ai-schema-changelog.xml`（业务表）+ `lambda-ai-vector-changelog.xml`（向量表），不新建聚合文件。

- **删除**：`ai_agent_workflow`（`graph_json`）、`ai_workflow_template`/`ai_workflow_template_version`；`ai_robot.workflow_id`、`ai_chat_session.workflow_id`。
- **`ai_robot` 扩展**：新增 `subagent_spec`/`tool_ids`/`tool_groups`/`mcp_server_ids`/`kb_ids`/`middleware_config`；`kb_id`->`kb_ids`（JSON 数组）。
- **`ai_chat_session` 迁移**：`kb_id`->`kb_ids`；新增 `retrieval_top_k`/`similarity_threshold`；drop `workflow_id`。
- **`ai_knowledge_base`**：新增 `backend_type`（`simple` 托管型本期；`bailian`/`dify`/`ragflow`/`haystack` 外部型 Phase 4）/`store_config`。
- **`ai_workflow_execution` -> `ai_agent_run`**：移除图步字段 `pipeline_id`/`pipeline_version`/`current_step`/`progress`；保留 `user_id`/`input_params`/`output_result`/`status`/`duration_ms`/`tenant_id`/`execution_log`/`started_at`/`completed_at`；`thread_id`/`checkpoint_id` 映射 AgentScope `session_id`。
- 自研向量分表 `ai_vector_store_*` 弃用（S8=pgvector 则复用 ai-postgres 作 `VDBStore`，见 D9）。

## 5. 拆除清单

见方案 §4。`agent` 子树 5983 行砍 70%+。Phase 3 cutover commit 为关键回滚点（无运行时开关，git revert）。

## 6. 测试与门禁

- 无黄金对拍（推倒重来）；新测试套件基于 AgentScope（Phase 1–2 建）。
- 保留 `ServiceRegressionTest`（知识库/RAG 服务回归）。
- 构建：`mvn -pl lambda-fusion-ai test` 全绿；`mvn -pl lambda-fusion-ai compile`（Spotless Palantir；本模块 `spotbugs.skip=true`）通过。
- 门禁：`grep org.bsc.langgraph4j` / `grep dev.langchain4j` 在 `src/main` = 0（Phase 3）。
