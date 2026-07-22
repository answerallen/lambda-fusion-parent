# Lambda Fusion AI 模块

`lambda-fusion-ai` 是 Lambda Fusion 体系中的 AI 能力模块，基于 AgentScope 2.0 构建，面向 Spring Boot 提供多智能应用托管能力：平台（ISV）预置智能体提供给运营商，每个应用绑定模型、系统提示词、工具与 MCP 服务，对外提供运营商隔离的流式对话；WORKSPACE 型应用进一步对齐 AgentScope harness 完整能力（workspace/技能/子agent/记忆/沙箱/自演化）。

适用于需要在业务系统中集成多应用 AI 对话、模型管理、MCP 工具、自演化 Agent、沙箱执行能力的 Spring Boot 应用。

> 能力表（app/provider/model/mcp）为平台级全局资源（无 tenant_id）；运营商隔离发生在会话层（session/message/audit 按 `tenant_id`）与运行时（agent 实例 + 工具按用户 tenant 查业务数据）。应用按 `audience`(B/C/ALL)+角色 或 `ownerId` 控制可见性。

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
| 智能应用 | `/v1/ai/apps` CRUD（平台预置，全局），appType/selfEvolve/sandboxBackend/audience/ownerId |
| MCP 工具 | `/v1/ai/mcp-servers`（全局），按应用装载 + 本地 `@Tool` |
| 对话 | `/v1/ai/sessions` 会话 + SSE 流式聊天，经 `HarnessGateway` 执行（per-session 串行），多轮上下文（内存状态） |
| Gateway/通道 | `HarnessGateway` + `ChannelManager`；外部 `Channel` Bean 自动装配为入站通道；`POST /v1/ai/outbound/send` 主动出站 |
| Workspace | `/v1/ai/apps/{id}/workspace/*?tenantId=` 文件管理 + 自演化审计 |
| 可见性 | app 按 `audience`(B/C/ALL)+角色 / `ownerId` 过滤；`GET /v1/ai/apps/available`（登录即可） |
| 运营商隔离 | 能力表(app/provider/model/mcp)全局无 tenant_id；session/message/audit 按 `tenant_id` 隔离 |

### 架构

```
请求 ──▶ ChatController(SSE) ──▶ ChatService
          ├─ ChatSessionService  会话/消息持久化（MySQL）
          ├─ AiAgentFactory      按 (app,tenant) 构建+缓存 HarnessAgent，并注册到 Gateway
          │    ├─ AiModelResolver     DB -> Model
          │    ├─ ToolkitAssembler    本地 @Tool + MCP
          │    └─ SandboxSpecResolver 沙箱后端 spec（DOCKER/K8s/E2B/Daytona/AgentRun）
          └─ HarnessGateway.runStream -> Flux<AgentEvent> -> SSE
              ├─ SessionTurnGate   per-session 轮次串行（同会话并发消息排队）
              ├─ ChannelManager    外部通道注册表（下游 Channel Bean 自动装配）
              └─ OutboundAddress   记录会话最近入站通道，供主动出站回推
外部通道（钉钉/微信等，下游实现 Channel Bean）──▶ ChannelManager ──▶ HarnessGateway ──▶ LF agent
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
| `runtime.gateway` | Gateway 集成：ChannelLifecycle / FusionRuntime（上下文透传 helper） |
| `security` | API Key 加解密（AES-GCM） |

各沙箱后端的条件装配以嵌套静态配置类的形式收敛在 `AiConfigure.SandboxConfig` 中（每个后端一个 `@ConditionalOnClass` 嵌套类，扩展 jar 缺席时对应类不加载）。

## 3. 核心链路

- **Agent 构建**：`AiAgentFactory.getOrBuild(appId, tenantId)` 按 `appType` 分支；CHAT 关闭 workspace 能力，WORKSPACE 开启（AGENTS.md/技能/子agent/记忆）+ 按 `sandboxBackend` 选文件系统 spec；`selfEvolve` 决定只读/可写。配置变更经 `AiConfigChangedEvent` 失效缓存。
- **沙箱**：`SandboxSpecResolver` 按 `sandboxBackend` 找 `SandboxBackendProvider`（各后端在 `AiConfigure.SandboxConfig` 中按 `@ConditionalOnClass` 条件装配）构建 spec；后端不可用回退 HOST。
- **流式对话**：`ChatService` 构建 `MsgContext`（room=LF sessionId，extra 透传 tenantId/appId/lfSessionId + agentId）+ `OutboundAddress`，经 `HarnessGateway.runStream` -> `Flux<AgentEvent>` -> SSE 帧（`delta`/`tool_start`/`tool_end`/`done`）。Gateway 的 `SessionTurnGate` 保证同会话并发消息串行。
- **会话标识双重性**：`RuntimeContext.sessionId` 为 Gateway 的 `gw-<hash>`（agent 内存状态槽位，按 `canonicalKey` 稳定映射）；LF `sessionId` 为业务/持久化键，经 `MsgContext.extra` 透传，由 `FusionRuntime` 读取。Gateway 未启用时回退直连 `agent.streamEvents`。
- **外部通道**：下游实现 `io.agentscope.harness.agent.gateway.channel.Channel` Bean，`ChannelLifecycle` 自动注册到 `ChannelManager` 并经共享 `HarnessGateway` 路由到 LF agent（靠 `preferredAgentId` 或 `ChannelConfig.defaultAgentId` 指定 `app:{appId}:t:{tenantId}`）。
- **自演化审计**：selfEvolve 应用每轮对话后，`WorkspaceAuditRecorder` 扫描 workspace 中本轮变更文件，复制快照并写入审计表。

多轮上下文由 Agent 状态存储维持（按 `sessionId` 隔离）。默认 `state-store.type=MEMORY`（进程内，重启丢失）；切 `FILE` 则 JSON 落盘，跨重启续聊。消息历史始终在 MySQL，可查看。

## 4. 能力详解

### 4.1 LLM 提供方与模型
提供方类型 `dashscope`/`openai`/`ollama`；模型类型 `CHAT`/`EMBEDDING`；API Key 加密存储；`AiModelResolver` 按 modelId 解析为 AgentScope `Model`。端点：`/v1/ai/llm-providers`、`/v1/ai/llm-models`。

### 4.2 智能应用
应用字段：`modelId` `systemPrompt` `maxIters` `temperature` `toolsAllow` `toolsDeny` `mcpServerIds` `appType` `selfEvolve` `sandboxBackend` `audience`(B/C/ALL) `ownerId`(预留独立应用) `enabled`。CRUD 端点 `/v1/ai/apps`（ROLE_DEV，平台 ISV 管理全局能力）；用户可用应用 `GET /v1/ai/apps/available`（登录，按 audience+角色/owner 过滤）。`appType` 创建后不可变；`selfEvolve`/`sandboxBackend`/`audience` 可调。

### 4.3 MCP 服务
传输 `stdio`/`sse`/`http`/`streamable_http`；`ToolkitAssembler` 按应用装载，单个失败不影响其余；`POST /v1/ai/mcp-servers/{id}/test` 测连通性。

### 4.4 Workspace（WORKSPACE 型）
- 首次对话时按运营商自动脚手架（per-`tenantId` workspace）：`AGENTS.md`/`skills/`/`subagents/`/`memory/`/`knowledge/`/`tools.json`。
- 文件管理 API（ROLE_DEV，需指定 `tenantId` 查看对应运营商的 workspace）：
  - `GET /v1/ai/apps/{id}/workspace/files?tenantId=` 列出
  - `GET /v1/ai/apps/{id}/workspace/file?tenantId=&path=` 读取
  - `PUT /v1/ai/apps/{id}/workspace/file?tenantId=&path=` 写入
  - `GET /v1/ai/apps/{id}/workspace/audit?tenantId=` 自演化审计记录

### 4.5 对话
`POST /v1/ai/sessions` 创建、`GET /v1/ai/sessions/page` 列表、`GET /v1/ai/sessions/{id}/messages` 历史、`POST /v1/ai/sessions/{id}/chat`（SSE）流式对话。流式对话经 `HarnessGateway.runStream` 执行，同会话并发消息由 `SessionTurnGate` 串行排队。

### 4.6 Gateway 与外部通道
- **Gateway**：`HarnessGateway`（单例）作为运行时入口，agent 按 `app:{appId}:t:{tenantId}` 注册；内部 SSE 直接调 `gateway.runStream`，外部通道经 `ChannelManager` 路由到同一 Gateway。`SessionTurnGate` 提供 per-session 公平锁。
- **外部通道扩展点**：下游实现 `io.agentscope.harness.agent.gateway.channel.Channel`（`channelId`/`config`/`dispatch`/`deliver`/`start`/`stop`），声明为 Spring Bean，`ChannelLifecycle` 自动注册并拉起。入站消息靠 `preferredAgentId` 或 `ChannelConfig.defaultAgentId` 路由到指定 LF agent。
- **主动出站**：`POST /v1/ai/outbound/send`（ROLE_DEV），`{sessionId, messages}` 回推到会话最近入站通道，或 `{channelId, to, messages}` 显式通道投递。内部 SSE 通道为请求/响应模型，不支持 proactive push 到 Web 端。

## 5. 配置

```yaml
lambda:
  fusion:
    ai:
      runtime:
        default-max-iters: 10
      security:
        encryption-key: ${AI_ENCRYPTION_KEY}      # AES 密钥，生产必配
      audience:
        b-roles: [ROLE_OPERATOR]                    # B 端角色名（下游自定义）
        c-roles: [ROLE_CONSUMER]                    # C 端角色名
      gateway:
        enabled: true                               # 关闭则回退直连 agent.streamEvents（无串行锁/通道）
      state-store:
        type: MEMORY                                # MEMORY（默认，进程内，重启丢失）/ FILE（JSON 落盘，重启不丢）
        root:                                       # FILE 模式根目录；默认 ${workspace.root}/state
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
| `audience.{b,c}-roles` | B/C 端角色名列表，用于 app 可见性过滤（下游自定义角色名） |
| `gateway.enabled` | 是否启用 `HarnessGateway`（默认 true）。关闭后回退直连，无轮次串行锁与外部通道能力 |
| `state-store.type` | Agent 状态存储（多轮记忆）：`MEMORY`（默认，进程内，重启丢失）/ `FILE`（JSON 落盘，跨重启）。按部署形态选 |
| `state-store.root` | FILE 模式根目录；默认 `${workspace.root}/state` |
| `sandbox.docker.*` | Docker 沙箱镜像/资源；镜像默认 `agentscope/python-sandbox:py311-slim` |
| `sandbox.{kubernetes,e2b,daytona,agentrun}.*` | 各云/K8s 后端凭据；对应扩展需在下游 classpath（ai 模块已 optional 引入） |
| 数据源 | v1 元数据表使用 MySQL `master` |

### 数据库对象
`ai_llm_provider` `ai_llm_model` `ai_app` `ai_mcp_server`（平台全局，无 tenant_id）；`ai_chat_session` `ai_chat_message` `ai_app_workspace_audit`（按 `tenant_id` 运营商隔离）。`ai_app` 另含 `owner_id`(预留独立应用)/`audience`(B/C/ALL)。迁移：`META-INF/db/changelogs/lambda-ai-changelog.xml`。

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
- 自定义外部通道：实现 `io.agentscope.harness.agent.gateway.channel.Channel`，声明为 Spring Bean，`ChannelLifecycle` 自动注册到 `ChannelManager` 并经 `HarnessGateway` 路由。入站消息在 `InboundMessage.preferredAgentId`（或 `ChannelConfig.defaultAgentId`）填 `app:{appId}:t:{tenantId}` 指定目标 agent。

## 9. 源码定位

| 模块 | 路径 |
| --- | --- |
| Agent 工厂 | `runtime/AiAgentFactory.java` |
| 模型解析 | `runtime/AiModelResolver.java` |
| 沙箱装配 | `AiConfigure.SandboxConfig`（嵌套配置类） |
| Gateway/通道装配 | `AiConfigure.GatewayConfiguration`（嵌套配置类） |
| 通道生命周期 | `runtime/gateway/ChannelLifecycle.java` |
| 上下文透传 | `runtime/gateway/FusionRuntime.java` |
| Workspace | `runtime/workspace/` |
| 流式对话 | `chat/service/impl/ChatServiceImpl.java` |
| 主动出站 | `chat/controller/OutboundController.java` |
| 数据库变更 | `META-INF/db/changelogs/lambda-ai-changelog.xml` |

## 10. 路线图
已完成：CHAT / WORKSPACE(ASSISTANT+AUTONOMOUS) / 自演化审计 / 多后端 SANDBOX(Docker+K8s+E2B+Daytona+AgentRun) / 能力表去 tenant_id + audience+owner_id 可见性 / review 修复（#1 审计异步、#2 workspace spec、#3 沙箱回退、#4 删除清 workspace、#6 stateStore） / **HarnessGateway 接入（per-session 串行 + 外部通道 SPI + 主动出站）** / **Agent 状态存储可配（MEMORY 默认 / FILE 落盘）**。

待跟进：
- 完整 Spring context-load 测试（需 DB/Redis/Docker 环境）
- `WakeupDispatcher` + `MessageBus`（异步子 agent 完成 announce、团队消息、定时触发）
- `DistributedStore` / `StoreBackedSubagentRegistry`（跨节点子 agent 恢复，多副本；多副本时 state-store 需换分布式实现）
- `ChannelConfig` 绑定规则管理 UI / DB 表（按 peer/guild/role 路由到 agent）
- 真实平台通道适配器（钉钉/微信/Slack，下游实现）
- RAG 知识库（pgvector，ai-postgres）
- 应用分享与权限（run/edit/fork）
- 成本/Token 统计
