# Lambda Fusion AI 模块

`lambda-fusion-ai` 是一个面向 Spring Boot 的 AI 能力模块，当前代码已经不只是传统的 RAG 知识库问答，还包含：

- 知识库与文档处理
- LLM 提供商与模型管理
- AI 应用/机器人管理
- 对话会话与 SSE 流式消息
- Prompt 模板管理
- MCP Server 与工具装载
- Agent 工作流编排、模板、执行、恢复与追踪
- 多租户数据源与 AI Schema 自动初始化

本文档以 `src/main/java` 和 `src/main/resources` 中的实际代码为准，覆盖旧版 README 中已经过时的接口、配置和表结构描述。

## 1. 当前代码范围

### 1.1 已落地能力

- Spring Boot 自动装配：`AiAutoConfiguration -> AiConfigure`
- RAG 检索：向量检索 + 关键词检索，使用 RRF 做混合召回
- 文档处理：上传到 OSS，异步解析、切分、向量化、入库
- 对话能力：基于会话上下文的流式聊天，支持知识库或工作流模式
- AI 应用：`/v1/ai/apps` 与 `/v1/ai/robots` 指向同一套应用管理
- LLM 管理：提供商、模型注册、默认模型、成本统计
- Prompt 模板：分类管理、系统模板、变量渲染
- MCP 工具：支持 STDIO 和 HTTP Streamable 两种传输
- 工作流引擎：工作流定义、执行、流式执行、checkpoint 恢复、执行状态查询
- 工作流模板：创建、发布、废弃、复制、回滚、导入导出、校验
- 多租户：默认数据源与租户数据源下自动执行 AI Liquibase 迁移

### 1.2 和旧文档相比的重要变化

- 旧文档里的 `RobotService`、`CreateRobot` 已不存在，当前统一走 `AppsService` / `AppsController`
- 对话消息当前公开接口只有流式模式，没有单独的同步发送接口
- RAG 不再依赖固定的 `ai_vector_store` 单表，而是按维度写入 `ai_vector_store_<dimension>` 分表
- 当前真实生效的 AI 配置核心来自 `AiProperties`，不是旧文档里的 `embedding.*`、`vector-name` 等字段
- 工作流图定义字段已是 `source/target + conditionType/conditionExpression`，不是旧版的 `sourceId/targetId/condition`

## 2. 代码结构

`src/main/java/com/lambda/fusion/ai` 主要分层如下：

| 包 | 作用 |
| --- | --- |
| `controller` | REST 接口层 |
| `service` / `service.impl` | 业务服务与实现 |
| `mapper` | MyBatis Mapper 与向量仓储访问 |
| `model` | DTO / VO / 请求响应模型 |
| `model.entity` | 数据库实体 |
| `commons.agent` | Agent 状态、节点、条件评估器、图工厂 |
| `commons.support` | 文档处理、模型工厂、Embedding、MCP、安全等支撑组件 |
| `commons.datasource` | 多租户数据源切换与 Schema 初始化 |

## 3. 核心执行链路

### 3.1 知识库文档链路

1. `DocumentController.upload`
2. `DocumentServiceImpl.uploadDocument`
3. 文件做 SHA-256 去重校验
4. 文件上传到 OSS，记录 `ai_document`
5. `DocumentProcessor.processDocument(...)` 异步执行
6. 从 OSS 拉取文件内容并解析
7. 使用 `DocumentSplitters.recursive(chunkSize, chunkOverlap)` 切分
8. 使用知识库绑定的 Embedding 模型批量 `embedAll`
9. 写入 `ai_document_chunk` 和对应维度的向量分表
10. 更新文档与知识库统计

### 3.2 对话链路

- 会话创建：`ChatSessionController.create`
- 消息发送：`ChatMessageController.startStreamChat`
- SSE 订阅：`GET /v1/chat/sessions/{sessionId}/messages/stream`
- 当会话绑定 `workflowId` 时走 `WorkflowExecutionService.executeStream`
- 否则走 `RagService.retrieve + RagService.streamChat`
- 消息入库后，原子更新会话 token / cost / messageCount

### 3.3 工作流链路

- 工作流定义存储在 `ai_agent_workflow.graph_json`
- `WorkflowExecutionServiceImpl` 负责：
  - 同步执行
  - 流式执行
  - checkpoint 恢复
  - 执行历史查询
  - 执行状态查询
  - token / cost 结算
- checkpoint 使用 `MemorySaver`，属于进程内内存状态

## 4. 当前模块能力拆分

### 4.1 知识库与文档

- 知识库字段包含：
  - `embeddingModel`
  - `embeddingDimension`
  - `chunkSize`
  - `chunkOverlap`
  - `chunkStrategy`
  - `retrievalTopK`
  - `similarityThreshold`
- 文档上传白名单：
  - `pdf`
  - `txt`
  - `doc`
  - `docx`
  - `xls`
  - `xlsx`
  - `ppt`
  - `pptx`
  - `md`
  - `json`
  - `xml`
  - `csv`
- 文档处理状态：`PENDING / PROCESSING / COMPLETED / FAILED`

注意：

- `chunkStrategy` 当前会被保存到知识库，但 `DocumentProcessor` 现阶段统一使用递归切分器，尚未按 `FIXED / PARAGRAPH / SENTENCE / SLIDING_WINDOW` 分支切换实现
- 控制器上传路径默认走 OSS 存储；处理器虽然保留了 `LOCAL` 分支，但当前上传实现实际写入的是 `OSS`

### 4.2 LLM 提供商与模型

- 提供商管理：`/v1/llm-model-providers`
- 模型管理：`/v1/llm-models`
- 模型类型：`CHAT / EMBEDDING / IMAGE`
- API Key 入库前会经过 `AesKeyEncryptionService` 做 AES-256-GCM 加密
- `ChatModelFactory` 使用 Caffeine 缓存 ChatModel / StreamingChatModel，TTL 为 1 小时，最大 100 条

当前代码实际运行时支持情况：

- ChatModel：`OPENAI`、`OLLAMA`
- StreamingChatModel：`OPENAI`、`OLLAMA`
- EmbeddingModel：当前仅实现 `OPENAI`

说明：

- Liquibase 已初始化多种提供商与模型类型关系
- 但“支持被注册”不等于“运行时已实现”
- 生产接入前应以 `ChatModelFactory` 和 `EmbeddingModelManager` 的实现为准

### 4.3 AI 应用 / 机器人

控制器：

- `POST /v1/ai/apps`
- `PUT /v1/ai/apps`
- `GET /v1/ai/apps/{id}`
- `GET /v1/ai/apps`
- `DELETE /v1/ai/apps/{id}`

兼容路径：

- 同一控制器也挂在 `/v1/ai/robots`

应用实体实际绑定能力：

- `llmModelId`
- `kbId`
- `workflowId`
- `systemPrompt`
- `temperature`
- `maxTokens`
- `retrievalTopK`
- `similarityThreshold`
- `showCitation`
- `welcomeMessage`
- `suggestedQuestions`
- `enableFollowUp`
- `publishChannels`

### 4.4 Prompt 模板

接口：

- `POST /v1/prompt-templates`
- `GET /v1/prompt-templates`
- `GET /v1/prompt-templates/{id}`
- `PUT /v1/prompt-templates/{id}`
- `DELETE /v1/prompt-templates/{id}`
- `GET /v1/prompt-templates/system`
- `POST /v1/prompt-templates/{templateId}/render`

代码行为：

- 模板渲染使用 LangChain4j `PromptTemplate`
- 占位符语法为 `{{variable}}`
- 系统模板不允许修改或删除
- RAG 默认会优先按知识库 `category` 加载系统模板，找不到时回退到 `system_rag_default`

### 4.5 MCP Server 与工具

接口：

- `POST /v1/mcp/servers`
- `GET /v1/mcp/servers`
- `GET /v1/mcp/servers/{id}`
- `PUT /v1/mcp/servers/{id}`
- `DELETE /v1/mcp/servers/{id}`
- `POST /v1/mcp/servers/{id}/connect/test`
- `GET /v1/mcp/servers/tools`
- `POST /v1/mcp/servers/tools/refresh`

支持的传输类型：

- `STDIO`
- `HTTP_STREAMABLE`

工具装载规则：

- 本地 Spring Bean 上的 `@Tool`
- 远程 MCP 工具
- 同名冲突时本地 `@Tool` 优先

### 4.6 工作流与模板

工作流接口：

- `POST /v1/ai/workflows`
- `GET /v1/ai/workflows`
- `GET /v1/ai/workflows/{id}`
- `POST /v1/ai/workflows/{id}/execute`
- `POST /v1/ai/workflows/{id}/execute/stream`
- `POST /v1/ai/workflows/{id}/resume`
- `GET /v1/ai/workflows/executions/{executionId}`
- `GET /v1/ai/workflows/{id}/threads/{threadId}/status`
- `GET /v1/ai/workflows/{id}/executions`

工作流模板接口：

- `POST /v1/ai/workflow-templates`
- `PUT /v1/ai/workflow-templates/{id}`
- `DELETE /v1/ai/workflow-templates/{id}`
- `GET /v1/ai/workflow-templates/{id}`
- `GET /v1/ai/workflow-templates/code/{code}`
- `GET /v1/ai/workflow-templates/code/{code}/version/{version}`
- `GET /v1/ai/workflow-templates/list`
- `GET /v1/ai/workflow-templates/system`
- `GET /v1/ai/workflow-templates/categories`
- `POST /v1/ai/workflow-templates/{id}/publish`
- `POST /v1/ai/workflow-templates/{id}/deprecate`
- `POST /v1/ai/workflow-templates/{id}/copy`
- `GET /v1/ai/workflow-templates/{templateId}/versions`
- `POST /v1/ai/workflow-templates/{templateId}/rollback`
- `GET /v1/ai/workflow-templates/{id}/export`
- `POST /v1/ai/workflow-templates/import`
- `POST /v1/ai/workflow-templates/validate`

## 5. 工作流图定义

### 5.1 GraphDefinition

当前 JSON 结构以以下模型为准：

```json
{
  "entryPoint": "node_a",
  "nodes": [
    {
      "id": "node_a",
      "type": "LLM_PROCESSOR",
      "properties": {}
    }
  ],
  "edges": [
    {
      "source": "node_a",
      "target": "node_b",
      "conditionType": "spel",
      "conditionExpression": "..."
    }
  ]
}
```

### 5.2 当前内置节点类型

| 节点类型 | 说明 |
| --- | --- |
| `LLM_PROCESSOR` | 通用 LLM 推理节点，支持工具调用与流式输出 |
| `TOOL_EXECUTOR` | 执行本地 `@Tool` 或 MCP 工具 |
| `CONDITIONAL` | 按条件表达式选择分支 |
| `PARALLEL` | 并行执行多个分支，汇总后跳转 joinNode |
| `LOOP` | `while / doWhile / for` 循环节点 |
| `REACT_AGENT` | ReAct 风格专家节点 |
| `SUPERVISOR_AGENT` | 多智能体路由主管节点 |
| `SUBGRAPH` | 嵌入执行另一个工作流 |
| `AGENT_AGGREGATOR` | 聚合多专家或并行分支结果 |

### 5.3 当前条件评估器

从测试和源码可见，至少包含：

- `spel`
- `js`
- `confidence`
- `retrievalRelevance`
- `toolResult`

### 5.4 内置示例

资源路径：

- `src/main/resources/examples/workflows/multi-agent-code-review.json`
- `src/main/resources/examples/workflows/multi-agent-solution-design.json`

这两个示例分别演示：

- `SUPERVISOR_AGENT + REACT_AGENT + AGENT_AGGREGATOR`
- `PARALLEL + REACT_AGENT + AGENT_AGGREGATOR`

## 6. 配置说明

### 6.1 自动装配

引入依赖后，Spring Boot 会通过：

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `AiAutoConfiguration`

自动注册 `AiConfigure`。

Maven 依赖：

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-ai</artifactId>
    <version>2026.1.1-SNAPSHOT</version>
</dependency>
```

### 6.2 当前代码真实生效的 AI 配置项

```yaml
lambda:
  fusion:
    ai:
      datasource:
        enabled: true
        name: ai-postgres
        tenant-prefix: ai-tenant-

      document:
        oss-client-name: default

      document-chunk:
        default-chunk-size: 500
        default-chunk-overlap: 50
        max-file-size: 10485760
        batch-size: 100
        vector-batch-size: 200

      agent:
        parallel-executor:
          core-pool-size: 5
          max-pool-size: 20
          queue-capacity: 100
          keep-alive-seconds: 60
          thread-name-prefix: agent-parallel-
        subgraph:
          async-timeout: 30000
          cache-enabled: true
          cache-max-size: 100

      security:
        encryption-key: ${AI_ENCRYPTION_KEY:}
```

说明：

- `security.encryption-key` 由 `AesKeyEncryptionService` 直接通过 `@Value` 读取
- 生产环境未配置该值会拒绝启动
- `document-chunk.default-chunk-overlap` 会在启动时自动校验并修正不合理值

### 6.3 数据源配置

模块要求 AI 数据源是 PostgreSQL，并已安装 `pgvector`。

典型配置：

```yaml
spring:
  datasource:
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          driver-class-name: com.mysql.cj.jdbc.Driver
          url: jdbc:mysql://localhost:3306/lambda_fusion
          username: root
          password: password
        ai-postgres:
          driver-class-name: org.postgresql.Driver
          url: jdbc:postgresql://localhost:5432/lambda_ai?currentSchema=public
          username: postgres
          password: password
```

### 6.4 旧配置键名兼容提示

以下写法出现在历史文档或示例文件中，但不是当前 `AiProperties` 的正式绑定字段：

- `lambda.fusion.ai.datasource.default-name`
- `lambda.fusion.ai.datasource.vector-name`
- `lambda.fusion.ai.embedding.*`

当前请统一按本 README 的配置项使用。

## 7. 数据库对象

### 7.1 业务表

| 表名 | 说明 |
| --- | --- |
| `ai_knowledge_base` | 知识库 |
| `ai_document` | 文档 |
| `ai_document_chunk` | 文档块 |
| `ai_llm_model` | LLM 模型配置 |
| `ai_chat_session` | 对话会话 |
| `ai_chat_message` | 对话消息 |
| `ai_workflow_execution` | 工作流执行记录 |
| `ai_prompt_template` | Prompt 模板 |
| `ai_agent_workflow` | 工作流定义 |
| `ai_workflow_template` | 工作流模板 |
| `ai_workflow_template_version` | 工作流模板版本历史 |
| `ai_robot` | AI 应用/机器人 |
| `ai_mcp_server` | MCP Server 配置 |
| `ai_llm_provider` | 提供商 |
| `ai_llm_model_type_provider` | 提供商支持的模型类型 |

### 7.2 向量分表

Liquibase 会创建 `create_vector_table(dimension)` 存储过程，并默认执行：

- `ai_vector_store_768`
- `ai_vector_store_1536`
- `ai_vector_store_2048`
- `ai_vector_store_4096`

运行时代码中的 `VectorDimensionProcessor` 当前支持维度常量是：

- `768`
- `1536`
- `3072`
- `4096`

因此如果要稳定支持 3072 维模型，建议补充执行：

```sql
SELECT create_vector_table(3072);
```

### 7.3 向量检索策略

- 语义检索：`vectorRepository.searchSimilar(...)`
- 关键词检索：`vectorRepository.searchKeyword(...)`
- 结果融合：RRF `reciprocalRankFusion(...)`

## 8. 快速开始

### 8.1 注册模型

```http
POST /v1/llm-models
Content-Type: application/json

{
  "name": "openai-gpt-4o-mini",
  "displayName": "OpenAI GPT-4o mini",
  "modelType": "CHAT",
  "provider": "OPENAI",
  "baseUrl": "https://api.openai.com/v1",
  "apiKeyEncrypted": "sk-***",
  "modelName": "gpt-4o-mini",
  "defaultTemperature": 0.7,
  "defaultMaxTokens": 2048
}
```

### 8.2 创建知识库

```http
POST /v1/knowledge-bases
Content-Type: application/json

{
  "name": "技术文档库",
  "description": "研发内部文档",
  "category": "tech",
  "embeddingModel": "text-embedding-3-small",
  "embeddingDimension": 1536,
  "chunkSize": 500,
  "chunkOverlap": 50,
  "chunkStrategy": "FIXED",
  "retrievalTopK": 5,
  "similarityThreshold": 0.7
}
```

### 8.3 上传文档

```http
POST /v1/knowledge-bases/{kbId}/documents
Content-Type: multipart/form-data

file=@architecture.pdf
```

### 8.4 创建 AI 应用

```http
POST /v1/ai/apps
Content-Type: application/json

{
  "name": "技术助手",
  "description": "绑定知识库的问答机器人",
  "llmModelId": "model-id",
  "kbId": "kb-id",
  "enabled": true,
  "isPublic": false,
  "showCitation": true
}
```

### 8.5 创建会话

```http
POST /v1/chat/sessions
Content-Type: application/json

{
  "title": "技术咨询",
  "robotId": "app-id"
}
```

`robotId` 存在时，服务会自动把应用上的：

- `llmModelId`
- `systemPrompt`
- `kbId`
- `workflowId`

挂载到会话。

### 8.6 流式对话

先建立 SSE：

```http
GET /v1/chat/sessions/{sessionId}/messages/stream
Accept: text/event-stream
```

再发起聊天：

```http
POST /v1/chat/sessions/{sessionId}/messages/stream
Content-Type: application/json

{
  "content": "总结一下该系统的工作流恢复机制"
}
```

### 8.7 执行工作流

```http
POST /v1/ai/workflows/{workflowId}/execute
Content-Type: application/json

{
  "kbId": "kb-id",
  "llmModelId": "model-id",
  "inputParams": {
    "question": "请给出方案设计建议"
  },
  "traceEnabled": true,
  "checkpointEnabled": true,
  "maxIterations": 20
}
```

### 8.8 恢复工作流

```http
POST /v1/ai/workflows/{workflowId}/resume
Content-Type: application/json

{
  "threadId": "thread-id",
  "checkpointId": "checkpoint-id",
  "message": "继续执行，并补充数据库约束方案",
  "inputParams": {
    "operatorDecision": "approve"
  }
}
```

## 9. 错误码

AI 模块错误码使用 `30000-30999` 段，主要分组如下：

| 范围 | 含义 |
| --- | --- |
| `30000-30099` | 知识库 |
| `30100-30199` | 文档 |
| `30200-30299` | 会话 |
| `30300-30399` | 消息 |
| `30400-30499` | LLM 模型 |
| `30500-30599` | RAG / Prompt |
| `30600-30699` | 向量存储 |
| `30750-30779` | 机器人 / 工作流 |
| `30800-30899` | 数据源 |
| `30950-30979` | MCP |

典型枚举定义见：

- `com.lambda.fusion.ai.commons.exception.AiErrorCode`

## 10. 扩展点

### 10.1 自定义本地工具

在 Spring Bean 中声明 `@Tool` 方法即可被 `AgentToolProvider` 自动扫描：

```java
@Component
public class WeatherTools {

    @Tool(name = "queryWeather")
    public String queryWeather(String city) {
        return "ok";
    }
}
```

### 10.2 自定义节点

实现 `AgentNode` 并注册为 Spring Bean，`AgentGraphFactory` 会按 `getName()` 返回值匹配 `NodeDefinition.type`。

### 10.3 自定义条件评估器

实现 `ConditionEvaluator` 并提供唯一的 `getType()`，即可被条件边与 `CONDITIONAL` 节点复用。

## 11. 现阶段实现限制

- 工作流 checkpoint 使用 `MemorySaver`，默认不是持久化存储，服务重启后内存状态不会保留
- Chat/StreamingChatModel 当前只实现 `OPENAI` 和 `OLLAMA`
- EmbeddingModel 当前只实现 `OPENAI`
- `chunkStrategy` 已入库，但文档切分当前仍统一使用递归切分器
- `application-example.yml` 中仍保留了部分历史配置示例，阅读时请以本 README 和源码为准

## 12. 源码定位

建议从以下入口阅读：

- 自动装配：`src/main/java/com/lambda/fusion/autoconfig/AiAutoConfiguration.java`
- 总配置：`src/main/java/com/lambda/fusion/ai/AiConfigure.java`
- 属性定义：`src/main/java/com/lambda/fusion/ai/AiProperties.java`
- 文档处理：`src/main/java/com/lambda/fusion/ai/commons/support/processor/DocumentProcessor.java`
- RAG：`src/main/java/com/lambda/fusion/ai/service/impl/RagServiceImpl.java`
- 会话消息：`src/main/java/com/lambda/fusion/ai/service/impl/ChatMessageServiceImpl.java`
- 工作流执行：`src/main/java/com/lambda/fusion/ai/service/impl/WorkflowExecutionServiceImpl.java`
- 图工厂：`src/main/java/com/lambda/fusion/ai/commons/agent/factory/AgentGraphFactory.java`
- MCP：`src/main/java/com/lambda/fusion/ai/service/impl/McpServerServiceImpl.java`
- 数据库变更：`src/main/resources/META-INF/db/changelogs`
