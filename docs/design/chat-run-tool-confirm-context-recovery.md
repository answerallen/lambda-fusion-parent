# HITL 工具确认上下文丢失修复设计

> 目标：修复 Run 已能通过 bootstrap 重建待确认工具卡片，但确认时 AgentScope 中找不到对应 `ToolUseBlock`，最终错误进入 `FAILED / START_FAILED` 的问题。
>
> 本文只描述修复设计。实际代码改造完成前，本文中的目标流程和新增错误码均不代表当前实现已经具备。
> AgentScope 的直连方式、状态身份和 phase 排空边界以
> [ChatRun 与 AgentScope 执行边界设计](chat-run-agentscope-execution.md) 为准。

## 1. 问题现象

典型事件如下：

```text
RUN_STARTED (phaseNo=2, bootstrap=true)
REASONING_*
TOOL_CALL_START (toolCallId=call_xxx)
TOOL_CALL_ARGS  (toolCallId=call_xxx)
RUN_ERROR: 确认工具上下文不存在: call_xxx
           code=START_FAILED
```

这里的 `TOOL_CALL_START` 和 `TOOL_CALL_ARGS` 并不能证明完整工具执行上下文仍然存在。它们是服务端根据数据库中的 `RunSnapshot` 合成的 bootstrap 展示事件；真正恢复工具执行时，当前实现还需要从 AgentScope state store 中读取原始 `ToolUseBlock`。

因此，错误不是前端遗漏 `toolCallId`，而是服务端的两份状态已经不一致：

| 状态 | 存储位置 | 用途 | 当前现象 |
| :--- | :--- | :--- | :--- |
| 脱敏工具快照 | `la_ai_chat_run.snapshot_json` | bootstrap 展示、确认请求 ID 校验 | 仍然存在 |
| 完整 `ToolUseBlock(ASKING)` | AgentScope state store | 构造 `ConfirmResult`、继续 Agent 执行 | 不存在、不可读或无法匹配 |

## 2. 修复后的确认链路

确认链路已收敛为 `ChatRunInstance` 上的单一原子操作：

```text
ChatServiceImpl.confirm
        |
        v
ChatRunCoordinator.confirm          （loadOwned 已做归属校验）
  1. get 命中则复用；未命中时在本地注册表临界区内检查容量、构造并注册唯一实例
  2. 仅注册成功的实例维护排空信号；业务终态由数据库状态和终态事件发布，最终排空后才按
     (runId, instance) 身份摘除
        |
        v
ChatRunInstance.confirm（synchronized 实例锁内，原子完成）
  3. 先做内存 phase/status 守卫：旧 phase 幂等返回，未来 phase 或非待确认状态拒绝
  4. validateAndBuildMessage：读 Agent ASKING 状态 → 三方 ID 校验 → 构造确认消息
  5. advanceConfirmation：REQUIRES_NEW 事务内复核所有权 + 阶段守卫 + (status, phaseNo) CAS
  6. 同步实例内 Run 状态并立即启动下一阶段
```

锁顺序恒为「实例 monitor → REQUIRES_NEW 数据库事务」，不反向持数据库行锁等待实例锁。

关键不变量：

1. **先验证、后推进**：完整确认上下文（Agent `ASKING` 块、三方 ID 一致）验证通过前，不推进 Run 数据库状态；验证失败保持 `AWAITING_CONFIRM`。
2. **读取失败不折叠为空**：状态存储异常、状态不存在、没有 `ASKING` block 抛 `CONFIRM_CONTEXT_UNAVAILABLE`；ID 不一致抛 `CONFIRM_CONTEXT_MISMATCH`；Run 保持可重试。
3. **旧 phase 重放不读 AgentState**：当前 Run 已越过来源 phase 时直接返回 `resumed=false`，避免拿新 phase 的
   `ASKING` 状态校验旧命令；同 phase 并发仍由实例锁和数据库 CAS 保证零副作用。
4. **迁移后失败才终结**：仅 CAS 成功后的启动失败按 `START_FAILED` 收敛终态，且不再静默吞掉。
5. **阶段不重叠**：只有旧 phase 源流终止、Sandbox release 和 Workspace 审计完成并进入
   `AWAITING_CONFIRM` 后才接受确认；CAS 成功后可立即启动新的 `streamEvents`。

## 3. 修复目标与边界

### 3.1 必须保证

- 完整确认上下文验证成功前，Run 不得离开 `AWAITING_CONFIRM`。
- 确认请求、数据库快照和 AgentScope `ASKING` 工具的 ID 集合必须完全一致。
- Agent 状态读取失败或上下文暂时不可用时，Run 保持可重试。
- 数据库阶段迁移成功后，不得再次从 state store 查询同一批工具上下文。
- 并发确认仍由 `(status, phaseNo)` CAS 保证只有一个请求推进。
- 不在日志、RunSnapshot 或错误响应中输出完整工具参数、Token、密码或凭据。
- bootstrap 继续只承担展示恢复，不冒充可执行上下文恢复。
- Agent 状态读取和确认续跑固定使用 `(ChatSession.userId, ChatSession.id)`，不得自行拼接 `gw-*`。

### 3.2 本次不做

- 不把完整 `ToolUseBlock` 写入当前脱敏 `RunSnapshot`。
- 不新增确认命令流水表、事务 outbox、Redis Stream、节点租约/心跳或远程停止。
- 不恢复 JVM 重启前处于模型或工具执行中的 phase。`AWAITING_CONFIRM` 仅在持久化 state store 可按权威
  `(userId, sessionId)` 重新读取并通过三方校验时保留，否则按失败边界收敛。
- 不改变现有前端确认协议；前端仍提交 `phaseNo` 和全部 `decisions`。
- 不迁移或双读旧的 `(userId, gw-*)` Agent state。

## 4. 目标流程

目标时序如下（确认在 `ChatRunInstance` 实例锁内原子完成）：

```text
Client           ChatService        ChatRunCoordinator   ChatRunInstance        Agent state    ChatRunStateService
  |                   |                    |                    |                     |                |
  | POST /confirm     |                    |                    |                     |                |
  |------------------>| loadOwned          |                    |                     |                |
  |                   | confirm            |                    |                     |                |
  |                   |------------------->| selectOrRestore    |                     |                |
  |                   |                    | (get / putIfAbsent，注册后订阅排空信号) |                   |                |
  |                   |                    |------------------->| synchronized {      |                |
  |                   |                    |                    | getAgentState       |                |
  |                   |                    |                    |-------------------->|                |
  |                   |                    |                    |<--------------------|                |
  |                   |                    |                    | 三方校验+构造消息     |                |
  |                   |                    |                    | advanceConfirmation |                |
  |                   |                    |                    |-------------------------------------->|
  |                   |                    |                    | (REQUIRES_NEW CAS)  |                |
  |                   |                    |                    |<--------------------------------------|
  |                   |                    |                    | 同步状态并启动新 phase |                |
  |                   |                    |                    | }                   |                |
  |<==================| bootstrap + phase N+1 SSE              |                     |                |
```

关键原则是：**读取一次、验证一次、捕获一次、消费一次**，且全程在同一实例临界区内。

## 5. 组件改造

### 5.1 `ChatRunInstance.confirm`：实例锁内单一原子操作

确认不再拆分为「预检 + CAS + resume」两阶段跨锁流程，而是 `ChatRunInstance` 上的一个 `synchronized` 方法，在同一实例临界区内完成全部步骤：

```java
synchronized ConfirmTransition confirm(ConfirmToolCall command) {
    ConfirmTransition replay = guardPhaseAndStatus(command);
    if (replay != null) {                  // 旧 phase：不读取当前 AgentState
        return replay;
    }
    Msg confirmMessage = validateAndBuildMessage(command);
    ConfirmTransition transition = runService.advanceConfirmation(run, session, command.getPhaseNo());
    if (!transition.resumed()) {          // 幂等重放：阶段已被处理过
        syncRun(transition.run());
        return transition;
    }
    syncRun(transition.run());
    beginConfirmedPhase();
    startPhase(confirmMessage);
    return transition;
}
```

`guardPhaseAndStatus` 必须在读取 AgentState 前执行：当前 `phaseNo` 大于来源阶段时幂等返回；来源阶段超前、
状态不是 `AWAITING_CONFIRM` 或仍有活动源流时返回状态冲突。由于进入 `AWAITING_CONFIRM` 已经保证旧 phase
完整排空，确认成功后不存在“立即启动还是登记待启动”的双分支。

`validateAndBuildMessage` 职责（**全部在 CAS 之前**完成，因此 CAS 冲突零副作用）：

1. `agentExecutionAdapter == null`：抛 `CONFIRM_CONTEXT_UNAVAILABLE`，保持 `AWAITING_CONFIRM`，不终结。
2. 决策非空、不重复、字段合法，否则 `INVALID_PARAMETER`。
3. 读取 Agent 状态上下文（`getAgentState(...).getContext()`）中当前待确认批次的 `ToolCallState.ASKING` 的 `ToolUseBlock`，口径与 AgentScope `getPendingToolUseIds` 一致：**只取最后一条 assistant message 的 ASKING 块**，无则判定当前无待确认批次（不回退更早消息）。不得扫描全上下文——旧消息残留的未收尾 ASKING 块不属于当前批次，计入会使三方严格相等误判失败；读取失败抛 `CONFIRM_CONTEXT_UNAVAILABLE`，**不执行 CAS**。
4. 三方 ID 集合完全相等校验（见 §5.2）。
5. 按请求决策顺序构造 `ConfirmResult` 并 build 确认消息。

### 5.2 三方一致性校验

校验规则为集合完全相等，且每组内部不得重复：

```text
snapshotIds == decisionIds == agentAskingIds
```

其中 `snapshotIds` 取自实例锁内 `accumulator.snapshot().pendingTools()`（实例重建时以 DB 快照初始化，与持久化同源，是锁内单一事实来源）。

禁止仅使用 `allMatch` 做子集校验，因为它不能表达三方事实是否完整一致；三方集合相等补上「Agent 侧存在 decision 之外的额外 `ASKING` 块不被察觉」的缺口。

完整 `ToolUseBlock` 只存在于 AgentScope state 和当前确认调用构造的消息 metadata，交给下一次 AgentScope
调用消费后即释放；不得写入 `RunSnapshot`、数据库、SSE 或日志。

### 5.3 `ChatRunStateService.advanceConfirmation`：权威迁移

状态机面新增独立事务方法，只承载权威迁移，不见决策内容：

```java
@Transactional(propagation = REQUIRES_NEW)
ConfirmTransition advanceConfirmation(
        ChatRunEntity identity, ChatSessionEntity expectedSession, int sourcePhaseNo)
```

事务内职责：

1. **所有权复核**：按 `session.id + expectedSession.userId` 查会话 `FOR UPDATE`；按 `run.id + session.id` 查 Run `FOR UPDATE`（租户由 `withTenant` 与租户插件保证）。
2. **阶段幂等守卫**：`phaseNo > sourcePhaseNo` 视为重复确认，返回 `resumed=false`；`phaseNo < sourcePhaseNo` 抛 `CHAT_RUN_STATE_CONFLICT`。
3. **状态校验**：仅 `AWAITING_CONFIRM` 可确认，否则状态冲突。
4. **CAS 推进**：以 `(status=AWAITING_CONFIRM, phaseNo)` 为前置条件迁移到 `RUNNING / phase N+1 / 新 aguiRunId`，`changed==1` 才视为推进成功，否则状态冲突。

### 5.4 `ChatServiceImpl`：编排下沉

确认的数据库状态推进与执行编排整体下沉到协调器/实例，服务层只做归属校验与事件流挂载：

```java
public SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command) {
    RunContext context = runService.loadOwned(sessionId, runId);
    ConfirmTransition transition = chatRunCoordinator.confirm(context.run(), context.session(), command);
    return openRunEventStream(transition.run(), 0, true);
}
```

`ChatRunCoordinator.confirm` 先查询 `executions`；未命中时构造候选实例，再由 `putIfAbsent` 选定唯一注册实例。
竞争落败或未注册实例不能操作注册表。业务终态由数据库状态和终态事件发布；Coordinator 等最终 `drainedSignal` 后才
通过 `remove(runId, instance)` 按实例身份摘除，避免记忆尾部仍运行时丢失实例所有权。取得规范实例后，确认流程在
其实例锁内执行。`ChatRunService.confirm`（HTTP 编排面）随之删除，确认推进不再经过该面。

CAS 未推进（幂等重放）时不启动新阶段，仅按数据库当前状态返回恢复流。

## 6. 错误语义

新增模块错误码，具体数值在实现时按 `AiErrorCode` 当前连续区间分配：

| 错误码建议 | 条件 | Run 状态 | 是否允许重试 |
| :--- | :--- | :--- | :--- |
| `CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE` | state store 读取失败、状态或 context 不存在 | `AWAITING_CONFIRM` | 是 |
| `CHAT_RUN_CONFIRM_CONTEXT_MISMATCH` | snapshot、decision、Agent 工具 ID 不一致 | `AWAITING_CONFIRM` | 状态刷新后决定 |
| `CHAT_RUN_STATE_CONFLICT` | phase/status 已变化或 CAS 竞争失败 | 数据库当前状态 | 按现有幂等语义 |
| `INVALID_PARAMETER` | decisions 重复、不完整或字段非法 | `AWAITING_CONFIRM` | 修正请求后可重试 |

上下文预检失败不属于 Agent 启动失败，因此不得走 `finalizeFailed(...)` 收敛终态（`finalizeFailed` 的 finishReason 恒为 `"ERROR"`，`START_FAILED` 是作为 `errorCode` 入参传入的 finish reason，而非 `AiErrorCode` 枚举值），也不得发 `RUN_ERROR` 终态事件。

HTTP 请求应由统一异常机制返回业务错误。前端收到错误后重新查询 Run；只要仍为 `AWAITING_CONFIRM`，继续展示原确认卡片并允许重试。

## 7. Agent state store 加固

### 7.1 禁止显式持久化配置静默降级

当前 `AgentFactory.resolveStateStore` 在 MYSQL 扩展缺失或创建失败时会回退 `InMemoryAgentStateStore`。若部署显式配置：

```yaml
lambda:
  fusion:
    ai:
      state-store:
        type: MYSQL
```

则 MYSQL 是部署者选择的持久化保证，创建失败应抛 `CONFIGURATION_ERROR` 并阻止 AI 能力带病启动，不能静默改成 MEMORY。

> 本条反转 `resolveStateStore` 既有的一条文档化保证——其 Javadoc 现写“扩展未安装或创建失败时回退 MEMORY，保证启动不阻塞”。新策略仅对**显式配置**的非 MEMORY 类型生效：未配置或显式 MEMORY 时行为不变；显式 FILE/MYSQL/POSTGRESQL/REDIS 时才由“静默回退”改为“启动失败”。既有 `StateStoreResolverTest` 当前断言的是回退行为（见 §11.4），实施时须同步改写为 fail-fast 断言。

建议规则：

- 未配置类型且默认值为 MEMORY：允许使用 MEMORY；
- 显式配置 FILE/MYSQL/POSTGRESQL/REDIS：扩展缺失、连接参数无效或创建失败时启动失败；
- 未识别的类型：按配置错误处理（启动失败），不静默回退 MEMORY；
- 日志不得输出数据库密码或完整连接串。

### 7.2 可观测性

增加结构化日志和指标时只记录：

- `runId`；
- `sessionId`；
- `phaseNo`；
- state store 类型；
- snapshot/decision/agent 三组工具数量；
- 异常类型。

禁止记录：

- 完整工具参数；
- `ToolUseBlock` JSON；
- Token、密码、API Key；
- state store 凭据。

建议指标：

```text
ai.chat.confirm.context.read.failure
ai.chat.confirm.context.missing
ai.chat.confirm.context.mismatch
ai.chat.confirm.cas.conflict
ai.chat.confirm.resume.failure
```

## 8. 并发与故障边界

### 8.1 两个确认请求并发

两个请求可能都读取到同一份 `ASKING` 上下文并完成三方校验，最终由数据库行锁和 `(status, phaseNo)` CAS 决定唯一胜者：

- 胜者在实例锁内迁移至 phase N+1 并立即启动；此时旧 phase 已由待确认不变量保证完整排空；
- 败者看到 phase 已推进（`resumed=false`）或 CAS 状态冲突，不启动第二次 Agent 阶段、不终结竞争方 Run。

### 8.2 校验成功、数据库状态推进失败

校验与确认消息构造均在 CAS 之前完成，CAS 竞争落败（`resumed=false` 重放或状态冲突）时不启动新阶段、不终结竞争方 Run，构造结果直接丢弃，Agent 状态和 Run 停留在原阶段，零副作用。

### 8.3 数据库状态推进成功、启动下一阶段失败

校验已经排除了「上下文缺失」这一类失败，但线程池或 Agent 本身仍可能在实际启动 phase N+1 时失败。
此时 Run 已进入 phase N+1，应使用明确的运行错误收敛为 `FAILED / START_FAILED`，并保留已有快照；不得静默吞掉，
也不得错误回滚成 `AWAITING_CONFIRM`，避免重复执行已经确认的外部工具。

### 8.4 state store 在等待期间失效

Run 保持 `AWAITING_CONFIRM` 并返回可重试错误。运维修复 state store 后，用户可以提交同一 `phaseNo` 再次确认。如果上下文永久丢失，用户仍可以显式停止 Run；服务端不得伪造 `ToolUseBlock` 继续执行。

### 8.5 JVM 重启

业务层不在启动时扫描并改写 `AWAITING_CONFIRM` 或 `RUNNING`。`AWAITING_CONFIRM` 保留在数据库中；用户再次提交确认时，
当前请求节点按正常确认路径从 AgentScope state store 读取 `ASKING` 集合，并与 Run 快照和用户决策进行三方校验。

跨进程确认成功至少要求：

- Agent 定义、工具集合和权限上下文在重启后仍可识别且未发生不兼容变化；
- `ASKING` 工具调用上下文能够从 state store 重新读取，并与 Run 快照中的待确认集合一致；
- 恢复路径不得伪造 `ToolUseBlock`，不得仅凭 `RunSnapshot` 跳过上下文校验；
- 一旦上下文不可读、集合不一致或版本边界不明确，确认请求必须失败且 Run 保持 `AWAITING_CONFIRM`，不得静默继续。

因此，跨进程确认依赖 AgentScope 持久化 state store，但不依赖 ChatRun 节点所有权协议。不满足条件时返回
`CONFIRM_CONTEXT_UNAVAILABLE` 或 `CONFIRM_CONTEXT_MISMATCH`；用户可以稍后重试，或显式停止旧 Run 后开始新 Run。

### 8.6 待确认事件与数据库事实的顺序

进入待确认时，中断事件与 `AWAITING_CONFIRM` 迁移之间存在「事件外发」与「数据库事实」两个事实源，必须显式约定先后，避免两类缺陷：

- **先发事件、后落库**：数据库状态提交失败时前端已看到「等待确认」，后端却仍是 `RUNNING`，甚至随后再收到失败终态——业务事实倒置。
- **先落库、后发事件**：序号先于事件持久化，重启/恢复时快照序号可能不覆盖中断事件——序号覆盖缺口。

收敛为**事件缓冲两态**：中断事件先**暂存**（编码、分配序号并推进 `nextSeq`，但不进入可见窗口、不推送订阅者），`awaitConfirm` 数据库状态提交成功后才**发布**（进入窗口并推送），失败则**丢弃**。由此同时满足：

1. 序号在暂存时分配，快照序号天然覆盖中断事件，不依赖「先存序号再发事件」的隐式顺序。
2. 数据库事实先于信号外发，落库失败时订阅者不可见中断事件，无副作用、可重试。

两态经 `ChatRunEventStore.runExclusive` 收敛为缓冲区实例锁内的单原子操作，不在锁外分散调用暂存/发布/丢弃。
数据库动作返回 `false` 或抛错时都必须丢弃暂存事件；抛错后由于源流已经结束且用户尚未看到确认卡片，运行收敛为
`FAILED / AWAIT_CONFIRM_FAILED`，不能遗留不可交互的 `RUNNING`。异常路径必须使用 `try/catch` 清空 staged events，
防止失败批次混入下一次发布。

## 9. 安全设计

完整 `ToolUseBlock` 可能包含敏感工具参数，只允许短暂存在于：

1. AgentScope state store；
2. 当前确认调用构造的消息 metadata（立即交 AgentScope 消费，随后清除引用）。

不得把它加入：

- `RunSnapshot`；
- SSE bootstrap；
- Controller DTO 响应；
- 普通应用日志；
- 异常 message。

RunSnapshot 继续使用现有脱敏逻辑，前端只获得展示和决策所需的信息。

## 10. 实施步骤

建议按以下原子步骤实施：

1. 在 `AiErrorCode` 增加确认上下文不可用和不一致错误码。
2. 将 Agent 状态读取方法改为「明确返回结果或抛业务异常」，删除捕获异常后返回空列表的行为。
3. 在 `ChatRunInstance` 增加 `confirm` 单一原子方法与三方一致性校验（先做 phase/status 守卫，再在锁内完成读取、校验、CAS 和立即启动）。
4. 在 `ChatRunStateService` 增加 `advanceConfirmation`（REQUIRES_NEW、所有权复核、阶段守卫、CAS），并删除 `ChatRunService.confirm`。
5. 调整 `ChatServiceImpl.confirm` 为「归属校验 → 协调器确认 → 挂载事件流」的薄编排。
6. 统一为完整排空后才进入 `AWAITING_CONFIRM`，删除排空期确认命令和单槽 `PendingPhase`；业务终态由持久化状态和终态事件表达，实例只保留供摘除使用的 `drainedSignal`。
7. 调整显式持久化 state store 的失败策略，禁止静默回退 MEMORY。
8. 改写既有 `StateStoreResolverTest`：显式非 MEMORY 配置失败时断言启动失败而非回退 MEMORY（仅未配置/显式 MEMORY 场景保留回退或直接创建用例）；并补充确认上下文相关的单元/集成测试。
9. 更新 `chat-run-resume.md` 的确认流程及失败语义，使总设计与本修复一致。

## 11. 测试方案

### 11.1 `ChatRunInstance` 确认测试

- state store 抛异常：返回 `CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE`，不执行 CAS，Run 不终结。
- state/context 为 null：返回上下文不可用，不执行 CAS。
- assistant message 不含 `ASKING`：返回上下文不可用，不执行 CAS。
- 旧 assistant message 残留未收尾 `ASKING`、最后一条为当前批次：只认最后一条，三方校验通过并确认（口径对齐 AgentScope `getPendingToolUseIds`，不扫描全上下文）。
- snapshot 与 Agent ID 不一致：返回上下文不一致，不执行 CAS。
- decision 缺少、多出或重复 ID：拒绝确认，不执行 CAS。
- 三方 ID 相同但顺序不同：CAS 推进后按 decision 顺序生成确认消息并启动一次新阶段。
- 旧 phase 幂等重放（`resumed=false`）：不读取当前 AgentState，不启动第二次 Agent 阶段。
- 旧 phase 未排空：Run 保持 `RUNNING`，确认返回状态冲突，不保存内存命令。
- 完整排空后：Run 才进入 `AWAITING_CONFIRM`，确认成功立即且只启动一次新 phase。

### 11.2 待确认事件两态

- 数据库状态提交成功：暂存的中断事件发布，订阅者可见，序号被快照覆盖。
- 数据库状态提交落败（返回 `false`）：暂存事件丢弃，订阅者不可见，可重试。
- 数据库状态提交抛错（回滚）：暂存事件丢弃，Run 收敛为 `FAILED / AWAIT_CONFIRM_FAILED`。
- 数据库动作抛错后再次成功：只发布成功批次，不能夹带前一次 staged events。
- 确认竞胜（落库返回 `false` 且当前 `RUNNING`）：实例安全收尾，不误判为 `STATE_CONFLICT`。
- 运行已终结后禁止暂存待确认事件（抛 `IllegalStateException`）。

### 11.3 `ChatRunStateService.advanceConfirmation` 测试

- phase 匹配时只推进一次。
- phase 超前时幂等返回 `resumed=false`。
- phase 落后或状态不为 `AWAITING_CONFIRM` 时返回状态冲突。
- 会话所有权复核失败（`session.id + userId` 查不到）时返回 `CHAT_RUN_NOT_FOUND`。
- CAS 竞争落败（`changed != 1`）时返回状态冲突。

### 11.4 `ChatServiceImpl` 编排测试

- 协调器确认抛错时原样向上抛出。
- 确认成功响应固定使用 `afterSeq=0, bootstrap=true`，并从迁移后 Run 挂载事件流。
- CAS 幂等重放时同样从迁移后 Run 挂载事件流（不重复推进）。

### 11.5 配置测试

> 既有 `StateStoreResolverTest` 中 `distributedTypeWithoutProviderFallsBackToInMemory`、`providerMismatchFallsBackToInMemory`、`providerFailureFallsBackToInMemory`、`unknownTypeFallsBackToInMemory` 当前断言“显式非 MEMORY 配置失败 -> 回退 MEMORY”，须随 §7.1 改写为断言启动失败（抛 `CONFIGURATION_ERROR`）；`defaultsToInMemory`、`fileMode*`、`dispatchesToMatchingProvider` 等成功路径用例保留。

- 默认 MEMORY 能正常创建。
- 显式 MYSQL 且 provider 存在时创建 MYSQL store。
- 显式 MYSQL 但 provider 缺失时启动失败，不回退 MEMORY。
- provider 创建异常时错误信息不包含密码或完整连接串。

### 11.6 回归测试

执行：

```shell
mvn -pl lambda-fusion-ai test
mvn -pl lambda-fusion-ai -am compile
```

同时人工验证：

1. 工具请求出现后立即确认；
2. 刷新页面、bootstrap 重建后确认；
3. state store 短暂不可用后恢复并重试；
4. 两个浏览器标签页同时确认；
5. 确认失败后仍能停止 Run；
6. 日志和 SSE 中不存在完整敏感工具参数。

## 12. 上线与回滚

### 12.1 上线前检查

- 确认生产 state store 类型和扩展依赖已正确安装。
- 确认 state store 表、账号权限和连接池健康。
- 检查历史日志中是否出现过 MYSQL 回退 MEMORY。
- 发布前等待或显式停止旧版本实例上的活跃 Run，避免跨版本 Agent 定义、工具集合或状态结构不兼容。
- 本次不迁移旧 `(userId, gw-*)` 状态；发布后只使用 `(ChatSession.userId, ChatSession.id)`，因此发布前必须结束
  所有 `RUNNING` 和 `AWAITING_CONFIRM` Run，不做跨版本续跑。

### 12.2 上线观察

重点观察：

- 确认上下文读取失败率；
- 三方 ID 不一致率；
- `AWAITING_CONFIRM` 停留时长；
- `START_FAILED` 数量是否下降；
- state store 连接错误与反序列化错误。

### 12.3 回滚策略

本方案不修改表结构和前端协议，但 Agent state 身份切换不是无损回滚。回滚前必须停止新版本接收流量并等待活动
Run 收敛；旧版本会重新使用历史 `(userId, gw-*)` 状态并忽略新版本写入的 `(userId, ChatSession.id)` 状态。既然本次
明确不做数据迁移，运维必须接受这段状态不连续，不能宣传为“会话记忆无损回滚”。

## 13. 验收标准

满足以下条件才视为修复完成：

- Agent 上下文缺失时，数据库仍为原 `AWAITING_CONFIRM / phase N`。
- 相同确认命令在上下文恢复后可以重试成功。
- 确认成功后只推进一次 phase，只启动一次下一阶段。
- 用户只能在旧 phase 完整排空并进入 `AWAITING_CONFIRM` 后提交确认，新旧 AgentScope Flux 不重叠。
- bootstrap 展示快照与 Agent 上下文不一致时返回明确业务错误。
- 不再出现“先产生 phase N+1 bootstrap，再因旧阶段工具上下文缺失而 `START_FAILED`”的事件序列。
- 显式 MYSQL state store 初始化失败时不会静默使用 MEMORY。
- AI 模块测试和 compile 阶段检查全部通过。
