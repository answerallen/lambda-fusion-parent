# ChatRun 与 AgentScope 执行边界设计

> 本文定义 Lambda Fusion 内部 ChatRun 调用 AgentScope 时的会话身份、Gateway 边界、完成语义和锁边界。
> 本次改造不修改 AgentScope，不迁移既有 Agent state，也不引入持久化记忆任务。
>
> 状态：当前实现边界。`chat.runtime` 是业务层，AgentScope 是执行与状态底座。

## 1. 结论

内部 ChatRun 已经持有目标应用、Agent、用户和业务会话，不再经过 `HarnessGateway` 二次路由，直接使用
`HarnessAgent#streamEvents`。外部钉钉、飞书、企微等通道继续通过 `HarnessGateway` 完成路由、出站地址记录和
通道会话串行化。

AgentScope 状态统一使用以下地址：

```text
(ChatSession.userId, ChatSession.id)
```

Lambda Fusion 不再根据 `MsgContext.canonicalKey()` 拼接 `gw-*` 状态会话 ID，也不复制
`HarnessGateway` 的私有会话 ID 生成规则。

一次调用包含两个完成边界：

```text
root AGENT_END       业务边界：主回答及 Agent 状态已保存
AgentScope Flux 结束 资源边界：记忆尾部、中间件和 Sandbox 清理已结束
```

业务 Run 可以在第一个边界完成；底层订阅必须保留到第二个边界。同一状态会话的下一条 AgentScope 源流也要
等前一条排空后再订阅。Lambda Fusion 不在模型调用期间持有 Workspace 写锁。

## 2. 已确认的框架语义

### 2.1 Gateway 会重新派生会话 ID

`HarnessGateway#runStream` 不直接使用调用方的业务会话 ID。它先计算：

```text
gateKey = MsgContext.canonicalKey()
sessionId = "gw-" + SessionIdUtils.deterministicHash(gateKey)
```

生成方法是 `HarnessGateway` 的私有实现，Gateway 没有公开从 `MsgContext` 查询实际 `sessionId` 的接口。
调用方重复计算该值只能保证与当前版本偶然一致，不能形成稳定 API 契约。

当前 ChatRun 构造的 `canonicalKey` 同时包含 channel、tenant、业务 session、路由 Agent ID 和应用 ID。
任一字段或 canonical 规则变化都会生成新的 `gw-*`，导致 Gateway 写入的状态槽与 Lambda Fusion 读取、
中断或保存的状态槽不一致。

### 2.2 Gateway 的会话门闩覆盖完整 Flux

`HarnessGateway` 的 `SessionTurnGate` 在订阅时按 `gateKey` 获取许可，在返回的 Flux `doFinally` 时释放。
AgentScope 的记忆整理中间件拼接在 Agent 主流程之后，因此该门闩也覆盖记忆模型调用。结果是同一个
Gateway 会话的下一阶段可能等待记忆尾部完成。

### 2.3 ReActAgent 已按状态槽串行化核心调用

`ReActAgent` 使用 `(userId, sessionId)` 作为调用串行键。同一状态槽的 Agent 主调用顺序执行，不同状态槽
可以并行。内部 ChatRun 直接传递业务会话 ID 后，仍然保留 AgentScope 对 Agent state 的原生保护。

该串行键位于 `ReActAgent` 核心生命周期内。`MemoryFlushMiddleware`、`MemoryMaintenanceMiddleware` 等
`HarnessAgent` 中间件包装在核心流外层，因此核心串行许可释放后，记忆尾部仍可能继续。它不能单独保证两个
完整的 `HarnessAgent#streamEvents` 源流不重叠。

### 2.4 root AGENT_END 先于记忆尾部

Agent 主流程在保存当前状态后发出根 `AgentEndEvent`。`MemoryFlushMiddleware` 和
`MemoryMaintenanceMiddleware` 在该主流程之后继续执行，整个 Flux 稍后才 complete。因此
`AGENT_END` 可以作为业务回答边界，不能作为全部资源已经释放的证明。

注意：`AgentEndEvent` 是在核心生命周期的 `doFinally` 中构造的，但下游订阅者能否收到取决于终止方式。
正常完成时订阅者能收到根 `AGENT_END`；而 error 路径下 `sink.error` 先终止流，`doFinally` 里的
`sink.next(AgentEndEvent)` 打在已终止的 sink 上会被丢弃，cancel 同理——订阅者只看到 `onError`/取消，
看不到根 `AGENT_END`。因此 `ChatRunInstance` 的 error/cancel 路径不能等待根 `AGENT_END`，必须在
`onError`/取消回调中直接按第 6.4 节收敛；只有正常完成路径才把根 `AGENT_END` 作为业务回答边界。

### 2.5 Sandbox 生命周期由 AgentScope 管理

`HarnessAgent` 使用自身的 Sandbox 生命周期中间件覆盖 acquire、执行、快照和 release。AgentScope 明文要求调用方
不要提前关闭返回的 `SandboxLease`（lease 由框架自动关闭）。guard 本身是 `executionGuard(...)` 可注入的扩展点，
框架并未明文禁止替换；本设计中 Lambda Fusion 选择不查询、不替换、不提前释放该 guard。Sandbox 是否仍被占用
只能以 AgentScope 源流终止为准。

### 2.6 同会话下一轮不等待后处理排空

业务终态早于源流排空后，用户可能立即发送下一条消息。下一条 Run 必须立即订阅，不能等待上一轮的 MemoryFlush、
MemoryMaintenance、Sandbox release 或 Workspace 审计，否则后处理模型耗时会直接表现为下一轮对话无法启动。

AgentScope `ReActAgent` 已按 `(userId, sessionId)` 串行核心生命周期并在根 `AGENT_END` 后释放状态槽；记忆中间件使用
防御性上下文副本、隔离键节流和文件写入保护处理尾部。因此 `ChatRunCoordinator` 不再重复串行完整 Flux，
`drainedSignal` 只用于实例摘除和资源清理。

## 3. 目标与非目标

### 3.1 必须保证

- 内部 ChatRun 直接调用已经选定的 `HarnessAgent`。
- Agent 状态、HITL、停止和保存统一使用 `(userId, ChatSession.id)`。
- 外部 Channel 仍由 `HarnessGateway` 路由，不影响现有渠道接入。
- 根 `AGENT_END` 后可以提交业务终态和发送 `RUN_FINISHED`。
- 业务完成后不取消 AgentScope 订阅，记忆尾部继续运行。
- 只有 AgentScope 源流终止后才执行 Workspace 审计和实例资源清理。
- 模型调用期间不持有 Lambda Fusion Workspace 写锁。
- 记忆和 Sandbox 的实现仍由 AgentScope 维护。
- 同一 HITL Run 的两个阶段不得重叠执行。
- 同一状态会话的相邻 ChatRun 立即订阅，核心状态调用由 AgentScope 串行保护。

### 3.2 本次不做

- 不修改 `_agentscopeV2` 或要求 AgentScope 新增公开 API。
- 不迁移旧的 `(userId, gw-*)` Agent state。
- 不保留旧状态 ID 的双读、回退或长期兼容分支。
- 不新增数据库记忆任务、事务 outbox、Redis Stream 或后台记忆 Worker。
- 不保证进程退出后继续未完成的记忆整理。
- 不把当前单实例 ChatRun 扩展成多节点可迁移执行。
- 不用新的应用级执行锁替代已经删除的 Workspace 全流程锁。
- 不新增进程内或跨节点的完整源流尾链。

## 4. 调用边界

### 4.1 内部 ChatRun

```text
ChatController
    -> ChatServiceImpl
    -> ChatRunCoordinator
    -> ChatRunInstance
    -> AgentExecutionAdapter
    -> HarnessAgent.streamEvents(message, RuntimeContext)
```

`AgentExecutionAdapter` 每次调用创建新的 `RuntimeContext`：

```java
RuntimeContext.builder()
        .userId(userId)
        .sessionId(sessionId)
        .put(RuntimeProperty.KEY_TENANT_ID, tenantId)
        .put(RuntimeProperty.KEY_APP_ID, appId)
        .put(RuntimeProperty.KEY_LF_SESSION_ID, sessionId)
        .build();
```

其中 `sessionId` 取自 `ChatRunEntity.getSessionId()`，并与 `ChatSessionEntity.id` 一致；`runId` 只标识一次业务回合，
不能用作 AgentScope 多轮状态槽。`RuntimeProperty` 是 Lambda Fusion 的属性键常量类
（`com.lambda.fusion.ai.runtime.gateway.RuntimeProperty`），不是 AgentScope 框架类型；AgentScope 只提供
`put(String, Object)` 与 `put(Class<T>, T)` 两套属性机制。

### 4.2 外部 Channel

```text
外部平台消息
    -> Channel / ChannelRouter
    -> HarnessGateway.runStream(MsgContext, ...)
    -> 已注册的 HarnessAgent
```

外部通道没有 Lambda Fusion 的业务会话 ID 时，由 Gateway 根据 channel、room、thread 等信息派生会话 ID 是
正确用法。`AgentFactory` 仍按稳定 Agent ID 将 Agent 注册到共享 Gateway。

内部 ChatRun 是否直连与 `lambda.fusion.ai.gateway.enabled` 无关；该开关只控制 Gateway 与外部通道基础设施。

### 4.3 子 Agent

`HarnessAgent` 自身为子 Agent 暴露和消息回送维护内部 Gateway。`FusionSubagentGateway#configureAgent` 继续配置
`agent.gateway()`，不依赖 ChatRun 经过共享 `HarnessGateway`。父会话 ID 改为业务 `ChatSession.id` 后，
子 Agent 注册、恢复和父会话归属使用同一状态身份。

## 5. 会话身份

| 标识 | 事实来源 | 用途 |
| :--- | :--- | :--- |
| `sessionId` | `ChatSessionEntity.id` | 多轮业务会话及 AgentScope 状态槽 |
| `userId` | `ChatSessionEntity.userId` | AgentScope 用户命名空间 |
| `appId` | `ChatSessionEntity.appId` | 选择 Agent 和 Workspace |
| `tenantId` | 已校验的 Session | 租户上下文和 Workspace 命名空间 |
| `runId` | `ChatRunEntity.id` | 一次用户消息触发的逻辑回合 |
| `aguiRunId` | ChatRun phase | 一个 AG-UI 执行阶段 |
| `phaseNo` | ChatRun | HITL 阶段和确认幂等 |

禁止在 ChatRun 路径中再出现以下状态身份：

```text
"gw-" + deterministicHash(MsgContext.canonicalKey())
```

状态操作只调用 AgentScope 的公开接口。这些 `(userId, sessionId)` 形态定义在 `ReActAgent` 上，需经
`HarnessAgent.getDelegate()` 取得后调用（`HarnessAgent` 自身仅有已废弃的无参重载）：

```java
ReActAgent delegate = agent.getDelegate();
delegate.getAgentState(userId, sessionId);
delegate.interrupt(userId, sessionId);
delegate.saveAgentState(userId, sessionId);
```

也可以使用对应的 `RuntimeContext` 重载，但不能从 Agent 实例的“最近活动上下文”推断目标会话；同一 Agent
实例可以并发服务不同用户和会话。

## 6. 生命周期

### 6.1 业务终态与排空信号

业务 Run 的最终状态由数据库状态和终态事件表达，不在实例内维护重复的完成信号。
`ChatRunInstance` 只维护 `drainedSignal`：当前 Run 的最终阶段、业务终态和后处理全部结束后完成，
供实例摘除、关机等待和资源统计使用。

`drainedSignal` 是稳定的实例级信号：数据库终结重试尚未成功时，即使源流已经排空也不能摘除实例；反过来，
业务先完成时也要继续保留实例到最终源流和审计结束。HITL 中间 phase 排空不会完成该信号，确认后的新 phase
继续由同一个实例承载。相邻 Run 的启动不消费该信号，避免把记忆尾部耗时传递给下一轮交互。

### 6.2 普通完成

```text
Agent 主流程
    -> 保存 Agent state
    -> root AGENT_END
        -> 提交助手消息和 Run=COMPLETED
        -> 发送 RUN_FINISHED
    -> MemoryFlush / MemoryMaintenance
    -> AgentScope Flux 终止并释放 Sandbox
    -> Workspace 审计
    -> drainedSignal
```

根事件必须属于当前 phase 的根调用，不能把子 Agent 的 `AgentEndEvent` 当成 ChatRun 业务完成。
同一 Session 的下一条 Run 即使已经创建，也只能在该源流排空后开始订阅。

### 6.3 HITL

收到根 `REQUIRE_USER_CONFIRM` 时只记录脱敏待确认工具和中断事件。`AgentExecutionAdapter` 继续等待根
`AGENT_END`，以确认 AgentScope 已保存 `ASKING` 状态；随后在该事件处结束当前 HITL 交互源流，使 AgentScope
通过 `concatWith` 追加的 MemoryFlush/MemoryMaintenance 尾部不被订阅。适配流终止并完成 Workspace 审计后，才把
包含 `pendingTools` 的快照与事件水位写入仍为 `RUNNING` 的 ChatRun，随后发布当前 AG-UI phase 的
`RUN_FINISHED(interrupt)`。

必须保持以下不变量：`snapshot.pendingTools 非空 => sourceActive == false`。旧 phase 排空前到达的确认请求按
`CHAT_RUN_STATE_CONFLICT` 拒绝。确认在实例锁内先处理旧 phase 幂等守卫，再读取 AgentScope `ASKING` 状态并做三方 ID
校验，最后以 `phaseNo` CAS 推进 phase N+1、同事务清空旧投影并立即启动新阶段。旧 phase 重放直接返回
`resumed=false`，不得读取当前新 phase 的 AgentState。

### 6.4 停止和失败

- 根 `AGENT_END` 前失败：Run 收敛为 `FAILED`，保存已有部分输出。
- 根 `AGENT_END` 后的记忆或维护失败：Run 保持 `COMPLETED`，记录后处理错误。
- 用户停止：本机持有活动实例时按 `(userId, sessionId)` 调用 AgentScope `interrupt`，宽限期后仍未结束才 dispose；
  本机没有 `RUNNING` 实例时只提交 ChatRun 业务终态，不恢复活动 Agent，也不发送跨节点中断；
  快照含待确认工具时可清理 AgentScope 持久化状态中的未决工具，但不调用 interrupt。
- 所有 complete、error、cancel 路径都必须执行源流终止清理；最终业务终态还要完成实例 `drainedSignal`，
  不能只在 `onComplete` 清理。

### 6.5 Run 启动与排空

Run 注册后立即在 ChatRun 调度器订阅 AgentScope 源流：

```text
register(runInstance)
    -> 订阅 drainedSignal，仅用于最终摘除实例
    -> 在 ChatRun 调度器立即执行 startAction
    -> root AGENT_END，提交业务终态
    -> 下一 Run 可立即注册和启动
    -> source complete/error/cancel
    -> Sandbox release
    -> Workspace audit
    -> 完成当前 Run 的 drainedSignal
    -> 按 (runId, instance) 摘除注册实例
```

约束：

- HITL 续跑仍由同一实例在旧 phase 已排空后启动，避免同一 Run 的两个 phase 重叠。
- Run 在异步启动前已被停止/终结时不再建立有效源流，终态完成后按正常排空语义摘除实例。
- `max-run-duration` 从实际订阅当前 phase 时开始，只覆盖到根 `AGENT_END`。
- 业务终态不摘除仍在后处理的实例；只有实例级 `drainedSignal` 完成后才能摘除。
- 相邻 Run 不等待该信号；同会话核心状态安全由 AgentScope `(userId, sessionId)` 状态槽负责。

若未来明确要求同一活动调用跨节点接续，应优先采用 AgentScope 提供的公开执行能力，并单独验证外部工具幂等；不得在
`chat.runtime` 追加 owner/lease/heartbeat 或把 Workspace 分布式写锁、`drainedSignal` 当作分布式调度器。

## 7. 超时

主交互和后处理使用不同计时边界：

- `max-run-duration` 只约束从 phase 启动到根 `AGENT_END` 的交互阶段。
- 根 `AGENT_END` 后取消交互超时，不因记忆整理耗时改变 Run 终态。
- 后处理首先记录耗时告警和指标。若后续增加硬超时，只能取消尾部并记录后处理失败，不能把
  `COMPLETED` 改为 `FAILED`。

不能继续用一个 Reactor `timeout()` 包围整个 AgentScope Flux。

## 8. 并发与锁

| 区域 | 并发保护 | 锁窗口 |
| :--- | :--- | :--- |
| Agent 会话状态 | AgentScope `ReActAgent` 状态槽串行化 | Agent 主调用 |
| 外部通道会话 | `HarnessGateway.SessionTurnGate` | 外部通道完整 turn |
| 内部 ChatRun 状态 | Session 行锁、Run 状态和实例锁 | 对应事务或实例原子操作 |
| Sandbox | AgentScope Sandbox guard | AgentScope 源流生命周期 |
| 管理端 Workspace 写入 | Lambda Fusion Workspace 短写锁 | 单次实际写入 |
| Agent 工具共享文件写入 | 文件系统原子能力；必要时仅锁写工具 | 单次工具写入 |
| Workspace 审计 | 源流排空后使用短写锁 | 单次审计快照 |

禁止：

- 在 `AgentExecutionAdapter#stream` 外包裹应用级 Workspace 执行锁。
- 在模型调用、MemoryFlush 模型调用或整个 Flux 生命周期内持有 Lambda Fusion Workspace 写锁。
- 在 AgentScope Sandbox guard 内再次获取同一个非重入分布式锁。
- 为同一 ChatRun 同时保留 Gateway 门闩和另一把阻塞式 Session/Workspace 执行锁。

移除应用级全流程锁后，同一应用的不同用户可以并行调用模型。同一会话的核心调用由 AgentScope 状态槽串行，
后处理与下一轮交互可以重叠。共享文件安全由 AgentScope 的隔离键、文件锁和存储后端原子能力负责，
不能再用串行完整应用或完整源流掩盖写冲突。

## 9. Workspace 审计

`WorkspaceAuditRecorder` 不在 `finalizeTerminal` 中执行。审计必须满足：

1. AgentScope 源流已经 complete、error 或 cancel。
2. Sandbox guard 已释放或完成快照。
3. 使用当前 phase 的 `phaseStartedAt`，不使用实例创建时刻覆盖所有 phase。
4. 审计失败不回滚已经提交的 ChatRun 业务终态。

当前按 `updatedAt > phaseStartedAt` 扫描变更只能作为最佳努力记录。同一应用的多个用户并发修改共享文件时，
可能把别的执行产生的文件归入本次审计。若审计以后承担合规责任，应基于文件系统实际 mutation 记录路径，
不能继续依赖时间窗口推断。

## 10. 组件改造

### 10.1 `AgentExecutionAdapter`

- 删除 `HarnessGateway`、`MsgContext`、`OutboundAddress` 和 `SessionIdUtils` 依赖。
- 删除 `routingAgentId`、`gatewayContext`、`stateSessionId` 和 Gateway/直连双分支。
- 每次 `stream` 新建 `RuntimeContext` 并直调 `HarnessAgent#streamEvents`。
- 状态读取、中断和保存统一使用业务 `(userId, sessionId)`。
- 保留 AgentEvent 源流、HITL 状态读取和子 Agent 暴露记录职责。

### 10.2 `ChatRunInstanceFactory`

- 不再注入 `ObjectProvider<HarnessGateway>`。
- 不再向 Adapter 传 Gateway 和稳定路由 Agent ID。
- 继续从 `AgentFactory` 获取按应用和租户构建的 Agent。

### 10.3 `ChatRunInstance`

- 拆分业务终态与实例排空。
- 根 `AGENT_END` 提交普通完成；HITL 在根事件处结束适配流，待该唯一源流终止后提交待确认快照。
- 保留业务完成后的底层订阅。
- 分离交互超时与后处理观测。
- 将 Workspace 审计移到源流终止之后。
- 排空前到达的确认按状态冲突拒绝；待确认快照写入后可立即确认并启动下一 phase。

### 10.4 `ChatRunCoordinator`

- 业务终态后不立即丢失仍在排空的实例。
- 交互实例参与容量统计；仅排空中的最终态实例不占用交互容量，但仍受关机和资源清理管理。
- 快照含待确认工具的实例继续保留在活动注册表中。
- 最终 `drainedSignal` 后按 `(runId, instance)` 删除，避免旧实例删除并发创建的新实例。
- 新建的 `RUNNING` Run 注册后立即异步启动，不等待其他实例排空。
- 启动失败、取消和关机都必须完成自身排空信号，避免实例注册表泄漏。

### 10.5 保留项

- `AgentFactory` 继续把 Agent 注册到共享 Gateway，供外部 Channel 使用。
- AgentScope MemoryFlush、MemoryMaintenance、WorkspaceManager 和 Sandbox 生命周期实现不变；Fusion 适配层仅在
  HITL 根 `AGENT_END` 处取消后续记忆尾段。
- `WorkspaceStorage#withWriteLock` 继续服务管理端和审计等短写操作。

### 10.6 实施顺序

1. 先增加特征测试，固定根 `AGENT_END` 早于记忆尾部、源流终止后 Sandbox 才释放等已确认语义。
2. 改造 `AgentExecutionAdapter` 和 `ChatRunInstanceFactory`，让内部 ChatRun 直连 Agent，并统一状态身份。
3. 在 `ChatRunInstance` 只保留稳定的实例级 `drainedSignal`，并保证 HITL 完整排空后才写入待确认投影。
4. `ChatRunCoordinator` 注册后立即异步启动 Run，`drainedSignal` 仅用于最终摘除实例。
5. 把交互超时截止点改到根 `AGENT_END`，把 Workspace 审计移动到 phase 排空路径。
6. 完成身份、生命周期、HITL、停止、容量与并发测试后，再按第 11 节发布边界上线。

不得再把 `drainedSignal` 复用为相邻 Run 的启动门闩；它只表达当前实例的资源排空状态。

## 11. 发布边界

本次明确不迁移旧 Agent state：

- 发布后，内部 ChatRun 只读取 `(userId, ChatSession.id)`。
- 不读取、复制或删除旧的 `(userId, gw-*)` 状态。
- 不增加运行期兼容判断。
- 发布前结束或停止所有活动 Run，尤其是快照仍含待确认工具的 Run。
- 使用持久化 StateStore 的既有会话在发布后的第一次对话按新状态槽开始。
- 回滚旧版本时会重新使用旧 `gw-*` 状态并忽略新状态槽，状态同样不连续；本次不提供双向同步。

该边界只影响 AgentScope 对话状态，不删除 ChatSession、ChatRun、ChatMessage 或 Workspace 数据。

## 12. 验证

### 12.1 身份和调用边界

- 内部 ChatRun 不调用 `HarnessGateway#runStream`。
- `RuntimeContext.userId/sessionId` 等于 Session 中的权威值。
- 状态读取、中断和保存使用相同 `(userId, sessionId)`。
- 代码和测试中不再计算 `gw-*`。
- 外部 Channel 仍能通过稳定 Agent ID 路由。

### 12.2 生命周期

- 记忆模型延迟时，根 `AGENT_END` 后仍立即提交并发送普通 Run 终态。
- 业务终态不会 dispose 仍在运行的记忆尾部。
- 尾部失败不会把 `COMPLETED` 改为 `FAILED`。
- `drainedSignal` 在业务终态提交、AgentScope 源流和 Workspace 审计都结束后完成。
- error、cancel 路径也能摘除排空实例。
- 前一 Run 根 `AGENT_END` 后立即创建下一 Run 时，下一源流只在前一源流排空后订阅。

### 12.3 HITL

- 根 `REQUIRE_USER_CONFIRM` 后必须继续等到根 `AGENT_END`，不得在 Agent state 保存前提前取消。
- HITL 根 `AGENT_END` 后不订阅记忆尾部；适配流终止后才提交待确认快照并发布确认卡片。
- 普通最终回答仍保留既有记忆尾部及完整排空语义。
- 旧 phase 重放不读取当前 AgentState；当前 phase 确认只推进一次并立即启动新 phase。
- 两个 phase 不同时持有同一个 Sandbox 生命周期资源。

### 12.4 锁

- 主模型和记忆模型调用期间没有 Lambda Fusion Workspace 写锁。
- 不同用户使用同一应用时可以并发进入模型调用。
- Sandbox 模式仍由 AgentScope guard 完成 acquire/release。
- Workspace 审计在 guard 释放后执行，不发生非重入锁自等待。

执行：

```shell
mvn -pl lambda-fusion-ai test
mvn -pl lambda-fusion-ai -am compile
```

## 13. 可观测性

每个 phase 至少记录以下时间点，并携带 `runId`、`phaseNo`、`sessionId`：

```text
agent_start
root_agent_end
business_state_committed
run_finished_emitted
agentscope_stream_terminated
workspace_audit_completed
instance_drained
```

核心指标：

```text
agent_core_duration
post_processing_duration
post_processing_failure_count
workspace_lock_wait_duration
draining_execution_count
session_source_queue_wait_duration
```

日志不得输出完整提示词、工具参数、Token 或 Agent state 内容。

## 14. 后续边界

当前设计只把记忆尾部从用户可见的完成路径中解耦，仍依赖本进程中的 AgentScope 订阅。如果以后出现“服务
重启后记忆整理也不能丢”的明确需求，应单独设计持久化记忆任务，并继续调用 AgentScope 的公开记忆组件；
不能在本次改造中预埋任务表、双实现或通用 outbox。
