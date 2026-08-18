# ChatRun 与 AgentScope 执行边界设计

> 本文定义 Lambda Fusion 内部 ChatRun 调用 AgentScope 时的会话身份、Gateway 边界、完成语义和锁边界。
> 本次改造不修改 AgentScope，不迁移既有 Agent state，也不引入持久化记忆任务。
>
> 状态：目标设计。完成本文改造前，现有代码中仍可能存在 Gateway 分支、单一终结信号、覆盖完整 Flux 的
> 交互超时，以及在业务终结路径执行 Workspace 审计等过渡实现。

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

### 2.6 同会话完整源流需要按排空顺序启动

业务终态早于源流排空后，用户可能立即发送下一条消息。虽然数据库中已经没有非终态 Run，直接订阅下一条源流
仍会让它与上一条记忆尾部重叠。Sandbox 模式可能在 AgentScope guard 上等待，HOST 模式则可能并发读写同一用户
记忆文件。

因此 `ChatRunCoordinator` 按 `(tenantId, userId, sessionId)` 维护进程内的源流尾链：新 Run 可以创建并建立 SSE，
但只有前一条源流的排空回调完成后才能真正订阅下一次 `streamEvents`。这是一条基于完成信号的非阻塞启动顺序，
不持有线程锁、数据库锁或 Workspace 锁，也不替代 AgentScope 对 state、文件和 Sandbox 的内部保护。

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
- 同一状态会话的相邻 ChatRun 不得重叠执行完整 AgentScope 源流。

### 3.2 本次不做

- 不修改 `_agentscopeV2` 或要求 AgentScope 新增公开 API。
- 不迁移旧的 `(userId, gw-*)` Agent state。
- 不保留旧状态 ID 的双读、回退或长期兼容分支。
- 不新增数据库记忆任务、事务 outbox、Redis Stream 或后台记忆 Worker。
- 不保证进程退出后继续未完成的记忆整理。
- 不把当前单实例 ChatRun 扩展成多节点可迁移执行。
- 不用新的应用级执行锁替代已经删除的 Workspace 全流程锁。
- 不实现跨节点的源流尾链；当前单实例约束下只做进程内顺序启动。

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

### 6.1 两类完成信号

`ChatRunInstance` 分别维护：

- `terminalSignal`：业务 Run 已进入最终状态，供 API 返回和 SSE 终态使用。
- `drainedSignal`：最终阶段的 AgentScope 源流和后处理全部结束，供实例摘除、关机等待和资源统计使用。

`terminalSignal` 不得触发底层 `Disposable.dispose()`。

最终实例的 `drainedSignal` 是 `terminalSignal` 与最终 phase-drained 的汇合信号：数据库终结重试尚未成功时，
即使源流已经排空也不能摘除实例；反过来，业务先完成时也要继续保留实例到源流和审计结束。

Coordinator 另以每次源流的 phase-drained 信号推进会话尾链。phase-drained 表示源流终止、Sandbox release 和
该 phase 的 Workspace 审计均已结束。最终 phase 的该信号汇入实例 `drainedSignal`；等待确认的中间 phase 排空
只推进当前实例，不摘除它。

### 6.2 普通完成

```text
Agent 主流程
    -> 保存 Agent state
    -> root AGENT_END
        -> 提交助手消息和 Run=COMPLETED
        -> 发送 RUN_FINISHED
        -> terminalSignal
    -> MemoryFlush / MemoryMaintenance
    -> AgentScope Flux 终止并释放 Sandbox
    -> Workspace 审计
    -> drainedSignal
```

根事件必须属于当前 phase 的根调用，不能把子 Agent 的 `AgentEndEvent` 当成 ChatRun 业务完成。
同一 Session 的下一条 Run 即使已经创建，也只能在该源流排空后开始订阅。

### 6.3 HITL

收到 `REQUIRE_USER_CONFIRM` 时只记录待确认工具。根 `AGENT_END` 到达且状态保存完成后：

1. 将 Run 迁移为 `AWAITING_CONFIRM`。
2. 发布当前 AG-UI phase 的 `RUN_FINISHED(interrupt)`。
3. 将本地 phase 标记为 `AWAITING_CONFIRM_DRAINING`。
4. 继续等待当前 AgentScope Flux 排空。

确认卡片随 `AWAITING_CONFIRM` 落库即可展示，不等待排空。用户在旧 phase 排空前即可提交确认：请求被受理并
暂存为待启动确认，确认 API 立即返回，但 `advanceConfirmation` 的数据库 CAS（`AWAITING_CONFIRM -> RUNNING`）
与「订阅下一次 `streamEvents`」整体推迟到旧 phase 完成 phase-drained 之后才执行。排空期间 Run 保持
`AWAITING_CONFIRM`（业务终态、数据库中无新增非终态 Run），因此新 phase 的订阅严格满足第 2.6 节
「数据库中已无非终态 Run」的前提，两个 phase 不会共享同一 Sandbox 或调用期资源，也不会重叠执行。

若同一 Run 在排空期间收到多个确认，只登记一个待启动确认；排空后恰好执行一次 `advanceConfirmation` 并启动
一次新 phase（幂等由 `advanceConfirmation` 的 `phaseNo` 守卫兜底，重复确认返回 `resumed=false` 不再启动）。

### 6.4 停止和失败

- 根 `AGENT_END` 前失败：Run 收敛为 `FAILED`，保存已有部分输出。
- 根 `AGENT_END` 后的记忆或维护失败：Run 保持 `COMPLETED`，记录后处理错误。
- 用户停止：先按 `(userId, sessionId)` 调用 AgentScope `interrupt`，宽限期后仍未结束才 dispose。
- 所有 complete、error、cancel 路径都必须完成 phase-drained；最终 phase 还要完成实例 `drainedSignal`，
  不能只在 `onComplete` 清理。

### 6.5 会话源流尾链

尾链只负责决定“什么时候订阅下一条源流”，不包裹源流执行，也不占用线程等待：

```text
enqueue(sessionKey, startAction)
    -> 原子取得并替换 sessionKey 当前 tail
    -> predecessor 完成后在 ChatRun 调度器执行 startAction
    -> source complete/error/cancel
    -> Sandbox release
    -> phase Workspace audit
    -> 完成当前 tail 节点
    -> compare-and-remove 已无后继的 sessionKey
```

约束：

- `sessionKey = (tenantId, userId, ChatSession.id)`，不能使用 appId 或 Workspace Agent ID。
- 普通新 Run 和 HITL 续跑共用同一条尾链。
- Run 在排队期间被停止或已终态时，跳过 `startAction`，但必须完成自己的尾链节点。
- 前驱 error、cancel、审计失败均不能阻塞后继；错误各自记录，尾链按完成语义继续。
- 排队等待时间不计入 `max-run-duration`；交互计时从实际订阅当前 phase 时开始。
- 排队等待启动的 Run/phase 计入交互容量，避免请求在内存中无上限堆积；已经业务终态、仅等待排空的实例不占
  交互容量，但仍计入单独的 draining 资源统计。
- `terminalSignal` 不能释放尾链；只有 phase-drained 可以释放。

该尾链是当前“单实例执行、内存事件缓冲”范围内的进程内设施。若未来允许同一 Session 在多节点接续，需要另行
设计执行 owner/lease，不能把 Workspace 分布式写锁拿来充当调度器。

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
| 内部同会话源流 | Coordinator 的非阻塞排空尾链 | 前一条源流及 phase 排空后处理 |
| Sandbox | AgentScope Sandbox guard | AgentScope 源流生命周期 |
| 管理端 Workspace 写入 | Lambda Fusion Workspace 短写锁 | 单次实际写入 |
| Agent 工具共享文件写入 | 文件系统原子能力；必要时仅锁写工具 | 单次工具写入 |
| Workspace 审计 | 源流排空后使用短写锁 | 单次审计快照 |

禁止：

- 在 `AgentExecutionAdapter#stream` 外包裹应用级 Workspace 执行锁。
- 在模型调用、MemoryFlush 模型调用或整个 Flux 生命周期内持有 Lambda Fusion Workspace 写锁。
- 在 AgentScope Sandbox guard 内再次获取同一个非重入分布式锁。
- 为同一 ChatRun 同时保留 Gateway 门闩和另一把阻塞式 Session/Workspace 执行锁。

移除应用级全流程锁后，同一应用的不同用户可以并行调用模型。共享文件是否允许并行写入由实际文件路径和
存储后端决定，不能再用“串行整个应用”掩盖写冲突。会话尾链只约束同一 `(tenantId, userId, sessionId)`，
不会把同一应用的不同用户串行化。

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

- 拆分业务终态与 phase/实例排空。
- 根 `AGENT_END` 提交普通完成或待确认状态。
- 保留业务完成后的底层订阅。
- 分离交互超时与后处理观测。
- 将 Workspace 审计移到源流终止之后。
- 对排空前到达的确认保存待启动消息，排空后启动下一 phase。

### 10.4 `ChatRunCoordinator`

- 业务终态后不立即丢失仍在排空的实例。
- 交互实例参与容量统计；仅排空中的最终态实例不占用交互容量，但仍受关机和资源清理管理。
- 等待确认的实例继续保留在活动注册表中。
- 最终 `drainedSignal` 后按 `(runId, instance)` 删除，避免旧实例删除并发创建的新实例。
- 按 Session 维护非阻塞源流尾链；`CREATED` Run 或确认 phase 等前驱排空后才启动。
- 启动失败、取消和关机都必须完成尾链节点，不能让同会话后续 Run 永久等待。

### 10.5 保留项

- `AgentFactory` 继续把 Agent 注册到共享 Gateway，供外部 Channel 使用。
- AgentScope MemoryFlush、MemoryMaintenance、WorkspaceManager 和 Sandbox 生命周期实现不变。
- `WorkspaceStorage#withWriteLock` 继续服务管理端和审计等短写操作。

### 10.6 实施顺序

1. 先增加特征测试，固定根 `AGENT_END` 早于记忆尾部、源流终止后 Sandbox 才释放等已确认语义。
2. 改造 `AgentExecutionAdapter` 和 `ChatRunInstanceFactory`，让内部 ChatRun 直连 Agent，并统一状态身份。
3. 在 `ChatRunInstance` 引入 phase-drained、`terminalSignal`、`drainedSignal` 和单槽待启动 phase。
4. 在 `ChatRunCoordinator` 引入按 Session 的源流尾链，再把普通 Run 与 HITL 启动统一接入该入口。
5. 把交互超时截止点改到根 `AGENT_END`，把 Workspace 审计移动到 phase 排空路径。
6. 完成身份、生命周期、HITL、停止、容量与并发测试后，再按第 11 节发布边界上线。

实施中不允许出现“先移除 Gateway 门闩、后补会话尾链”的可发布中间态，否则业务终态提前后，同一 Session 的
下一 Run 可能与上一条记忆尾部重叠。两个改动必须作为同一个原子版本交付。

## 11. 发布边界

本次明确不迁移旧 Agent state：

- 发布后，内部 ChatRun 只读取 `(userId, ChatSession.id)`。
- 不读取、复制或删除旧的 `(userId, gw-*)` 状态。
- 不增加运行期兼容判断。
- 发布前结束或停止所有活动 Run，尤其是 `AWAITING_CONFIRM` Run。
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

- 待确认状态在根 `AGENT_END` 后可见，不等待记忆模型完成。
- 排空前提交确认只推进一次数据库 phase，并只登记一个待启动阶段。
- 旧 phase 排空后恰好启动一次新 phase。
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
