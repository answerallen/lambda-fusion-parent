---
name: "lambda-fusion-ai"
description: "面向 RAG/知识库/文档解析分块/向量检索(pgvector)/LLM模型与提供商管理/Agent工作流与执行等需求的模块分析与改造指南。Invoke when 需求涉及 AI 能力或 AI 表结构。"
---

# lambda-fusion-ai 模块 Skill

## 适用范围（何时使用）

- 需要实现/改造 RAG（检索增强生成）、知识库问答、文档上传与解析分块、向量检索、聊天会话、提示词模板、机器人、工作流编排与执行时。
- 需要接入/扩展 LLM 提供商（OpenAI/Ollama/Azure/Anthropic 等）、模型类型（Chat/Embedding/Image）或完善调用容错（限流/熔断/重试）时。
- 需要处理 AI 模块的多租户数据源、Schema 初始化、Liquibase 变更时。

## 模块定位与边界

- 本模块提供 AI 平台能力：知识库（RAG）+ 文档处理链路 + LLM 模型/提供商管理 + Agent 工作流引擎 + API（Controller）对外服务。
- 数据访问采用 MyBatis（Mapper + XML），数据库演进采用 Liquibase。
- 依赖动态数据源与租户能力（来自 lambda-fusion-datasource 与 lambda-cloud-starter-datasource）。

## 自动装配与入口

- 自动装配入口：AiAutoConfiguration（lambda-fusion-ai: com.lambda.fusion.autoconfig.AiAutoConfiguration）
- 模块配置类（扫描/Bean/线程池/容错/Schema 初始化）：AiConfigure（lambda-fusion-ai: com.lambda.fusion.ai.AiConfigure）
- 配置属性（线程池、文档分块、AI 数据源等）：AiProperties（lambda-fusion-ai: com.lambda.fusion.ai.AiProperties）
- 领域常量与枚举（分段策略、文档状态、模型类型、提供商等）：AiConstants（lambda-fusion-ai: com.lambda.fusion.ai.AiConstants）

## 核心子域与代码分布（从需求快速定位）

- **知识库 / 文档 / 向量检索**
  - Controller：DocumentController / KnowledgeBaseController
  - Service：DocumentService / KnowledgeBaseService / RagService
  - Mapper：DocumentMapper / DocumentChunkMapper / VectorRepository
  - 典型链路：上传文档 → 异步解析与切分 → 生成 embedding → 入库向量 → 检索召回 → LLM 生成
- **聊天会话与消息**
  - Controller：ChatSessionController / ChatMessageController
  - Service：ChatSessionService / ChatMessageService / AtomicSessionUpdateService
  - 关注点：历史消息截断（DEFAULT_HISTORY_LIMIT）、并发更新（AtomicSessionUpdateService）
- **LLM 提供商与模型注册**
  - Controller：LlmProviderController / LlmModelController
  - Service：LlmProviderService / LlmModelService
  - 关注点：模型类型（CHAT/EMBEDDING/IMAGE）、密钥存储与脱敏、baseUrl/temperature/maxTokens 等默认值
- **Agent 工作流**
  - Controller：WorkflowController / WorkflowTemplateController
  - Service：WorkflowService / WorkflowExecutionService / WorkflowTemplateService
  - 核心模型：AgentGraph/AgentNode/AgentState（见 commons/agent 包）

## 多租户与 Schema 初始化（关键机制）

- AiConfigure 在应用启动后注册 ApplicationRunner，完成：
  - 默认 AI 数据源 Schema 初始化
  - 遍历已启用租户数据源并为每个租户执行 Schema 初始化
  - 入口见：AiConfigure（com.lambda.fusion.ai.AiConfigure，约 L156-L253）
- 初始化依赖：
  - DatabaseSchemaInitializer（Liquibase 执行器）：com.lambda.fusion.ai.commons.datasource.DatabaseSchemaInitializer
  - TenantDataSourceHelper（查询启用租户数据源）：com.lambda.fusion.ai.commons.datasource.TenantDataSourceHelper
  - DynamicDataSourceService（按数据源名取 DataSource）：lambda-cloud 侧提供

## 容错与并发（LLM 调用稳定性）

- LLM 调用的 Resilience4j 组件由 AiConfigure 内部类提供：
  - RateLimiter / CircuitBreaker / Retry：见 AiConfigure（约 L89-L154）
- Agent 并行线程池与流式线程池由配置项驱动：
  - agentParallelExecutor / agentStreamExecutor：见 AiConfigure（约 L56-L82）

## 数据库与 Liquibase

- Schema 变更：
  - lambda-ai-schema-changelog.xml（lambda-fusion-ai/src/main/resources/META-INF/db/changelogs/）
  - lambda-ai-vector-changelog.xml（lambda-fusion-ai/src/main/resources/META-INF/db/changelogs/）
- 修改/新增表字段时：
  - 同步更新 Liquibase changelog
  - 同步更新对应 Entity/Mapper/XML 与查询对象（Query/DTO）
  - 注意向量存储（pgvector）的维度与 embedding 模型维度一致性

## 常见改造任务指引

- **新增一个对外 API**：优先在对应 Controller 增加端点 → 补 Service 接口与实现 → 补 Mapper 与 XML → 补测试（src/test 中已有 Graph/Workflow 相关测试示例）。
- **新增 LLM 提供商/模型类型**：先扩展枚举与持久化模型 → 在 Service 中实现构建对应 LangChain4j Client 的逻辑 → 为调用链补充容错策略与参数默认值。
- **优化文档分块**：以 AiProperties.DocumentChunkConfig 为收口，保持“默认值 + 校验/纠正”策略一致；涉及 token 逻辑时，避免把字符长度当 token。
- **引入新的租户初始化逻辑**：复用 DatabaseSchemaInitializer 的入口，保持单租户失败不影响其他租户（runSchemaInit 的隔离语义）。

## 关键配置项（最小心智模型）

- 前缀：`lambda.fusion.ai`
- `lambda.fusion.ai.dataSource.*`：AI 数据源启用、默认数据源名、租户前缀（见 AiProperties，约 L34-L43）
- `lambda.fusion.ai.documentChunk.*`：分块大小/重叠/批处理等（见 AiProperties，约 L44-L181）
- `lambda.fusion.ai.agent.*`：并行线程池与子图缓存配置（见 AiProperties，约 L183-L245）
