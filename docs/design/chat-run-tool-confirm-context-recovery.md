# HITL 工具确认上下文丢失修复设计

> 目标：修复 Run 已能通过 bootstrap 重建待确认工具卡片，但确认时 AgentScope 中找不到对应 `ToolUseBlock`，最终错误进入 `FAILED / START_FAILED` 的问题。
>
> 本文只描述修复设计。实际代码改造完成前，本文中的目标流程和新增错误码均不代表当前实现已经具备。

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

## 2. 当前失败链路

当前确认链路是：

```text
ChatServiceImpl.confirm
        |
        v
ChatRunServiceImpl.confirm
  1. 锁定 Session 和 Run
  2. 用 RunSnapshot 校验 decisions
  3. AWAITING_CONFIRM / phase N
       -> RUNNING / phase N+1
  4. 提交事务
        |
        v
ChatRunManager.resumeConfirmed
  5. 从 AgentScope state store 读取 ASKING ToolUseBlock
  6. 按 toolCallId 匹配
  7. 找不到则抛 IllegalStateException
  8. finalizeFailed(START_FAILED)
```

这个顺序有两个确定缺陷：

1. **先迁移、后验证**：数据库已经离开 `AWAITING_CONFIRM`，才验证继续执行所必需的完整上下文。
2. **读取失败被折叠为空列表**：状态存储异常、状态不存在、没有 `ASKING` block 和 ID 不匹配最终都表现为“确认工具上下文不存在”。

失败之后，原确认卡片已经越过阶段号，用户无法重试，Run 只能进入终态失败。

## 3. 修复目标与边界

### 3.1 必须保证

- 完整确认上下文验证成功前，Run 不得离开 `AWAITING_CONFIRM`。
- 确认请求、数据库快照和 AgentScope `ASKING` 工具的 ID 集合必须完全一致。
- Agent 状态读取失败或上下文暂时不可用时，Run 保持可重试。
- 数据库阶段迁移成功后，不得再次从 state store 查询同一批工具上下文。
- 并发确认仍由 `(status, phaseNo)` CAS 保证只有一个请求推进。
- 不在日志、RunSnapshot 或错误响应中输出完整工具参数、Token、密码或凭据。
- bootstrap 继续只承担展示恢复，不冒充可执行上下文恢复。

### 3.2 本次不做

- 不把完整 `ToolUseBlock` 写入当前脱敏 `RunSnapshot`。
- 不新增确认命令流水表、事务 outbox、Redis Stream 或多实例执行租约。
- 不尝试恢复 JVM 重启前正在等待确认的 Agent。当前设计仍按 `INSTANCE_LOST` 收敛遗留 Run。
- 不改变现有前端确认协议；前端仍提交 `phaseNo` 和全部 `decisions`。

## 4. 目标流程

目标时序如下：

```text
Client              ChatService          ChatRunManager        Agent state       ChatRunService
  |                      |                     |                    |                   |
  | POST /confirm        |                     |                    |                   |
  |--------------------->|                     |                    |                   |
  |                      | loadOwned           |                    |                   |
  |                      |---------------------|                    |                   |
  |                      | prepareConfirmation |                    |                   |
  |                      |-------------------->| getAgentState      |                   |
  |                      |                     |------------------->|                   |
  |                      |                     |<-------------------|                   |
  |                      |                     | 校验三方 ID          |                   |
  |                      |<--------------------| PreparedConfirmation                   |
  |                      | confirm(CAS)        |                    |                   |
  |                      |------------------------------------------------------------>|
  |                      |<------------------------------------------------------------|
  |                      | resumePrepared      |                    |                   |
  |                      |-------------------->|                    |                   |
  |                      |                     | 直接构造确认消息并启动下一阶段           |
  |<=====================| bootstrap + phase N+1 SSE                                  |
```

关键原则是：**读取一次、验证一次、捕获一次、消费一次**。

## 5. 组件改造

### 5.1 `ChatRunManager`：准备确认上下文

新增仅在 manager 内部使用的不可变对象：

```java
record PreparedConfirmation(
        String runId,
        int sourcePhaseNo,
        List<ConfirmResult> results) {}
```

新增方法：

```java
PreparedConfirmation prepareConfirmation(
        ChatRunEntity run,
        ChatSessionEntity session,
        ConfirmToolCall command)
```

职责：

1. 获取或恢复当前 `Execution`。`AWAITING_CONFIRM` 期间 Agent 空闲、Execution 稳定，`prepareConfirmation` 与后续 `resumePrepared`/`startConfirmedPhase` 按 `runId` 取到同一 Execution 实例；即使 Execution 需重建，`getAgentState` 仍从独立的 AgentScope state store 读取，"恢复"不依赖内存中的 Agent 实例。
2. 在 `Execution` 的串行边界内读取 Agent 状态。
3. 从 Agent 状态上下文（`getAgentState(...).getContext()`）提取所有 `ToolCallState.ASKING` 的 `ToolUseBlock`，读取范围与现状 `deserializePendingFromAgentState` 一致，不限定“最近一条 assistant message”。
4. 比较三组 ID：
   - `RunSnapshot.pendingTools()`；
   - `command.decisions`；
   - Agent 状态中的 `ASKING ToolUseBlock`。
5. 按请求决策顺序构造 `ConfirmResult`。
6. 返回捕获了完整 `ToolUseBlock` 的 `PreparedConfirmation`。

`PreparedConfirmation` 不对 Controller 暴露，不序列化、不写日志、不存入 RunSnapshot。

### 5.2 三方一致性校验

校验规则为集合完全相等，且每组内部不得重复：

```text
snapshotIds == decisionIds == agentAskingIds
```

同时校验：

- `run.status == AWAITING_CONFIRM`；
- `run.phaseNo == command.phaseNo`；
- `PreparedConfirmation.sourcePhaseNo == command.phaseNo`；
- 每个 decision 恰好对应一个 `ToolUseBlock`。

禁止仅使用 `allMatch` 做子集校验，因为它不能表达三方事实是否完整一致。现状 `startConfirmedPhase` 仅按 decision 逐个 `orElseThrow` 查找 Agent 块，Agent 侧存在 decision 之外的额外 `ASKING` 块时不会被察觉；三方集合相等正好补上这一缺口。

上述 `run.status == AWAITING_CONFIRM` 等校验基于 `loadOwned` 读到的 Run 快照，可能已被并发请求推进，属 fast-fail 优化而非权威判定；唯一推进判定仍是 `ChatRunServiceImpl.confirm` 的 `(AWAITING_CONFIRM, phaseNo)` CAS。

### 5.3 `ChatServiceImpl`：调整编排顺序

目标伪代码：

```java
public SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command) {
    RunContext context = runService.loadOwned(sessionId, runId);
    PreparedConfirmation prepared =
            runManager.prepareConfirmation(context.run(), context.session(), command);

    ConfirmTransition transition = runService.confirm(sessionId, runId, command);
    if (transition.resumed()) {
        runManager.resumePrepared(
                transition.run(), transition.session(), prepared);
    }
    return attach(transition.run(), 0, true);
}
```

注意：预检不是数据库状态迁移的替代品。两个并发请求都可能完成预检，但 `ChatRunServiceImpl.confirm` 的行锁与 `(AWAITING_CONFIRM, phaseNo)` CAS 仍是唯一推进判定。

如果 CAS 返回幂等未推进，当前请求不得消费 `PreparedConfirmation`，只按数据库当前状态返回恢复流。

### 5.4 `Execution.startConfirmedPhase`：禁止二次读取

当前方法接收原始 `ConfirmToolCall`，并调用 `deserializePendingFromAgentState()`。目标方法改为接收准备结果：

```java
synchronized void startConfirmedPhase(
        ChatRunEntity updated,
        PreparedConfirmation prepared)
```

执行前只校验：

- `prepared.runId` 与当前 Run 相同；
- `prepared.sourcePhaseNo + 1 == updated.phaseNo`；
- Execution 未运行且未终结。

随后直接构造：

```java
Msg confirm = UserMessage.builder()
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, prepared.results()))
        .build();
```

这里不得再次访问 Agent state store，否则仍存在“预检成功、迁移成功、二次读取失败”的窗口。

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

两个请求可能都读取到同一份 `ASKING` 上下文。允许两者完成预检，最终由数据库行锁和 phase CAS 决定唯一胜者：

- 胜者迁移至 phase N+1 并消费准备结果；
- 败者看到 phase 已推进，按既有幂等逻辑返回，不启动第二次 Agent 阶段。

### 8.2 预检成功、数据库迁移失败

准备结果只存在于当前请求内存中，直接丢弃。Agent 状态和 Run 仍停留在原阶段，不产生副作用。

### 8.3 数据库迁移成功、启动下一阶段失败

预检已经排除了“上下文缺失”这一类失败，但模型、线程池或 Agent 本身仍可能启动失败。此时 Run 已进入 phase N+1，应使用明确的运行错误收敛为 `FAILED / ERROR`，并保留已有快照；不得错误回滚成 `AWAITING_CONFIRM`，避免重复执行已经确认的外部工具。

### 8.4 state store 在等待期间失效

Run 保持 `AWAITING_CONFIRM` 并返回可重试错误。运维修复 state store 后，用户可以提交同一 `phaseNo` 再次确认。如果上下文永久丢失，用户仍可以显式停止 Run；服务端不得伪造 `ToolUseBlock` 继续执行。

### 8.5 JVM 重启

当前边界是：仅当 state store 为持久化实现、且重启后仍可恢复并校验确认上下文时，允许保留 `AWAITING_CONFIRM`；否则启动恢复会将遗留 `RUNNING/AWAITING_CONFIRM` 收敛为 `INSTANCE_LOST`。

这里的“可恢复并校验”至少要求：

- Agent 定义、工具集合和权限上下文在重启后仍可识别且未发生不兼容变化；
- `ASKING` 工具调用上下文能够从 state store 重新读取，并与 Run 快照中的待确认集合一致；
- 恢复路径不得伪造 `ToolUseBlock`，不得仅凭 `RunSnapshot` 跳过上下文校验；
- 一旦上下文不可读、集合不一致或版本边界不明确，必须按失败边界收敛，而不是静默继续。

因此，跨重启确认恢复不是默认能力，而是有明确前置条件的受支持边界；不满足条件时仍按 `INSTANCE_LOST` 处理。

## 9. 安全设计

完整 `ToolUseBlock` 可能包含敏感工具参数，只允许短暂存在于：

1. AgentScope state store；
2. `PreparedConfirmation` 请求内存对象；
3. AgentScope 确认消息 metadata。

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
2. 将 Agent 状态读取方法改为“明确返回结果或抛业务异常”，删除捕获异常后返回空列表的行为。
3. 在 `ChatRunManager` 增加 `PreparedConfirmation` 和三方一致性校验。
4. 调整 `ChatServiceImpl.confirm`，在事务状态迁移前完成预检。
5. 调整 `resumeConfirmed/startConfirmedPhase`，直接消费准备结果并删除二次读取。
6. 调整显式持久化 state store 的失败策略，禁止静默回退 MEMORY。
7. 改写既有 `StateStoreResolverTest`：显式非 MEMORY 配置失败时断言启动失败而非回退 MEMORY（仅未配置/显式 MEMORY 场景保留回退或直接创建用例）；并补充确认上下文相关的单元/集成测试。
8. 更新 `chat-run-resume.md` 的确认流程及失败语义，使总设计与本修复一致。

## 11. 测试方案

### 11.1 `ChatRunManager` 单元测试

- state store 抛异常：返回 `CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE`。
- state/context 为 null：返回上下文不可用，Run 不终结。
- assistant message 不含 `ASKING`：返回上下文不可用。
- snapshot 与 Agent ID 不一致：返回上下文不一致。
- decision 缺少、多出或重复 ID：拒绝确认。
- 三方 ID 相同但顺序不同：按 decision 顺序正确生成 `ConfirmResult`。
- `resumePrepared` 不再调用 `getAgentState`。

### 11.2 `ChatRunServiceImpl` 测试

- phase 匹配时只推进一次。
- 两次相同确认请求中只有一次 `resumed=true`。
- phase 落后时保持现有幂等返回。
- phase 超前或状态不为 `AWAITING_CONFIRM` 时返回状态冲突。

### 11.3 `ChatServiceImpl` 编排测试

- 预检失败时不调用 `runService.confirm`。
- CAS 未推进时不调用 `resumePrepared`。
- CAS 成功时只消费一次准备结果。
- 确认成功响应仍固定使用 `afterSeq=0, bootstrap=true`。

### 11.4 配置测试

> 既有 `StateStoreResolverTest` 中 `distributedTypeWithoutProviderFallsBackToInMemory`、`providerMismatchFallsBackToInMemory`、`providerFailureFallsBackToInMemory`、`unknownTypeFallsBackToInMemory` 当前断言“显式非 MEMORY 配置失败 -> 回退 MEMORY”，须随 §7.1 改写为断言启动失败（抛 `CONFIGURATION_ERROR`）；`defaultsToInMemory`、`fileMode*`、`dispatchesToMatchingProvider` 等成功路径用例保留。

- 默认 MEMORY 能正常创建。
- 显式 MYSQL 且 provider 存在时创建 MYSQL store。
- 显式 MYSQL 但 provider 缺失时启动失败，不回退 MEMORY。
- provider 创建异常时错误信息不包含密码或完整连接串。

### 11.5 回归测试

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
- 发布前等待或显式停止旧版本实例上的活跃 Run，避免滚动发布把等待确认的执行器判为 `INSTANCE_LOST`。

### 12.2 上线观察

重点观察：

- 确认上下文读取失败率；
- 三方 ID 不一致率；
- `AWAITING_CONFIRM` 停留时长；
- `START_FAILED` 数量是否下降；
- state store 连接错误与反序列化错误。

### 12.3 回滚策略

本方案不修改表结构和前端协议，代码可直接回滚。回滚前应停止新版本实例接收流量，并等待其正在执行的 Run 收敛；否则进程切换仍会触发当前单实例设计的 `INSTANCE_LOST`。

## 13. 验收标准

满足以下条件才视为修复完成：

- Agent 上下文缺失时，数据库仍为原 `AWAITING_CONFIRM / phase N`。
- 相同确认命令在上下文恢复后可以重试成功。
- 确认成功后只推进一次 phase，只启动一次下一阶段。
- bootstrap 展示快照与 Agent 上下文不一致时返回明确业务错误。
- 不再出现“先产生 phase N+1 bootstrap，再因旧阶段工具上下文缺失而 `START_FAILED`”的事件序列。
- 显式 MYSQL state store 初始化失败时不会静默使用 MEMORY。
- AI 模块测试和 compile 阶段检查全部通过。
