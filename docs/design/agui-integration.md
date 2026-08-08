# AG-UI 协议集成

> 对话流接入 [AG-UI](https://docs.ag-ui.com/) 协议，将 agentscope 事件流映射为标准 AG-UI SSE 事件，前端 TDesign `AGUIAdapter` 消费，实现文本流式、工具调用、推理过程的可视化与历史回放。

## 1. 架构总览

```
agentscope v2 streamEvents          前端 TDesign AGUIAdapter
  AgentEvent (细粒度)                 handleAGUIEvent
        |                                  ^
        v                                  |
  AguiEventMapper ----> AguiEventEncoder ---> SSE "data: {json}"
  (映射 + 工具调用累积)    (camelCase JSON)        |
                                                 v
  后端 ChatServiceImpl                       前端 chat-panel.vue
  (SseEmitter + 持久化)                      (遍历 content 分类型渲染)
```

| 端 | 文件 | 职责 |
| :--- | :--- | :--- |
| 后端 | `lambda-fusion-ai/.../chat/adapter/AguiEventMapper.java` | agentscope `AgentEvent` -> AG-UI `AguiEvent`，累积工具调用快照 |
| 后端 | `lambda-fusion-ai/.../chat/service/impl/ChatServiceImpl.java` | 订阅事件流，经 mapper + encoder 输出 SSE，流结束持久化文本与工具调用 |
| 后端 | `lambda-fusion-ai/.../chat/controller/ChatController.java` | `POST /v1/ai/sessions/{id}/chat`（`text/event-stream`） |
| 后端 | `lambda-fusion-ai/src/test/.../AguiEventMapperTest.java` | 映射与序列化协议测试（7 用例） |
| 前端 | `packages/system-ui/src/views/ai/chat/chat-panel.vue` | `AGUIAdapter.handleAGUIEvent` 消费，模板遍历 `m.content` 分类型渲染 |

## 2. 协议链路（非显而易见的关键点）

后端 `AguiEvent` 是 sealed interface + records，经 Jackson 序列化为 AG-UI 标准 JSON：

- **type 字段**：`@JsonTypeInfo(use=Id.NAME, property="type")` + `@JsonSubTypes`，name 为 `TEXT_MESSAGE_START` / `TOOL_CALL_RESULT` / `RUN_FINISHED` 等，与前端 `AGUIEventType` 枚举一一对应。
- **字段命名**：`JacksonJsonCodec`（agentscope 默认）**未设 `PropertyNamingStrategy`**，使用默认 camelCase，字段名 `threadId` / `messageId` / `delta` / `toolCallId` / `toolCallName` 与前端读取一致。
- **SSE 格式**：`AguiEventEncoder.encodeToJson()` 返回**带前导空格**的 JSON（`" {json}"`），配合 `SseEmitter.event().data()` 生成 `data: {json}\n\n`，符合 AG-UI 客户端期望。
- **事件序列**（一次文本对话）：`RUN_STARTED` -> `TEXT_MESSAGE_START` -> `TEXT_MESSAGE_CONTENT`(多次) -> `TEXT_MESSAGE_END` -> `RUN_FINISHED`；带工具时中间插入 `TOOL_CALL_START` / `TOOL_CALL_ARGS` / `TOOL_CALL_END` / `TOOL_CALL_RESULT`。

## 3. 后端实现要点

### AguiEventMapper

- 消费 v2 `streamEvents`（**非** deprecated 的 v1 `agent.stream()`）。v1 的 coarse `Event` 不暴露 `RequireUserConfirmEvent`，会丢失 HITL 能力。
- 有状态（每个对话流新建实例）：`textMessageId` / `reasoningMessageId` 配对 START/END；`startedToolCalls` 去重；`toolCallAccumulators` 累积 args/result。
- 工具调用开始前 `closeActiveMessage()` 先关闭活跃的文本/推理消息（AG-UI 要求消息边界清晰）。
- `enableReasoning` 开关控制是否产出 `REASONING_MESSAGE_*`。
- **`getToolCalls()`**：流结束后返回 `List<ToolCallRecord>`（`toolCallId` / `toolCallName` / `args` / `result`），供持久化。

### ChatServiceImpl

- `subscribe` 的 onNext：累积 `TextBlockDeltaEvent` 的 delta 为 `assistantText`，每个 `AgentEvent` 经 `mapper.map()` 产出 0..n 个 `AguiEvent`，`encodeToJson` 后 `SseEmitter.send`。
- onComplete：`serializeToolCalls(mapper.getToolCalls())` -> JSON，`saveAssistantMessage(session, content, toolCallJson)` 持久化文本与工具调用。
- onError：直接 `completeWithError`（后续可补 `RUN_ERROR` 事件）。

### 持久化

- `ai_chat_message.tool_call` 字段（String JSON）+ `tool` role 早存在于 schema，**无需数据库 migration**。
- `ChatMessageService.saveAssistantMessage(session, content, toolCall)` 重载写入 `tool_call`；空则不写。
- `listBySession` 返回 `ChatMessageView`（含 `toolCall` 字段）。

## 4. 前端实现要点

### AGUIAdapter 接入

- `onMessage: (chunk) => aguiAdapter.handleAGUIEvent(chunk, { onRunError })`：`AGUIAdapter` 内部 `JSON.parse(chunk.data)` 后按 `event.type` 映射为 `AIMessageContent`。
- `onRequest` 里 `aguiAdapter.reset()`：每次发送重置适配器状态，避免跨轮工具调用残留。
- `AGUIAdapter` 从 `@tdesign-vue-next/chat` 导出（经 `tdesign-web-components/lib/chat-engine` 重新导出）。

### 模板分类型渲染

`ChatContent` 只接受单 `text` / `markdown` 块（`ChatContentData = {type, data}`），**不渲染 toolcall / reasoning**。故模板遍历 `m.content` 分类型：

| 内容块 type | 渲染组件 | 说明 |
| :--- | :--- | :--- |
| `reasoning` | `<ChatReasoning>` | `#header` slot 放标题，默认 slot 放 `reasoningText` 拍平文本（data 是嵌套 `AIMessageContent[]`） |
| `toolcall` / `toolcall-{name}-{id}` | `<ToolCallRenderer :tool-call>` | 默认渲染，无需 `useAgentToolcall` 注册 |
| `markdown` / `text` | `<ChatContent :content>` | 单块渲染 |

> toolcall 块的 type 是动态 `toolcall-{name}-{id}`（AGUIAdapter 用它做 merge key），判断时用 `startsWith('toolcall-')`。
>
> `ChatReasoning` 的 `header` prop 是 `TNode`，直接传 string 会被 `PropType` 拒（Vue 联合类型解析问题），改用 `#header` slot 绕过。

### 历史回放

- `mapHistory` 解析 `tool_call` JSON，构造 `toolcall-{name}-{id}` 内容块（type 与实时流一致）+ markdown 块。
- **未用** `AGUIAdapter.convertHistoryMessages`：它期望独立 `role=tool` 消息 + `assistant.toolCalls` 分组，当前架构单条 assistant 消息含工具调用更简单，手动构造更可控。
- 兼容旧数据（无 `toolCall` 时仅文本块）。
- 推理不持久化（过程性，刷新后看最终回复即可）。

## 5. 设计决策

| 决策 | 理由 |
| :--- | :--- |
| 用 `streamEvents` + 自写 `AguiEventMapper`，**非** agentscope 内置 `AguiAgentAdapter` | `AguiAgentAdapter` 用 deprecated v1 `agent.stream()`，coarse `Event` 不含 `RequireUserConfirmEvent`，丢失 HITL |
| 工具调用存单条 assistant 消息的 `tool_call` 字段，**非**独立 tool 消息 | 改动小，复用现有 schema，无需改消息存储结构 |
| 历史回放手动构造 content，**非** `convertHistoryMessages` | 单条 assistant 含工具调用，无需独立 tool 消息分组 |
| 推理不持久化 | 推理是过程性数据，回放价值低；只持久化有回放价值的工具调用 |

## 6. 测试与验证

### 后端单元测试

```bash
mvn -pl lambda-fusion-ai test -Dtest=AguiEventMapperTest
```

7 用例覆盖：文本流 Start/Content 配对、同 replyId 不重复 Start、camelCase JSON（无 snake_case）、工具调用完整序列、推理开关、工具调用关闭活跃文本、`getToolCalls` 快照。

### curl 验证 SSE

```bash
curl -N -X POST "http://localhost:20005/v1/ai/sessions/{sessionId}/chat" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"content":"你好"}'
```

预期逐行输出 `data: {"type":"TEXT_MESSAGE_CONTENT",...}`，字段 camelCase，type 为 AG-UI 标准大写串。

### 前端端到端

1. 文本回复流式渲染
2. 带工具应用：工具调用显示为 ToolCallRenderer 卡片（工具名/参数/结果）
3. 推理模型：推理显示为 ChatReasoning 折叠区
4. 刷新页面：历史消息显示工具调用卡片 + 文本（推理不回放）

## 7. 后续阶段

- **HITL**：`REQUIRE_USER_CONFIRM` 事件映射 + 回传端点。agentscope 的 HITL 机制是 agent 暂停（`ToolCallState.ASKING`），第二次 `streamEvents` 携带 `Msg.METADATA_CONFIRM_RESULTS`（`List<ConfirmResult>`）恢复。
- **Activity**：`CUSTOM` 事件 -> `ACTIVITY_*`。agentscope 不自动产出 Activity 事件，需自建数据源（如 RAG 检索过程、子智能体调度）。

### 错误处理（已实现）

流异常（`Flux#onError`）时，`AguiEventMapper.mapError` 发 `RunError` 事件（含异常 message），`ChatServiceImpl` 再正常 `emitter.complete()` 关闭连接（**非** `completeWithError`）。前端 `onRunError` 回调显示错误提示，`onComplete` 让对话状态结束可重试，避免静默断流。

> `AGENT_END` 时主动 `emitter.complete()`（不依赖 `Flux#onComplete`）：gateway/agent 的 Flux 在 AGENT_END 后可能不立即 complete，仅依赖 onComplete 会导致前端 status 不变 complete。`AtomicBoolean` 防重复，`emitter.onCompletion/onTimeout` 时 `dispose()` Flux 订阅防泄漏。

## 8. 提交记录

| commit | 模块 | 阶段 |
| :--- | :--- | :--- |
| `41133895` | lambda-fusion-ai | 1+2a 对话流改发 AG-UI 协议事件 |
| `1fa6b8d24` | @vben/system-ui | 2a 对话接入 AG-UI 协议适配器 |
| `a8b8c976c` | @vben/system-ui | 2b 对话渲染工具调用与推理过程 |
| `7a2f2261` | lambda-fusion-ai | 3 持久化工具调用快照供历史回放 |
| `2ca86e057` | @vben/system-ui | 3 历史回放渲染工具调用 |
| `d65793ab` | lambda-fusion-ai | fix AGENT_END 主动结束 SSE 连接 |
| `b5b613be` | lambda-fusion-ai | 流异常发 RunError 事件 |
| `cb17d776a` | @vben/system-ui | RunError 显示错误提示 |
