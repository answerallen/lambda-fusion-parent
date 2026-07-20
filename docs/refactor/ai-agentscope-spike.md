# AgentScope 2.0 Spike 结论（Phase 0）

> 日期：2026-07-19　方法：从 Maven Central 下载 `io.agentscope:agentscope-bom:2.0.0`（2026-07-10 发布）及 core/harness/extension jar，`jar tf` + `javap` 逐类核实 API；GitHub `agentscope-ai/agentscope-java` README + release notes 交叉验证。
> 结论：AgentScope 2.0.0 GA 真实可用，API 与方案 v3 假设基本一致；**两处方案错误已修正**（PG 后端存在；`streamEvents` 返回 `Flux<AgentEvent>` 非 `Flux<Event>`）。

## 1. 智能体执行 API（S1）

`io.agentscope.harness.agent.HarnessAgent`（`agentscope-harness`）implements `io.agentscope.core.agent.Agent, AutoCloseable`：

- 构造：`HarnessAgent.builder() -> HarnessAgent$Builder`，链式 `name/description/sysPrompt/model(Model|String)/toolkit(Toolkit)/maxIters(int)/generateOptions(GenerateOptions)/stateStore(AgentStateStore)/distributedStore(DistributedStore)/defaultSessionId(String)/middleware(MiddlewareBase)/middlewares(List)/hooks(List)/subagents(List<SubagentDeclaration>)/enablePlanMode()/skillRepository/.../build()`。
- 同步：`Mono<Msg> call(List<Msg>, RuntimeContext)`、`call(Msg, RuntimeContext)`、`call(String, RuntimeContext)`。
- 流式：`Flux<AgentEvent> streamEvents(Msg, RuntimeContext)`、`streamEvents(List<Msg>, RuntimeContext)`、`streamEvents(String, RuntimeContext)`。**返回 `Flux<io.agentscope.core.event.AgentEvent>`**（底层 `stream(...)` 返回 `Flux<io.agentscope.core.agent.Event>`，二者不同--聊天主路用 `streamEvents`）。
- Plan Mode：`enterPlanMode(ctx)`/`exitPlanMode(ctx)`/`isPlanModeActive(ctx)`。
- 权限：`setPermissionMode(ctx, PermissionMode)`/`getPermissionMode(...)`。
- 会话：`getRuntimeContext()`/`getStateStore() -> AgentStateStore`/`getDistributedStore()`/`getAgentState()`/`interrupt()`/`close()`。
- skill：`getSkillRepositories()`/`runCuratorOnce()`/`promoteSkill(...)`/`queryAudit(...)`。
- workspace：`getWorkspaceManager()`/`workspaceFor(...)`。
- channel/gateway：`channel(T)`/`gateway()`。

### 28 种 `AgentEventType`（`io.agentscope.core.event.AgentEventType`）
`AGENT_START`/`AGENT_END`/`AGENT_RESULT`/`MODEL_CALL_START`/`MODEL_CALL_END`/`TEXT_BLOCK_START`/`TEXT_BLOCK_DELTA`/`TEXT_BLOCK_END`/`THINKING_BLOCK_START`/`THINKING_BLOCK_DELTA`/`THINKING_BLOCK_END`/`DATA_BLOCK_START`/`DATA_BLOCK_DELTA`/`DATA_BLOCK_END`/`TOOL_CALL_START`/`TOOL_CALL_DELTA`/`TOOL_CALL_END`/`TOOL_RESULT_START`/`TOOL_RESULT_TEXT_DELTA`/`TOOL_RESULT_DATA_DELTA`/`TOOL_RESULT_END`/`EXCEED_MAX_ITERS`/`REQUIRE_USER_CONFIRM`/`REQUIRE_EXTERNAL_EXECUTION`/`USER_CONFIRM_RESULT`/`EXTERNAL_EXECUTION_RESULT`/`REQUEST_STOP`/`SUBAGENT_EXPOSED`/`HINT_BLOCK`/`ALL_TOOLS_DENIED`/`CUSTOM`。

### 事件访问器（SSE 桥接用）
- `AgentEvent`（abstract）：`getType()`/`getId()`/`getCreatedAt()`/`getSource()`/`getMetadata()`。
- `TextBlockDeltaEvent`：`getDelta()`/`getReplyId()`/`getBlockId()`。
- `AgentResultEvent`：`getResult() -> Msg`（`Msg.getTextContent()`/`Msg.getChatUsage() -> ChatUsage`）。
- `ToolCallStartEvent`：`getToolCallName()`/`getToolCallId()`/`getReplyId()`。
- `ToolResultEndEvent`：`getToolCallName()`/`getState()`（`ToolResultState`）。
- `HintBlockEvent`：`getHint()`/`getHintSource()`/`getReplyId()`/`getBlockId()`。
- `ChatUsage`：`getInputTokens()`/`getOutputTokens()`/`getTotalTokens()`/`getCachedTokens()`/`getTime()`。

## 2. DB 驱动 model client（S2）

`agentscope-extensions-model-openai` / `-model-ollama` / `-model-dashscope` / `-model-gemini` / `-model-anthropic`（5 provider，与方案"5 provider 扩展"一致）。每个 provider：
- `XxxChatModel extends io.agentscope.core.model.ChatModelBase`，`static XxxChatModel$Builder builder()`。
- `OpenAIChatModel$Builder`：`apiKey(String)`/`modelName(String)`/`baseUrl(String)`/`stream(boolean)`/`generateOptions(GenerateOptions)`/`endpointPath`/`httpTransport`/`proxy`/`contextWindowSize`/`nativeStructuredOutput`/`build()`。
- `OllamaChatModel$Builder`：`modelName(String)`/`baseUrl(String)`/`defaultOptions(OllamaOptions)`/`httpTransport`/`proxy`/`contextWindowSize`/`build()`（无 apiKey 字段）。
- SPI：`io.agentscope.core.model.spi.ModelProvider`（`OpenAIModelProvider implements ModelProvider`），方案"ModelProvider SPI"扩展点确认。

**关键**：温度/maxTokens **不在 model builder**，而在 `GenerateOptions`（`GenerateOptions$Builder.temperature(Double)/maxTokens(Integer)/topP(Double)/...`），经 `HarnessAgent.builder().generateOptions(...)` 注入。故 model = 连接配置（apiKey/baseUrl/modelName），agent = 运行参数（temperature/maxTokens）。这比 langchain4j 把温度焊在 model 上更干净。

映射自 `LlmModelEntity`（`ai_llm_model`）：`provider`(OPENAI/OLLAMA/DASHSCOPE)/`baseUrl`/`apiKeyEncrypted`（经 `KeyEncryptionService.decrypt`）/`modelName`/`defaultTemperature`/`defaultMaxTokens`/`contextWindow`/`inputTokenPrice`/`outputTokenPrice`/`enabled`/`modelType`/`isDefault`。

## 3. 工具与 MCP（S3/S9）

`io.agentscope.core.tool.Toolkit`（`agentscope-core`）：
- `new Toolkit()` / `new Toolkit(ToolkitConfig)`。
- `registerTool(Object)`：扫描对象上 `@Tool` 注解方法--**注解是 `io.agentscope.core.tool.Tool`，非 langchain4j 的 `dev.langchain4j.agent.tool.Tool`**（S9 迁移成本=改 import 包名 + 校验签名兼容）。
- `registerAgentTool(AgentTool)`/`registerSchemas(List<ToolSchema>)`/`getToolSchemas()`。
- Tool Group：`createToolGroup(name, description, activateOnDemand)`/`createToolGroup(name, desc, onDemand, ToolGroupScope)`/`updateToolGroups(List<String>, activate)`/`removeToolGroups(List)`。方案"Tool Group 动态激活"确认。
- MCP：`registerMcpClient(McpClientWrapper) -> Mono<Void>`/`removeMcpClient(String)`（AgentScope core MCP，取代 langchain4j-mcp `DefaultMcpClient`）。

## 4. `Flux<AgentEvent>` -> SSE（S4）

现有 `ChatMessageServiceImpl` 用 `com.lambda.cloud.sse.SseEmitterManager.sendEvent(clientId, eventName, data)`，`clientId = "chat_" + sessionId`，事件名 `message`/`finish`/`error`。`EventToSseAdapter` 订阅 `Flux<AgentEvent>`，按 §1 事件访问器映射（见 design §1.3 表）。错误经 Reactor `Flux.doOnError`/`subscribe(onError)` 推 `error` 事件。

## 5. RuntimeContext 与多租户（S6 前置）

`io.agentscope.core.agent.RuntimeContext`：
- `static RuntimeContext$Builder builder()`：`sessionId(String)`/`userId(String)`/`agentState(AgentState)`/`put(String, Object)`/`putAll(Map)`/`put(Class<T>, T)`/`toolExecutionContext(...)`/`build()`。
- 多租户：`ctx.put("tenantId", tenantId)`；会话/记忆按 `sessionId`（= 聊天 `sessionId`）隔离。

## 6. 分布式会话后端（S6）--**修正方案错误**

方案 v3 §2/§5.5/D5/D9 反复声明"无独立 PG 后端--PG 仅经 Mysql 扩展方言接入"。**核实为错误**：

- BOM 列出独立 artifact `agentscope-extensions-postgresql`（`io.agentscope:agentscope-extensions-postgresql:2.0.0`）。
- README："Distributed Deployment: True distributed session and memory management (**Redis / MySQL / PostgreSQL** / OSS / COS) with cross-replica session recovery."
- 该 jar 含 `io.agentscope.extensions.postgresql.state.PostgresAgentStateStore implements io.agentscope.core.state.AgentStateStore`：
  - 构造：`new PostgresAgentStateStore(javax.sql.DataSource)` / `(DataSource, boolean)` / `(DataSource, schemaName, tableName, boolean)`。
  - Builder：`PostgresAgentStateStore.builder(DataSource).schemaName(String).tableName(String).createIfNotExist(boolean).build()`。
  - API：`save(namespace, sessionId, agentId, State)`/`get(namespace, sessionId, agentId, Class<T>) -> Optional<T>`/`getList(...)`/`exists(namespace, sessionId)`/`listSessionIds(namespace)`/`delete(namespace, sessionId)`/`truncateAllSessions()`/`close()`。
  - 含 `PostgresDistributedStore`/`PostgresSnapshotSpec`/`PostgresSandboxExecutionGuard` 等（snapshot/sandbox 一并覆盖）。
- 还有 `agentscope-extensions-mysql`/`-redis`/`-oss`/`-cos` 并列。

**结论**：D5/D9 应为"PG 经 `agentscope-extensions-postgresql` 一等接入（复用 ai-postgres `DataSource`）"，**非**"经 mysql 扩展方言"。S8=pgvector 时，向量留 PG + 会话/业务表留 ai-postgres + AgentStateStore 用 `PostgresAgentStateStore(aiPostgresDataSource)`，全在同一 PG，最小改动。方案文档需据此修正（已在本会话修正）。

## 7. 会话状态与记忆

- `io.agentscope.core.state.AgentStateStore`（SPI）：`InMemoryAgentStateStore`/`JsonFileAgentStateStore`（HarnessAgent 默认，单机）/ `PostgresAgentStateStore`/`MysqlAgentStateStore`（`-extensions-mysql`）/ Redis（`-extensions-redis`）。
- `AgentState`（`io.agentscope.core.state.AgentState`）：`PlanModeContextState` 等。
- `Msg implements State`：消息本身就是可持久化状态。

## 8. RAG（S8，Phase 2 已核实落地）

`agentscope-extensions-rag-simple`（托管型，本期）+ `-rag-bailian`/`-rag-dify`/`-rag-ragflow`/`-rag-haystack`（外部型，Phase 4）。core 含 `io.agentscope.core.rag.Knowledge`（接口：`addDocuments(List<Document>) -> Mono<Void>` / `retrieve(String, RetrieveConfig) -> Mono<List<Document>>`）+ `KnowledgeRetrievalTools`（把 KB 检索暴露为 agent 工具）。

**已核实（Phase 2 落地时 javap）**：
- `SimpleKnowledge.builder().embeddingModel(EmbeddingModel).embeddingStore(VDBStoreBase).build()`。
- **5 个 `VDBStore` 适配器：`MilvusStore`/`ElasticsearchStore`/`QdrantStore`/`InMemoryStore`/`PgVectorStore`**--**含 pgvector**。故 D9=S8=pgvector -> 复用 ai-postgres 作 `VDBStore`，最小改动。
- `PgVectorStore.builder().jdbcUrl(String).username(String).password(String).schema(String).tableName(String).dimensions(int).distanceType(L2/INNER_PRODUCT/COSINE).connectionTimeoutMs(long).build()` throws `VectorStoreException`；**取 JDBC 凭据（非 DataSource）**，从 `spring.datasource.dynamic.datasource.<name>.url/username/password` 解析。`implements AutoCloseable`。
- embedding：`EmbeddingModel` 接口（`embed(ContentBlock) -> Mono<double[]>`/`getModelName()`/`getDimensions()`）；实现 `OpenAITextEmbedding`/`DashScopeTextEmbedding`（builder `apiKey/modelName/dimensions/baseUrl`）/`OllamaTextEmbedding`（builder `baseUrl/modelName/dimensions`，无 apiKey）。
- `KnowledgeRetrievalTools(Knowledge)` 的 `retrieveKnowledge(String, Integer, Agent, RuntimeContext)` 已 `@Tool` 注解（name=`retrieve_knowledge`，description="Retrieve relevant documents..."，params `@ToolParam`；Agent/RuntimeContext 为 state-injected），经 `Toolkit.registerTool(new KnowledgeRetrievalTools(knowledge))` 注册即成 agent 工具。
- readers：`PDFReader`/`WordReader`/`TextReader`/`TikaReader`/`ImageReader`/`ExternalApiReader`（自有，取代 langchain4j-document-parser）。
- 落地：`KnowledgeFactory`（`KnowledgeBaseEntity` -> `SimpleKnowledge`，Caffeine 缓存），按 session `kbId` 在 `AgentRuntimeServiceImpl.buildToolkit` 注册检索工具。单 KB 本期；多 KB（`kbIds`）随 Phase 3 字段重命名。

## 9. Spring Boot 集成

`agentscope-spring-boot-starter` 含 `io.agentscope.spring.boot.AgentscopeAutoConfiguration`（注册于 `META-INF/spring/...AutoConfiguration.imports`），提供 `agentscopeMemory()`/`agentscopeToolkit()`/`agentscopeReActAgent(Model, Memory, Toolkit, AgentscopeProperties)` Bean；`AgentscopeProperties`（prefix 待核实，含 `getAgent()`/`getModel()`）。本模块**不直接依赖**该 starter 的自动装配（自行经 `AiConfigure` 条件装配 `HarnessAgent`），仅依赖 `agentscope-harness` + 扩展 jar。

## 10. middleware（横切能力单一集成点）

`io.agentscope.core.middleware.MiddlewareBase`（`agentscope-core`），五阶段 hook：`onAgent`(`AgentInput`)/`onReasoning`(`ReasoningInput`)/`onActing`(`ActingInput`)/`onModelCall`(`ModelCallInput`)/`onSystemPrompt`。内置 `TaskReminderMiddleware`/`OtelTracingMiddleware`/`GracefulShutdownMiddleware`/`DynamicSkillMiddleware`。经 `HarnessAgent.builder().middleware(...)/middlewares(...)` 注入。Resilience4j 熔断/限流/重试 + `CostCalculator` 记账 + OTel trace + prompt 渲染全收敛到此（方案 §3 结论 2 落地）。

## 附录 A：BOM 真实 artifact 清单（已核实）

core：`agentscope-core`/`agentscope-harness`/`agentscope`（聚合）。
spring-boot-starter：`agentscope-spring-boot-starter`/`-openai-`/`-dashscope-`/`-gemini-`/`-anthropic-`/`-chat-completions-web-`/`-admin-`/`-agui-`/`-a2a-`/`-nacos-`。
model 扩展：`-extensions-model-openai`/`-gemini`/`-anthropic`/`-dashscope`/`-ollama`。
RAG：`-extensions-rag-simple`/`-bailian`/`-dify`/`-ragflow`/`-haystack`。
会话/存储后端：`-extensions-redis`/`-mysql`/`-postgresql`/`-oss`/`-cos`。
sandbox：`-extensions-sandbox-kubernetes`/`-agentrun`/`-daytona`/`-e2b`。
memory：`-extensions-mem0`/`-reme`/`-memory-bailian`。
scheduler：`-extensions-scheduler-xxl-job`/`-quartz`。
channel：`-extensions-channel-dingtalk`/`-feishu`/`-wecom`/`-github`/`-gitlab`。
其它：`-extensions-a2a-client`/`-a2a-server`/`-agui`/`-agent-protocol`/`-studio`/`-higress`/`-skill-git-repository`/`-skill-mysql-repository`/`-skill-postgresql-repository`/`-training`/`-nacos-a2a`/`-nacos-prompt`/`-nacos-skill`。Quarkus/Micronaut 扩展各一。

## 附录 B：spike 操作可复现

```bash
B=https://repo1.maven.org/maven2/io/agentscope
curl -sS -o core.jar        "$B/agentscope-core/2.0.0/agentscope-core-2.0.0.jar"
curl -sS -o harness.jar     "$B/agentscope-harness/2.0.0/agentscope-harness-2.0.0.jar"
curl -sS -o pg.jar          "$B/agentscope-extensions-postgresql/2.0.0/agentscope-extensions-postgresql-2.0.0.jar"
curl -sS -o model-openai.jar "$B/agentscope-extensions-model-openai/2.0.0/agentscope-extensions-model-openai-2.0.0.jar"
CP=core.jar:harness.jar:pg.jar:model-openai.jar
javap -classpath "$CP" io.agentscope.harness.agent.HarnessAgent
javap -classpath "$CP" 'io.agentscope.harness.agent.HarnessAgent$Builder'
javap -classpath "$CP" io.agentscope.extensions.postgresql.state.PostgresAgentStateStore
```

## 8a. RAG ingestion 模型与设计张力（Phase 3 落地前核实）

**ingestion 流水线（spike 核实）**：
- `Reader.read(ReaderInput) -> Mono<List<Document>>` 负责解析+分块；`ReaderInput.fromString/fromFile/fromPath`；Reader 构造 `(int chunkSize, SplitStrategy, int overlap)`，实现 `TextReader`/`PDFReader`/`WordReader`/`TikaReader`（均 extends `AbstractChunkingReader`）。`SplitStrategy` = CHARACTER/PARAGRAPH/TOKEN/SEMANTIC（fusion KB 的 FIXED/PARAGRAPH/SENTENCE/SLIDING_WINDOW 需映射）。
- `SimpleKnowledge.builder()` 仅 `embeddingModel`+`embeddingStore`，**不含 reader/chunker**--分块在 Reader 侧，`addDocuments(List<Document>)` 只 embed+store。
- `Document` 包 `DocumentMetadata(ContentBlock, docId, chunkId, payload)`；`getContentText()` 取文本，`getContent()` 取 `ContentBlock`（`EmbeddingModel.embed(ContentBlock)` 的入参）。
- `EmbeddingModel.embed(ContentBlock) -> Mono<double[]>`，**仅单条，无批量**（旧 langchain4j 有 `embedAll` 批量）。

**删除 API 缺口（阻塞 storage 迁移）**：
- `VDBStoreBase.delete(String)` 仅按单 id 删；`SearchDocumentDto` 仅向量检索（queryEmbedding+limit+scoreThreshold+vectorName），**无按 docId 过滤/删除**。故迁移到 SimpleKnowledge 的 PgVectorStore 后，"删整篇文档的所有 chunk" 无原生 API（需自建：在 ai_document_chunk 记 chunkId 后逐条删，或扩展 PgVectorStore 加 delete-by-doc SQL）。

**两条迁移路径与张力**：
1. **保 VectorRepository 存储**（自研 pgvector 分表，含 deleteByDocumentId）：DocumentProcessor 改 AgentScope Reader 解析+分块 + AgentScope EmbeddingModel 逐块 embed -> DocumentChunkEntity -> VectorRepository。**清零 langchain4j 但 embed 退化为逐块（N 次 HTTP，大文档慢）**；删除路径不变（安全）。
2. **迁 SimpleKnowledge PgVectorStore**：`SimpleKnowledge.addDocuments`（可能内部批量 embed）+ 需解决 delete-by-doc（扩展 VDBStore 或记 chunkId 逐删）。**彻底删自研 pgvector 但删除语义需设计**。

**结论**：DocumentProcessor 重写非机械替换，有批量 embed + 删除模型两个设计点需先定。建议先定路径（1 保存储清 langchain4j，还是 2 彻底迁），再实施。`EmbeddingModelManager` 可随 DocumentProcessor 一并处理（DocumentProcessor 改用 `KnowledgeFactory.get(kbId).getEmbeddingModel()` 后，EmbeddingModelManager 仅剩 `LlmModelServiceImpl.clearCache` 用，可删 + clearCache 改 `knowledgeFactory.invalidateAll()`）。
