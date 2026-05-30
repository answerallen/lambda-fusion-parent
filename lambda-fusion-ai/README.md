# 🤖 Lambda Fusion AI 模块

`lambda-fusion-ai` 是 Lambda Fusion 体系中的 AI 能力模块，面向 Spring Boot 提供知识库、RAG、流式对话、AI 应用、MCP 工具接入，以及 Agent 工作流编排与执行能力。

> 📌 面向对象：需要在业务系统中集成知识库问答、工作流编排、模型管理与 MCP 工具调用能力的 Spring Boot 应用。


## 1. 🧭 模块概览

### 1.1 核心能力

| 能力域 | 当前实现 |
| --- | --- |
| 自动装配 | `AiAutoConfiguration -> AiConfigure` |
| 知识库 | 创建、更新、删除、分页、租户隔离 |
| 文档处理 | 上传到 OSS，异步解析、切分、向量化、入库 |
| RAG | 向量检索 + 关键词检索，使用 RRF 做混合召回 |
| 对话 | 基于会话上下文的 SSE 流式聊天 |
| AI 应用 | `/v1/ai/apps` 应用管理 |
| LLM 管理 | 提供商、模型注册、默认模型、调用统计、成本统计 |
| Prompt 模板 | 分类管理、系统模板、变量渲染 |
| MCP | STDIO / HTTP Streamable 接入，统一工具装载 |
| 工作流 | 定义、执行、流式执行、恢复、状态查询、执行历史 |
| 工作流模板 | 创建、发布、废弃、复制、回滚、导入导出、校验 |
| 多租户 | 默认数据源与租户数据源自动执行 AI Liquibase 迁移 |

## 2. 🗂️ 代码结构

主包：`src/main/java/com/lambda/fusion/ai`

| 包 | 作用 |
| --- | --- |
| `controller` | REST 接口层 |
| `service` / `service.impl` | 业务服务与实现 |
| `mapper` | MyBatis Mapper 与向量仓储访问 |
| `model` | DTO、VO、请求响应模型 |
| `model.entity` | 数据库实体 |
| `commons.agent` | Agent 状态、节点、条件评估器、图工厂 |
| `commons.support` | 文档处理、模型工厂、Embedding、MCP、安全等支撑组件 |
| `commons.datasource` | 多租户数据源切换与 Schema 初始化 |

## 3. 🔄 核心执行链路

### 3.1 📄 知识库文档链路

1. `DocumentController.upload`
2. `DocumentServiceImpl.uploadDocument`
3. 文件做 SHA-256 去重校验
4. 上传到 OSS，并写入 `ai_document`
5. `DocumentProcessor.processDocument(...)` 异步执行
6. 从 OSS 拉取文件内容并解析
7. 使用 `DocumentSplitters.recursive(chunkSize, chunkOverlap)` 切分
8. 使用知识库绑定的 Embedding 模型批量 `embedAll`
9. 写入 `ai_document_chunk` 与对应维度的向量分表
10. 更新文档与知识库统计

### 3.2 💬 对话链路

| 阶段 | 当前实现 |
| --- | --- |
| 会话创建 | `ChatSessionController.create` |
| SSE 建链 | `GET /v1/chat/sessions/{sessionId}/messages/stream` |
| 发起消息 | `POST /v1/chat/sessions/{sessionId}/messages/stream` |
| 工作流模式 | 会话存在 `workflowId` 时走 `WorkflowExecutionService.executeStream` |
| 知识库模式 | 否则走 `RagService.retrieve + RagService.streamChat` |
| 持久化与统计 | 消息入库后原子更新会话 token、cost、messageCount |

### 3.3 🧠 工作流链路

| 项目 | 当前实现 |
| --- | --- |
| 工作流定义存储 | `ai_agent_workflow.graph_json` |
| 执行入口 | `WorkflowExecutionServiceImpl` |
| 支持能力 | 同步执行、流式执行、checkpoint 恢复、执行历史、执行状态 |
| 状态保存 | `MemorySaver`，进程内内存状态 |
| 结算 | token / cost 在执行完成后统一结算 |

## 4. 🧩 模块能力拆分

### 4.1 📚 知识库与文档

知识库核心字段：

| 字段 | 说明 |
| --- | --- |
| `embeddingModel` | 使用的 Embedding 模型 |
| `embeddingDimension` | 向量维度 |
| `chunkSize` | 切分块大小 |
| `chunkOverlap` | 切分重叠大小 |
| `chunkStrategy` | 分段策略 |
| `retrievalTopK` | 检索返回数量 |
| `similarityThreshold` | 相似度阈值 |

文档上传白名单：

| 类型 | 扩展名 |
| --- | --- |
| 文本类 | `txt` `md` `json` `xml` `csv` |
| Office / PDF | `pdf` `doc` `docx` `xls` `xlsx` `ppt` `pptx` |

文档处理状态：

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

> 💡 说明
>
> - `chunkStrategy` 会保存到知识库，当前文档切分统一使用递归切分器。
> - 文档上传默认走 OSS 存储。

### 4.2 🧪 LLM 提供商与模型

基础能力：

| 项目 | 当前实现 |
| --- | --- |
| 提供商管理 | `/v1/llm-model-providers` |
| 模型管理 | `/v1/llm-models` |
| 模型类型 | `CHAT / EMBEDDING / IMAGE` |
| 密钥存储 | `AesKeyEncryptionService`，AES-256-GCM |
| 模型缓存 | Caffeine，TTL 1 小时，最大 100 条 |

运行时实际支持情况：

| 模型能力 | 当前实现 |
| --- | --- |
| `ChatModel` | `OPENAI`、`OLLAMA` |
| `StreamingChatModel` | `OPENAI`、`OLLAMA` |
| `EmbeddingModel` | 当前仅实现 `OPENAI` |

运行时可用能力以 `ChatModelFactory` 与 `EmbeddingModelManager` 为准。

### 4.3 🤖 AI 应用

控制器路径：

| Method | Path |
| --- | --- |
| `POST` | `/v1/ai/apps` |
| `PUT` | `/v1/ai/apps` |
| `GET` | `/v1/ai/apps/{id}` |
| `GET` | `/v1/ai/apps` |
| `DELETE` | `/v1/ai/apps/{id}` |

应用实体绑定能力：

| 分类 | 字段 |
| --- | --- |
| 模型绑定 | `llmModelId` `systemPrompt` `temperature` `maxTokens` |
| 知识库绑定 | `kbId` `retrievalTopK` `similarityThreshold` `showCitation` |
| 工作流绑定 | `workflowId` |
| 对话配置 | `welcomeMessage` `suggestedQuestions` `enableFollowUp` |
| 发布配置 | `publishChannels` |

### 4.4 📝 Prompt 模板

接口总览：

| Method | Path |
| --- | --- |
| `POST` | `/v1/prompt-templates` |
| `GET` | `/v1/prompt-templates` |
| `GET` | `/v1/prompt-templates/{id}` |
| `PUT` | `/v1/prompt-templates/{id}` |
| `DELETE` | `/v1/prompt-templates/{id}` |
| `GET` | `/v1/prompt-templates/system` |
| `POST` | `/v1/prompt-templates/{templateId}/render` |

代码行为：

| 项目 | 当前实现 |
| --- | --- |
| 渲染引擎 | LangChain4j `PromptTemplate` |
| 占位符语法 | `{{variable}}` |
| 系统模板 | 不允许修改或删除 |
| RAG 模板装载 | 优先按知识库 `category` 加载，找不到则回退到 `system_rag_default` |

### 4.5 🔌 MCP Server 与工具

接口总览：

| Method | Path |
| --- | --- |
| `POST` | `/v1/mcp/servers` |
| `GET` | `/v1/mcp/servers` |
| `GET` | `/v1/mcp/servers/{id}` |
| `PUT` | `/v1/mcp/servers/{id}` |
| `DELETE` | `/v1/mcp/servers/{id}` |
| `POST` | `/v1/mcp/servers/{id}/connect/test` |
| `GET` | `/v1/mcp/servers/tools` |
| `POST` | `/v1/mcp/servers/tools/refresh` |

工具接入规则：

| 项目 | 当前实现 |
| --- | --- |
| 传输类型 | `STDIO`、`HTTP_STREAMABLE` |
| 本地工具来源 | Spring Bean 上的 `@Tool` |
| 远程工具来源 | MCP Server 工具 |
| 冲突优先级 | 本地 `@Tool` 优先于远程 MCP 同名工具 |

### 4.6 🕸️ 工作流与模板

工作流接口：

| Method | Path |
| --- | --- |
| `POST` | `/v1/ai/workflows` |
| `GET` | `/v1/ai/workflows` |
| `GET` | `/v1/ai/workflows/{id}` |
| `POST` | `/v1/ai/workflows/{id}/execute` |
| `POST` | `/v1/ai/workflows/{id}/execute/stream` |
| `POST` | `/v1/ai/workflows/{id}/resume` |
| `GET` | `/v1/ai/workflows/executions/{executionId}` |
| `GET` | `/v1/ai/workflows/{id}/threads/{threadId}/status` |
| `GET` | `/v1/ai/workflows/{id}/executions` |

工作流模板接口：

| Method | Path |
| --- | --- |
| `POST` | `/v1/ai/workflow-templates` |
| `PUT` | `/v1/ai/workflow-templates/{id}` |
| `DELETE` | `/v1/ai/workflow-templates/{id}` |
| `GET` | `/v1/ai/workflow-templates/{id}` |
| `GET` | `/v1/ai/workflow-templates/code/{code}` |
| `GET` | `/v1/ai/workflow-templates/code/{code}/version/{version}` |
| `GET` | `/v1/ai/workflow-templates/list` |
| `GET` | `/v1/ai/workflow-templates/system` |
| `GET` | `/v1/ai/workflow-templates/categories` |
| `POST` | `/v1/ai/workflow-templates/{id}/publish` |
| `POST` | `/v1/ai/workflow-templates/{id}/deprecate` |
| `POST` | `/v1/ai/workflow-templates/{id}/copy` |
| `GET` | `/v1/ai/workflow-templates/{templateId}/versions` |
| `POST` | `/v1/ai/workflow-templates/{templateId}/rollback` |
| `GET` | `/v1/ai/workflow-templates/{id}/export` |
| `POST` | `/v1/ai/workflow-templates/import` |
| `POST` | `/v1/ai/workflow-templates/validate` |

## 5. 🧱 工作流图定义

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
| `PARALLEL` | 并行执行多个分支，汇总后跳转 `joinNode` |
| `LOOP` | `while / doWhile / for` 循环节点 |
| `REACT_AGENT` | ReAct 风格专家节点 |
| `SUPERVISOR_AGENT` | 多智能体路由主管节点 |
| `SUBGRAPH` | 嵌入执行另一个工作流 |
| `AGENT_AGGREGATOR` | 聚合多专家或并行分支结果 |

### 5.3 当前条件评估器

从源码与测试可见，至少包含以下类型：

- `spel`
- `js`
- `confidence`
- `retrievalRelevance`
- `toolResult`

### 5.4 内置示例

资源路径：

- `src/main/resources/examples/workflows/multi-agent-code-review.json`
- `src/main/resources/examples/workflows/multi-agent-solution-design.json`

示例覆盖：

| 文件 | 侧重点 |
| --- | --- |
| `multi-agent-code-review.json` | `SUPERVISOR_AGENT + REACT_AGENT + AGENT_AGGREGATOR` |
| `multi-agent-solution-design.json` | `PARALLEL + REACT_AGENT + AGENT_AGGREGATOR` |

## 6. ⚙️ 配置说明

### 6.1 自动装配

引入依赖后，Spring Boot 会通过以下入口自动注册 `AiConfigure`：

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `AiAutoConfiguration`

Maven 依赖：

```xml
<dependency>
    <groupId>com.lambda.cloud</groupId>
    <artifactId>lambda-fusion-ai</artifactId>
    <version>2026.1.1-SNAPSHOT</version>
</dependency>
```

### 6.2 AI 配置项

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

补充说明：

| 项目 | 说明 |
| --- | --- |
| `security.encryption-key` | 由 `AesKeyEncryptionService` 读取 |
| 启动约束 | 未配置加密密钥会拒绝启动 |
| `document-chunk.default-chunk-overlap` | 启动时会自动校验并修正不合理值 |

### 6.3 数据源配置

模块要求 AI 数据源为 PostgreSQL，并已安装 `pgvector`。

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

## 7. 🗄️ 数据库对象

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
| `ai_robot` | AI 应用 / 机器人 |
| `ai_mcp_server` | MCP Server 配置 |
| `ai_llm_provider` | 提供商 |
| `ai_llm_model_type_provider` | 提供商支持的模型类型 |

### 7.2 向量分表

Liquibase 会创建 `create_vector_table(dimension)` 存储过程，并默认创建：

- `ai_vector_store_768`
- `ai_vector_store_1536`
- `ai_vector_store_2048`
- `ai_vector_store_4096`

运行时支持的维度常量：

- `768`
- `1536`
- `3072`
- `4096`

如需使用 3072 维模型，请额外执行：

```sql
SELECT create_vector_table(3072);
```

### 7.3 向量检索策略

| 阶段 | 当前实现 |
| --- | --- |
| 语义检索 | `vectorRepository.searchSimilar(...)` |
| 关键词检索 | `vectorRepository.searchKeyword(...)` |
| 结果融合 | RRF `reciprocalRankFusion(...)` |

## 8. 🚀 快速开始

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

当 `robotId` 存在时，会话会自动继承应用上的以下配置：

- `llmModelId`
- `systemPrompt`
- `kbId`
- `workflowId`

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

## 9. ❗ 错误码

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

## 10. 🛠️ 扩展点

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

## 11. 📍 源码定位

建议优先从以下入口阅读：

| 模块 | 路径 |
| --- | --- |
| 自动装配 | `src/main/java/com/lambda/fusion/autoconfig/AiAutoConfiguration.java` |
| 总配置 | `src/main/java/com/lambda/fusion/ai/AiConfigure.java` |
| 属性定义 | `src/main/java/com/lambda/fusion/ai/AiProperties.java` |
| 文档处理 | `src/main/java/com/lambda/fusion/ai/commons/support/processor/DocumentProcessor.java` |
| RAG | `src/main/java/com/lambda/fusion/ai/service/impl/RagServiceImpl.java` |
| 会话消息 | `src/main/java/com/lambda/fusion/ai/service/impl/ChatMessageServiceImpl.java` |
| 工作流执行 | `src/main/java/com/lambda/fusion/ai/service/impl/WorkflowExecutionServiceImpl.java` |
| 图工厂 | `src/main/java/com/lambda/fusion/ai/commons/agent/factory/AgentGraphFactory.java` |
| MCP | `src/main/java/com/lambda/fusion/ai/service/impl/McpServerServiceImpl.java` |
| 数据库变更 | `src/main/resources/META-INF/db/changelogs` |
