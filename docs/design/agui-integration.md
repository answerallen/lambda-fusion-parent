# AG-UI 协议集成设计

> 本文描述目标 ChatRun 架构下 AgentScope 事件、Run 快照、SSE 和前端 AG-UI 状态之间的边界。
> 后台续跑与恢复以 [对话后台续跑与断线恢复设计](chat-run-resume.md) 为准；AgentScope 调用和完成语义以
> [ChatRun 与 AgentScope 执行边界设计](chat-run-agentscope-execution.md) 为准。

## 1. 架构

```text
HarnessAgent.streamEvents
        |
        v
AgentExecutionAdapter
        |
        v
ChatRunInstance
        +---- AgentEventInterpreter ----> AG-UI 事件 ----> ChatRunEventStore
        |                                      |                  |
        +---- ChatRunSnapshotAccumulator <-----+                  v
        |                                                     SSE 订阅
        +---- ChatRunStateService ----> Run 快照/消息/业务状态      |
                                                               v
                                                    TDesign AGUIAdapter
```

| 组件 | 职责 |
| :--- | :--- |
| `chat/runtime/adapter/AgentExecutionAdapter` | 使用 Session 权威身份调用 AgentScope，并提供状态读取、中断和 HITL 操作 |
| `chat/runtime/agui/AgentEventInterpreter` | 将单个 `AgentEvent` 解释为 AG-UI 事件和快照增量 |
| `chat/runtime/ChatRunSnapshotAccumulator` | 维护可持久化、可 bootstrap 的规范展示状态 |
| `chat/runtime/event/ChatRunEventStore` | 分配严格递增序号，保存有界事件窗口并管理实时订阅 |
| `chat/runtime/agui/AguiEventJsonCodec` | 编码正式事件并补充 ChatRun 元数据 |
| `chat/runtime/agui/AguiBootstrapEncoder` | 从规范快照合成恢复事件，不写回正式事件窗口 |
| `chat/runtime/ChatRunInstance` | 组织阶段生命周期、事件/快照提交和业务终态 |
| `chat/service/impl/ChatServiceImpl` | 建立 SSE 连接；只挂载/关闭事件订阅，不拥有 AgentScope 执行 |

## 2. AgentScope 调用边界

内部 ChatRun 已经选定应用和 Agent，使用 `HarnessAgent#streamEvents` 的 v2 `AgentEvent` 流，不使用 deprecated
的 v1 `agent.stream()`，也不通过 `HarnessGateway` 二次路由。`RuntimeContext` 的状态身份固定为：

```text
userId    = ChatSessionEntity.userId
sessionId = ChatSessionEntity.id
```

`runId` 是一次逻辑回合，`aguiRunId` 是一个 AG-UI phase，两者都不是 AgentScope 多轮状态会话 ID。外部 Channel
仍可通过 `HarnessGateway` 路由；该路径不属于本文的 ChatRun SSE 链路。

## 3. 事件解释

`AgentEventInterpreter` 按 phase 创建，维护文本、推理和工具调用的配对状态。一次输入产生两类结果：

1. 零到多个 `AguiEvent`，供实时传输；
2. 一个 `ExecutionSnapshotDelta`，供规范快照累积。

主要映射如下：

| AgentScope 事件 | AG-UI 输出 | 快照变化 |
| :--- | :--- | :--- |
| 根 `AGENT_START` | `RUN_STARTED` | 无 |
| `TEXT_BLOCK_DELTA` | `TEXT_MESSAGE_START/CONTENT` | 追加助手文本 |
| `THINKING_BLOCK_DELTA` | `REASONING_*`（启用时） | 追加推理文本 |
| `TOOL_CALL_START/DELTA` | `TOOL_CALL_START/ARGS` | 追加工具名称和参数 |
| `TOOL_RESULT_*` | `TOOL_CALL_END/RESULT` | 追加结果并完成工具状态 |
| `REQUIRE_USER_CONFIRM` | 暂存 `RUN_FINISHED(interrupt)` | 保存脱敏的待确认工具集合 |

子 Agent 事件带有 `source`，不能重复产生根 `RUN_STARTED`，也不能被当成根 Agent 的业务结束。文本、推理和工具
之间切换时必须先闭合当前消息块；恢复实例即使没有解释器内存 ID，也要能闭合快照中仍打开的内容块。

`REQUIRE_USER_CONFIRM` 产生的中断事件先暂存。只有 `AWAITING_CONFIRM` 数据库状态提交成功后才进入可见事件窗口；
失败时丢弃，避免前端事实领先于数据库事实。

## 4. 正式事件与序号

正式事件编码后在顶层补充：

```json
{
  "type": "TEXT_MESSAGE_CONTENT",
  "threadId": "session-id",
  "runId": "agui-phase-id",
  "chatRunId": "business-run-id",
  "seq": 42,
  "messageId": "message-id",
  "delta": "..."
}
```

- `threadId`：业务 Session ID。
- `runId`：当前 `aguiRunId`，一次 HITL phase 一个值。
- `chatRunId`：跨 phase 稳定的业务 Run ID。
- `seq`：同一 ChatRun 内严格递增的正式事件序号。
- SSE `id`：`{chatRunId}:{seq}`。

事件必须先在 `ChatRunEventStore` 中取得序号，再编码并发布。回放到实时订阅的切换在同一缓冲区临界区完成，
避免漏事件；慢订阅者只关闭自己，不反向阻塞或取消业务 Run。

## 5. 快照与 bootstrap

正式事件是有限窗口，`ExecutionSnapshot` 才是展示恢复的持久化事实。它保存文本、推理、工具结果、消息开闭状态
和脱敏后的待确认工具信息。完整 `ToolUseBlock` 仍只存在于 AgentScope state，不进入快照或 SSE。

bootstrap 流程：

1. 读取内存实例快照；实例不存在时读取数据库快照。
2. 取得快照对应的事件高水位 `H`。
3. 合成带 `bootstrap=true`、`bootstrapSeq=H` 的 AG-UI 事件。
4. 从 `seq > H` 继续正式事件回放和实时订阅。

bootstrap 事件只用于重建前端状态，不分配正式 `seq`，也不写回事件窗口。等待确认时补发 interrupt；业务终态时
补发相应终态。完整确认上下文必须从 AgentScope state 读取，不能由展示快照伪造。

## 6. 业务完成与源流排空

根 Agent 的 `AGENT_END` 表示主回答及 Agent 状态已经保存，是 AG-UI 业务完成边界。此时可以提交助手消息和
ChatRun 终态，并在事务提交后发布 `RUN_FINISHED` 或 `RUN_ERROR`。

它不表示 AgentScope Flux 已结束。记忆整理中间件和 Sandbox 清理可能仍在继续，因此：

- 业务终态写入数据库并发布终态事件，让客户端及时结束当前展示；
- 不 dispose 底层 AgentScope 订阅；
- 源流 complete、error 或 cancel 后再执行 Workspace 审计和资源清理；
- 业务终态已提交且最终源流排空后完成稳定的实例级 `drainedSignal`，Coordinator 此时才摘除实例；
- 根事件后的后处理失败不反向覆盖已提交的 `COMPLETED`。

HITL phase 收到确认事件后继续等待根 `AGENT_END`，确认 AgentScope 已保存 `ASKING` 状态；执行适配器随后在根事件处
结束当前适配流，取消尚未订阅的记忆尾部。适配流终止并完成 Workspace 审计后再提交 `AWAITING_CONFIRM`，
根事件前的确认按状态冲突拒绝；进入待确认后完成校验和数据库 CAS，新的 `streamEvents` 可以立即启动。

普通下一条消息遵循相同原则：前端可在上一 Run 业务完成后创建并挂载新 Run，但同一 Session 的 AgentScope
源流由 Coordinator 按实例级 `drainedSignal` 尾链启动。该排序不阻塞其他 Session，也不是 Workspace 执行锁。

## 7. SSE 所有权

`ChatServiceImpl` 只持有 `ChatRunEventSubscription`。SSE completion、timeout 和 error 回调只执行订阅关闭：

```text
subscription.close()
```

浏览器切换会话、关闭页面或网络断开不会 dispose AgentScope Flux。用户主动停止必须调用 Run stop API，由
`ChatRunCoordinator` 按 `(ChatSession.userId, ChatSession.id)` 中断 Agent，并在宽限期后决定是否强制取消源流。

SSE 收到业务终态事件后可以完成当前 HTTP 连接；这与后台源流是否排空无关。

## 8. 持久化与历史展示

- 助手文本和工具调用保存到同一条 assistant 消息，复用现有 `content` 与 `tool_call` 字段。
- 最终消息、Run 终态和 Session 最后消息时间在同一事务中提交。
- `STOPPED` / `FAILED` 存在部分文本或工具结果时仍保存助手消息。
- 历史展示从持久化消息构造工具和文本内容块；实时/断线恢复则以 Run 快照和 AG-UI 事件为准。
- 推理是否进入最终历史消息是产品策略，不影响 Run 快照在执行期的恢复职责。

## 9. 验证

至少覆盖：

- 文本、推理和工具事件的 START/CONTENT/END 配对；
- 子 Agent 事件不会提前产生根 Run 终态；
- 正式事件 `seq` 严格递增，bootstrap 高水位与后续回放无缝衔接；
- 待确认事件只在数据库状态提交成功后可见；
- SSE 断开只关闭事件订阅，AgentScope Flux 继续；
- 根 `AGENT_END` 后业务及时完成，延迟的记忆尾部继续排空；
- 后处理失败不改变已提交业务终态；
- HITL 新旧 phase 不重叠；
- 同一 Session 的下一 Run 不等待上一 Run 的记忆尾部，核心状态调用仍由 AgentScope 串行保护；
- JSON 字段使用前端所需的 camelCase，并包含 `chatRunId` 和 `seq`。

执行：

```shell
mvn -pl lambda-fusion-ai test
mvn -pl lambda-fusion-ai -am compile
```
