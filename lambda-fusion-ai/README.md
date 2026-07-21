# Lambda Fusion AI 模块

`lambda-fusion-ai` 是 Lambda Fusion 体系中的 AI 能力模块，基于 AgentScope 2.0 构建，面向 Spring Boot 提供多智能应用托管能力：在数据库中定义多个智能应用（App），每个应用绑定模型、系统提示词、工具与 MCP 服务，对外提供租户隔离的流式对话；WORKSPACE 型应用进一步对齐 AgentScope harness 完整能力（workspace/技能/子agent/记忆/沙箱/自演化）。

适用于需要在业务系统中集成多应用 AI 对话、模型管理、MCP 工具、自演化 Agent、沙箱执行能力的 Spring Boot 应用。

## 1. 设计概览

应用分两型，由 `appType` 决定能力栈：

| appType | 说明 | workspace | 文件工具 | 技能/子agent | 记忆 | 自演化 | 沙箱(shell) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `CHAT` | 纯 DB 配置（v1） | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ |
| `WORKSPACE` + `selfEvolve=false` | ASSISTANT 助手 | ✓ 平台管理 | ✗ | ✓ | ✗ | ✗ | 按 sandboxBackend |
| `WORKSPACE` + `selfEvolve=true` | AUTONOMOUS 自主 | ✓ agent 可写 | ✓ | ✓ | ✓ | ✓ | 按 sandboxBackend |

WORKSPACE 型的 `sandboxBackend` 决定执行隔离：`HOST`（宿主，无 shell）/ `DOCKER` / `KUBERNETES` / `E2B` / `DAYTONA` / `AGENTRUN`（后 5 个为沙箱，启用 shell 工具；条件装配，扩展未安装时回退 HOST）。

| 能力域 | 实现 |
| --- | --- |
| 自动装配 | `AiAutoConfiguration -> AiConfigure` |
| 模型管理 | DB 驱动：提供方 + 模型，API Key AES-GCM 加密 |
| 智能应用 | `/v1/ai/apps` CRUD，appType/selfEvolve/sandboxBackend |
| MCP 工具 | `/v1/ai/mcp-servers`，按应用装载 + 本地 `@Tool` |
| 对话 | `/v1/ai/sessions` 会话 + SSE 流式聊天，多轮上下文（内存状态） |
| Workspace | `/v1/ai/apps/{id}/workspace/*` 文件管理 + 自演化审计 |
| 多租户 | 所有表 `tenant_id` 隔离，应用层过滤 |

### 架构

```
请求 ──▶ ChatController(SSE) ──▶ ChatService
          ├─ ChatSessionService  会话/消息持久化（MySQL）
          ├─ AiAgentFactory      按 (app,tenant) 构建+缓存 HarnessAgent
          │    ├─ AiModelResolver     DB -> Model
          │    ├─ ToolkitAssembler    本地 @Tool + MCP
          │    └─ SandboxSpecResolver 沙箱后端 spec（DOCKER/K8s/E2B/Daytona/AgentRun）
          └─ HarnessAgent.streamEvents -> Flux<AgentEvent> -> SSE
```

## 2. 代码结构

主包：`src/main/java/com/lambda/fusion/ai`

| 包 | 作用 |
| --- | --- |
| `llm` | LLM 提供方与模型管理 |
| `apps` | 智能应用管理 + workspace 文件管理 API |
| `mcp` | MCP 服务管理 |
| `chat` | 对话会话与流式聊天 |
| `runtime` | AgentScope 集成：AiAgentFactory / AiModelResolver / ToolkitAssembler |
| `runtime.workspace` | Workspace 路径/脚手架/文件服务/自演化审计 |
| `runtime.sandbox` | 沙箱后端解析器（SandboxSpecResolver / SandboxBackendProvider） |
| `security` | API Key 加解密（AES-GCM） |

各沙箱后端的条件装配以嵌套静态配置类的形式收敛在 `AiConfigure.SandboxConfig` 中（每个后端一个 `@ConditionalOnClass` 嵌套类，扩展 jar 缺席时对应类不加载）。

## 3. 核心链路

- **Agent 构建**：`AiAgentFactory.getOrBuild(appId, tenantId)` 按 `appType` 分支；CHAT 关闭 workspace 能力，WORKSPACE 开启（AGENTS.md/技能/子agent/记忆）+ 按 `sandboxBackend` 选文件系统 spec；`selfEvolve` 决定只读/可写。配置变更经 `AiConfigChangedEvent` 失效缓存。
- **沙箱**：`SandboxSpecResolver` 按 `sandboxBackend` 找 `SandboxBackendProvider`（各后端在 `AiConfigure.SandboxConfig` 中按 `@ConditionalOnClass` 条件装配）构建 spec；后端不可用回退 HOST。
- **流式对话**：`streamEvents(text, RuntimeContext)` -> `Flux<AgentEvent>` -> SSE 帧（`delta`/`tool_start`/`tool_end`/`done`）。
- **自演化审计**：selfEvolve 应用每轮对话后，`WorkspaceAuditRecorder` 扫描 workspace 中本轮变更文件，复制快照并写入审计表。

多轮上下文由 Agent 内存状态（按 `sessionId` 隔离）维持。单节点 v1：进程重启后内存状态丢失（消息历史仍在 MySQL，可查看但不参与续聊）。

## 4. 能力详解

### 4.1 LLM 提供方与模型
提供方类型 `dashscope`/`openai`/`ollama`；模型类型 `CHAT`/`EMBEDDING`；API Key 加密存储；`AiModelResolver` 按 modelId 解析为 AgentScope `Model`。端点：`/v1/ai/llm-providers`、`/v1/ai/llm-models`。

### 4.2 智能应用
应用字段：`modelId` `systemPrompt` `maxIters` `temperature` `toolsAllow` `toolsDeny` `mcpServerIds` `appType` `selfEvolve` `sandboxBackend` `enabled`。端点：`/v1/ai/apps`。`appType` 创建后不可变；`selfEvolve`/`sandboxBackend` 可调。

### 4.3 MCP 服务
传输 `stdio`/`sse`/`http`/`streamable_http`；`ToolkitAssembler` 按应用装载，单个失败不影响其余；`POST /v1/ai/mcp-servers/{id}/test` 测连通性。

### 4.4 Workspace（WORKSPACE 型）
- 创建 WORKSPACE 应用时自动脚手架：`AGENTS.md`/`skills/`/`subagents/`/`memory/`/`knowledge/`/`tools.json`。
- 文件管理 API（ROLE_DEV）：
  - `GET /v1/ai/apps/{id}/workspace/files` 列出
  - `GET /v1/ai/apps/{id}/workspace/file?path=` 读取
  - `PUT /v1/ai/apps/{id}/workspace/file?path=` 写入
  - `GET /v1/ai/apps/{id}/workspace/audit` 自演化审计记录

### 4.5 对话
`POST /v1/ai/sessions` 创建、`GET /v1/ai/sessions/page` 列表、`GET /v1/ai/sessions/{id}/messages` 历史、`POST /v1/ai/sessions/{id}/chat`（SSE）流式对话。

## 5. 配置

```yaml
lambda:
  fusion:
    ai:
      runtime:
        default-max-iters: 10
      security:
        encryption-key: ${AI_ENCRYPTION_KEY}      # AES 密钥，生产必配
      workspace:
        root: ${user.home}/.agentscope/fusion      # workspace 根
      sandbox:
        isolation-scope: AGENT                      # AGENT|USER|SESSION|GLOBAL
        docker:    { image, network, cpu-count, memory-size-bytes, workspace-root }
        kubernetes:{ master-url, namespace, image, workspace-root, service-account, token }
        e2b:       { api-key, template-id, domain, workspace-root }
        daytona:   { api-key, api-url, workspace-root }
        agentrun:  { api-key, api-url, workspace-root }
```

| 项目 | 说明 |
| --- | --- |
| `security.encryption-key` | 未配置时启动告警，加密 API Key 时抛异常 |
| `sandbox.docker.*` | Docker 沙箱镜像/资源；镜像默认 `agentscope/python-sandbox:py311-slim` |
| `sandbox.{kubernetes,e2b,daytona,agentrun}.*` | 各云/K8s 后端凭据；对应扩展需在下游 classpath（ai 模块已 optional 引入） |
| 数据源 | v1 元数据表使用 MySQL `master` |

### 数据库对象
`ai_llm_provider` `ai_llm_model` `ai_app` `ai_mcp_server` `ai_chat_session` `ai_chat_message` `ai_app_workspace_audit`。迁移：`META-INF/db/changelogs/lambda-ai-changelog.xml`。

## 6. 快速开始

注册提供方 -> 注册模型 -> 创建应用 -> 创建会话 -> 流式对话。

创建 WORKSPACE 自演化应用：
```http
POST /v1/ai/apps
{
  "name": "自演化助手",
  "systemPrompt": "你是一位会持续学习的助手",
  "modelId": "model-id",
  "appType": "WORKSPACE",
  "selfEvolve": true,
  "sandboxBackend": "HOST",
  "enabled": true
}
```

沙箱执行（需下游引入对应扩展依赖 + 配置凭据 + Docker/集群可用）：
```http
POST /v1/ai/apps   { "appType":"WORKSPACE", "selfEvolve":true, "sandboxBackend":"DOCKER", ... }
```

## 7. 错误码
`30000-30999` 段。枚举见 `com.lambda.fusion.ai.exception.AiErrorCode`。

## 8. 扩展点
- 自定义本地工具：Spring Bean + AgentScope `@Tool` 方法，`ToolkitAssembler` 自动扫描。
- 自定义沙箱后端：实现 `SandboxBackendProvider`，在 `AiConfigure.SandboxConfig` 中仿照现有后端加一个带 `@ConditionalOnClass` 的嵌套配置类。

## 9. 源码定位

| 模块 | 路径 |
| --- | --- |
| Agent 工厂 | `runtime/AiAgentFactory.java` |
| 模型解析 | `runtime/AiModelResolver.java` |
| 沙箱装配 | `AiConfigure.SandboxConfig`（嵌套配置类） |
| Workspace | `runtime/workspace/` |
| 流式对话 | `chat/service/impl/ChatServiceImpl.java` |
| 数据库变更 | `META-INF/db/changelogs/lambda-ai-changelog.xml` |

## 10. 路线图
已完成：CHAT / WORKSPACE(ASSISTANT+AUTONOMOUS) / 自演化审计 / 多后端 SANDBOX(Docker+K8s+E2B+Daytona+AgentRun)。

待跟进：
- 完整 Spring context-load 测试（需 DB/Redis/Docker 环境）
- RAG 知识库（pgvector，ai-postgres）
- 应用分享与权限（run/edit/fork）
- 分布式状态（PostgresDistributedStore，多副本）
- 成本/Token 统计
