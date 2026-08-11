# 子代理 Remote Agent 支持

> 为 fusion 子代理（`ai_sub_agent`）增加 **Remote HTTP** 来源模式：子代理不再局限于本地内联 system prompt，可声明为指向 AgentScope task HTTP server 的远程代理，执行出进程。harness 侧为**声明驱动、开箱即用**，fusion 仅需落 DB + DTO + 映射 + 校验。

## 1. 背景与结论

当前 fusion 子代理只支持一种来源——`prompt → inlineAgentsBody`（内联 system prompt，本地执行），见 `SubAgentDeclarationMapper.toDeclaration()`。

harness 的 `SubagentDeclaration` 原生定义了**三种互斥来源模式**（`build()` 强校验，`SubagentDeclaration.java:643-674`）：

| 来源模式 | 触发字段 | 执行位置 | fusion 现状 |
| :--- | :--- | :--- | :--- |
| Definition workspace | `workspace(Path)` | 本地（外部 AGENTS.md 目录） | 未用 |
| Inline body | `inlineAgentsBody(String)` | 本地 | **唯一在用** |
| **Remote HTTP** | `url(String)` | **出进程（远端 task server）** | 待接入 |

**结论：fusion 接入 Remote 子代理改动小、无 harness 侧改动。** harness 执行链路已全自动：

- 装配：`HarnessAgentBuilderSupport.buildDeclaredFactory` 在 `decl.isRemote()` 时挂 `RemoteSubagentStub` 占位注册（`HarnessAgentBuilderSupport.java:421`）。
- 派发：`AgentSpawnTool` 用 `decl.getUrl()/getHeaders()` 构造 `RemoteTarget` + `RemoteTaskRunSpec`，经 `RemoteSubagentTransport` 提交远端（`AgentSpawnTool.java:1344-1352`）。
- 协议：`AgentProtocolTaskClient` 实现 `POST /tasks`、`GET /tasks/{id}/wait`、`/events`(SSE)、`/resume`(HITL)、`/cancel`。

## 2. harness 远端执行链路（非显而易见的关键点）

```
父 agent LLM 决定调度某 remote 子代理
        |
        v
AgentSpawnTool.agent_spawn (timeout_seconds=0 后台任务)
        |  decl.getUrl()/getHeaders() → RemoteTarget
        |  decl.isRemoteStreaming()   → 是否回流事件
        v
RemoteSubagentTransport (Agent Protocol)
        |  POST /tasks {task_id, agent_id, input, context?}
        |  GET  /tasks/{id}/events (SSE, 远端事件回流父流)
        |  GET  /tasks/{id}/wait?timeout_seconds=n
        |  POST /tasks/{id}/resume {decisions}  (远端 HITL)
        |  POST /tasks/{id}/cancel
        v
AgentScope task HTTP server (出进程执行 agent)
```

Remote 专属声明字段（`SubagentDeclaration` builder）：

| 字段 | 默认 | 说明 |
| :--- | :--- | :--- |
| `url` | — | 远端 task server base URL；非空即 `isRemote()` |
| `headers` | null | 鉴权头等（如 `Authorization`） |
| `remoteStreaming` | true | 远端事件是否回流父事件流 |
| `remoteStreamDetail` | `FULL` | `FULL` / `VERBOSE`（转发事件详细度） |
| `remoteAskPolicy` | `DENY` | 远端 HITL 确认策略：`DENY` 自动拒绝 / `PROPAGATE` 透传上游 |
| `remoteContextAttributes` | null | 每次 submit 附带 `context.attributes`（租户/路由标签） |

**关键约束（来自 harness `build()` 互斥校验）**：remote 模式下 `url` 与 `inlineAgentsBody`/`workspace` **互斥**，`prompt` 必须为空，否则 harness 抛 `IllegalArgumentException("url() and inlineAgentsBody() are mutually exclusive")`。

## 3. fusion 侧改动方案

### 3.1 概念与字段

新增「代理来源类型」概念 `agent_kind`，区分本地内联与远程 HTTP。remote 模式新增一组 `remote_*` 字段。

`AiConstants` 新增枚举（沿用现有枚举风格，`of(code)` 容错返回 null）：

```java
enum SubAgentKind {
    LOCAL("LOCAL", "本地内联"),       // prompt → inlineAgentsBody，现有行为
    REMOTE("REMOTE", "远程 HTTP");    // url → 远端 task server
    // of(String code)
}
```

### 3.2 数据库迁移（Liquibase）

新增 changeSet，对 `ai_sub_agent` `addColumn`（幂等 `preConditions columnExists`，沿用现有 changelog 风格）：

| 列 | 类型 | 说明 |
| :--- | :--- | :--- |
| `agent_kind` | `varchar(16)` default `'LOCAL'` | 来源类型：LOCAL\|REMOTE |
| `remote_url` | `varchar(512)` | 远端 task server base URL（REMOTE 必填） |
| `remote_headers_encrypted` | `varchar(2048)` | 鉴权头密文 JSON（AES-GCM，见 §4 安全） |
| `remote_streaming` | `tinyint(1)` default 1 | 远端事件是否回流父流 |
| `remote_stream_detail` | `varchar(16)` | FULL\|VERBOSE |
| `remote_ask_policy` | `varchar(16)` default `'DENY'` | 远端 HITL 策略：DENY\|PROPAGATE |
| `remote_context_attributes` | `text` | 提交上下文属性 JSON 对象 |

> `prompt` 列现为 `nullable=false`。remote 模式下 prompt 无意义，迁移需同步放宽 `prompt` 为可空（`modifyDataType` / drop not-null），或 remote 行写入占位空串。**建议放宽为可空**，由 service 层按 `agent_kind` 校验必填性。

### 3.3 Entity / DTO

`SubAgentEntity` 新增对应字段。`remote_headers_encrypted` 存密文 String；`remote_context_attributes` 用 `JacksonTypeHandler`（同 `toolsAllow`）映射 `Map<String, Object>`。

`CreateSubAgent` / `UpdateSubAgent` 新增：

```java
@Schema(description = "来源类型: LOCAL|REMOTE")
private String agentKind = "LOCAL";

@Schema(description = "远端 task server 地址(REMOTE 必填, http/https)")
private String remoteUrl;

@Schema(description = "远端鉴权头(明文入参,落库加密;REMOTE)")
private Map<String, String> remoteHeaders;

@Schema(description = "远端事件是否回流父流(默认 true)")
private Boolean remoteStreaming;

@Schema(description = "远端事件详细度: FULL|VERBOSE")
private String remoteStreamDetail;

@Schema(description = "远端 HITL 策略: DENY|PROPAGATE(默认 DENY)")
private String remoteAskPolicy;

@Schema(description = "远端提交上下文属性(租户/路由标签)")
private Map<String, Object> remoteContextAttributes;
```

> `remoteHeaders` 入参为明文 Map，service 层序列化后经 `KeyEncryptionService.encrypt` 落库；读取时 `decrypt`。与 `LlmProviderEntity.apiKeyEncrypted` 同一套 `KeyEncryptionService`（`security/KeyEncryptionService.java`）。

### 3.4 Service 校验（`SubAgentServiceImpl`）

`create` / `update` 按 `agentKind` 走两套互斥校验——**在 service 层提前拦截，给友好错误，而非等 harness 抛 `IllegalArgumentException`**：

**REMOTE 模式：**
- `remoteUrl` 必填，且需 `http(s)://` 前缀校验（非法抛 `AiErrorCode.INVALID_PARAMETER`）。
- `prompt` 必须为空（与 url 互斥）。
- 以下本地专属字段**应忽略/置空**：`modelId`、`steps`、`temperature`、`topP`、`toolsAllow`、`skillsAllow`、`workspaceMode`（模型/工具/workspace 由远端 server 决定）。
- `remoteStreamDetail` ∈ {FULL, VERBOSE}；`remoteAskPolicy` ∈ {DENY, PROPAGATE}。

**LOCAL 模式（现有行为）：**
- `prompt` 必填（现有 `@NotBlank`）。
- `remote_*` 字段全部置空/忽略。

新增错误码：`AiErrorCode.SUB_AGENT_REMOTE_URL_INVALID`（或复用 `INVALID_PARAMETER`）。

### 3.5 声明映射（`SubAgentDeclarationMapper`）

`toDeclaration` 增加 remote 分支：

```java
if (SubAgentKind.of(entity.getAgentKind()) == SubAgentKind.REMOTE
        && StringUtils.isNotBlank(entity.getRemoteUrl())) {
    builder.url(entity.getRemoteUrl())
           .headers(decryptHeaders(entity))           // KeyEncryptionService.decrypt
           .remoteStreaming(entity.getRemoteStreaming())
           .remoteStreamDetail(RemoteStreamDetail.valueOf(...))
           .remoteAskPolicy(RemoteAskPolicy.valueOf(...))
           .remoteContextAttributes(entity.getRemoteContextAttributes());
    // 不设置 inlineAgentsBody / model / steps / temperature / topP / tools / skills / workspaceMode
    return builder.build();
}
// LOCAL：现有 inlineAgentsBody 分支不变
```

> 注意：解密需要 `KeyEncryptionService`，而 mapper 当前是**包级静态纯函数**（便于单测）。方案：把 headers 解密上移到 `AgentFactory.resolveSubAgents`（已是 Spring Bean），解密后再传入纯函数映射；或 mapper 改为实例 Bean 注入 `KeyEncryptionService`。**倾向前者**，保持 mapper 纯函数与可测性。

### 3.6 变更重建

沿用现有 `publishChanged() → ConfigChangedEvent.all()` 全量失效重建机制即可，remote 字段变更同样触发 agent 重建（`AgentFactory.onConfigChanged`）。无需额外改动。

## 4. 安全

- `remote_headers_encrypted` 常含 token/Authorization，**落库必加密**：复用 `KeyEncryptionService`（AES-GCM，`security/AesKeyEncryptionService.java`），与 `LlmProviderEntity.apiKeyEncrypted` 一致。
- API 出参（`get`/`page`）**不回传明文 headers**，仅回脱敏标记或 key 列表。
- `remoteUrl` 校验 scheme 仅允许 `http/https`，避免 `file:`/`gopher:` 等 SSRF 风险；如需内网地址白名单可后续加配置。

## 5. 运维与行为注意点

1. **HITL 语义变化**：`remoteAskPolicy` 默认 `DENY`——父 agent 无活动事件流消费者时**自动拒绝**远端工具确认。fusion 已有本地 HITL（`@RequireConfirm` + ASK 规则 + AG-UI）。若要让远端确认透传到用户，需配 `PROPAGATE` 且保证事件流可用。这是一个**行为差异点**，文档与前端需明确。
2. **超时**：`submitTask` 10 分钟、`waitForResult` 按 `timeout_seconds`、SSE 3 小时；远端长任务由父 spawn 的 `timeout_seconds=0`（后台任务）驱动，符合 harness 设计。
3. **多租户**：`remoteContextAttributes` 是把 `tenantId`/部署标签透传给远端路由的官方通道，建议默认注入 `tenantId`。
4. **可观测**：远端执行细节（模型/工具/迭代）在远端 server 侧，fusion 只能看到回流事件流；`remoteStreaming=true` + `VERBOSE` 可获得接近本地子代理的完整流。

## 6. 设计决策

| 决策 | 理由 |
| :--- | :--- |
| `agent_kind` 显式区分 LOCAL/REMOTE | 三模式互斥，显式类型比「靠 url 是否为空推断」更清晰，便于 service 校验与前端表单分流 |
| 复用 `KeyEncryptionService` 加密 headers | 与 provider apiKey 同一套密钥管理，无新增基础设施 |
| **remote 下放宽 `prompt` 可空** ✅ 已确认 | prompt 仅 LOCAL 有意义；NOT NULL 会逼 remote 行写占位空串，语义脏 |
| **mapper 保持纯函数，解密上移到 AgentFactory** ✅ 已确认 | 保留 mapper 单测便利性，避免引入 Spring 依赖 |
| **一期支持 `remoteAskPolicy=PROPAGATE`** ✅ 已确认 | 远端 HITL 确认透传上游（父事件流 → AG-UI → 用户），与 fusion 本地 HITL 体验对齐；`DENY` 仍为默认保底 |
| remote 下忽略 model/tools/workspace 等本地字段 | 这些由远端 server 决定，强行映射会被 harness 互斥校验拒绝或无意义 |

## 7. 影响面与落地清单

| 端 | 文件 | 改动 |
| :--- | :--- | :--- |
| DB | `lambda-ai-changelog.xml` | 新增 changeSet：`ai_sub_agent` addColumn（agent_kind + remote_*），放宽 prompt 可空 |
| 常量 | `AiConstants.java` | 新增 `SubAgentKind` 枚举 |
| Entity | `SubAgentEntity.java` | 新增 agentKind + remote_* 字段 |
| DTO | `CreateSubAgent.java` / `UpdateSubAgent.java` | 新增 remote 字段 |
| Service | `SubAgentServiceImpl.java` | 按 agentKind 两套互斥校验；headers 加解密；URL 校验 |
| 错误码 | `AiErrorCode.java` | 新增 remote URL 非法错误码 |
| 映射 | `SubAgentDeclarationMapper.java` | 新增 REMOTE 分支（或 headers 解密上移到 AgentFactory） |
| 工厂 | `AgentFactory.java` | （可选）headers 解密后传入映射 |
| 测试 | `lambda-fusion-ai/src/test/...` | 映射单测（LOCAL/REMOTE 两分支）+ service 互斥校验测试 |
| 前端 | `_web` 子代理表单 | 按 agentKind 分流 LOCAL（prompt/模型/工具）与 REMOTE（url/headers/策略）表单 |

> 前端 `_web` 改动单独评估；commitlint scope 用 `@vben/<pkg>` 包名（见工程约定）。

## 8. 待确认项

已确认：① `prompt` 放宽可空；② headers 解密上移 `AgentFactory`（mapper 保持纯函数）；③ 一期支持 `PROPAGATE`。

剩余待确认：

1. `remoteUrl` 是否需要内网地址白名单 / SSRF 防护配置？（一期先做 scheme 白名单 http/https，是否追加 IP/域名白名单？）
