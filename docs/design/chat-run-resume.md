# 对话后台续跑与断线恢复设计

> 状态：当前实现说明。多节点故障边界见 [ChatRun 多节点部署边界](chat-run-cluster.md)。

## 1. 目标

本功能解决浏览器连接生命周期与 Agent 执行生命周期不一致的问题：

- SSE 断开不取消后台 Agent；
- 页面刷新、切换会话或重新打开浏览器后，可以恢复最近持久化的文本、推理、工具和确认卡片；
- 最终助手消息与 Run 终态可靠落库；
- 相同客户端请求重放不会重复启动 Agent；
- 用户可以显式停止旧 Run，再创建新 Run 重试。

它不承诺应用节点失效后从旧 token 或旧工具调用中间点继续执行，也不建立跨节点实时事件总线。

## 2. 分层

```text
Controller / ChatServiceImpl
        |
        v
ChatRunServiceImpl ---- ai_chat_run / ai_chat_message
        |
        v
ChatRunCoordinator ---- local ChatRunInstanceRegistry
        |
        v
ChatRunInstance ------- local ChatRunEventStore
        |
        v
AgentExecutionAdapter - AgentScope HarnessAgent
```

| 组件 | 职责 |
| :--- | :--- |
| `ChatRunServiceImpl` | 幂等创建、四态业务终结、快照检查点、最终消息事务 |
| `ChatRunCoordinator` | 当前请求节点上的启动、确认、停止和本地实例选择 |
| `ChatRunInstanceRegistry` | 本 JVM 实例唯一性、容量和排空后摘除 |
| `ChatRunInstance` | Agent 订阅、事件解释、快照累加、终态提交 |
| `ChatRunEventStore` | 本 JVM 有界事件、订阅、短期重放和终态 TTL |
| `AgentExecutionAdapter` | 用业务 `(userId, sessionId)` 调用 AgentScope |

不存在启动恢复 Listener 或全局 MaintenanceScheduler。快照延迟写入属于单个实例，事件到期释放属于事件存储本身。

## 3. ChatRun 四态

```text
                 +--> COMPLETED
RUNNING ---------+--> STOPPED
                 +--> FAILED
```

`RUNNING` 表示“业务请求尚未终结”，不复制 AgentScope 内部状态。Agent 正在输出和 AgentScope 已进入 `ASKING` 都属于
`RUNNING`；待确认信息由快照 `pendingTools` 投影给前端。

终态更新以数据库行为准。第一个成功提交的 `COMPLETED / STOPPED / FAILED` 获胜，后到回调只能读取既有终态，不能覆盖。

不再使用以下中间状态：

- `CREATED`：创建事务直接写 `RUNNING`；
- `AWAITING_CONFIRM`：由 AgentScope `ASKING` + 快照投影表达；
- `STOPPING`：停止请求直接提交 `STOPPED`。

## 4. 幂等创建与启动

同一 Session 的创建流程：

1. 锁定用户拥有的 Session 行；
2. 规范化消息正文与附件 ID，计算 `requestHash`；
3. 命中 `(sessionId, clientRequestId)` 时校验 hash 并返回已有 Run，`created=false`；
4. 检查会话没有其他非终态 Run；
5. 同一事务写入用户消息、附件绑定和初始 `RUNNING` Run，`created=true`；
6. HTTP 编排层只有在 `created=true` 时调用 `ChatRunCoordinator.start`。

这条显式标记代替 `CREATED -> RUNNING` 认领和后台扫描。若当前节点装配 Agent 或容量检查失败，Run 立即终结为
`FAILED / START_FAILED`，不会留在隐藏队列中等待某个定时任务。

## 5. 执行、检查点与最终落库

### 5.1 正常阶段

```text
register local instance
  -> async subscribe AgentScope stream
  -> interpret AgentEvent
  -> append local AG-UI event
  -> update in-memory snapshot
  -> checkpoint when threshold/delay is reached
  -> root AGENT_END
  -> transactionally save assistant message + COMPLETED
  -> append terminal AG-UI event
```

根 `AGENT_END` 是业务回答边界，不一定是 AgentScope 整个 Flux 的结束。MemoryFlush、MemoryMaintenance、Sandbox release
和 Workspace 审计可能仍在尾部运行。业务终态可以先提交，但本地实例要等源流终止后才完成 `drainedSignal` 并摘除。

### 5.2 实例自有检查点

每次文本、推理或工具展示发生变化后，实例把快照标记为 dirty：

- 新增事件数达到 `snapshot-every-events` 时立即检查点；
- 否则只安排一个 `snapshot-interval-seconds` 后的延迟任务；
- 检查点成功后更新 `snapshot_seq` 并收缩已覆盖的本地事件；
- 终态或待确认快照已经提交后取消未执行的延迟任务。

该机制按活动实例工作，没有扫描全表或遍历所有 Run 的固定频率任务。

### 5.3 最终事务

最终事务锁定 Session 与 Run，提交：

- 终态、结束原因和脱敏错误；
- 助手正文与工具调用记录；
- 最终展示快照和事件水位；
- Session 最近消息时间。

已有终态时幂等返回既有结果。这样用户在其他节点显式停止后，旧节点迟到的完成回调不能覆盖 `STOPPED`。

## 6. SSE 与页面恢复

SSE 连接只持有 `ChatRunEventSubscription`：

```text
completion / timeout / network error -> subscription.close()
```

连接断开不会调用 AgentScope interrupt，也不会 dispose Agent Flux。

### 6.1 本地正式事件

`ChatRunEventStore` 为每个 Run 维护当前 JVM 内的有界窗口。正式事件具有递增 `seq`，SSE ID 为
`{chatRunId}:{seq}`。订阅在同一缓冲区临界区完成“历史回放到实时”切换；慢消费者只关闭自己的订阅。

终态后，事件存储为该缓冲区安排一次 TTL 到期任务并按实例身份删除。没有全局过期扫描。缓冲区不存在时，订阅返回
`CHAT_RUN_EVENTS_EXPIRED`，调用方退化为数据库 bootstrap。

### 6.2 Bootstrap

bootstrap 从当前实例快照或数据库 `snapshot_json` 合成 AG-UI 事件：

1. `RUN_STARTED`；
2. 已生成的 reasoning、工具和 text；
3. 快照有 `pendingTools` 时生成 `RUN_FINISHED(interrupt)`；
4. ChatRun 是终态时生成对应 `RUN_FINISHED` 或 `RUN_ERROR`。

bootstrap 事件不进入正式窗口，也不分配正式序号。若请求落到没有本地事件的节点，服务返回 bootstrap 后关闭连接；页面
仍可显示最近检查点，但不会假装拿到了另一节点的实时流。

## 7. HITL

AgentScope 是可执行确认上下文的权威来源：

1. 收到 `REQUIRE_USER_CONFIRM` 时，实例仅累加脱敏的工具 ID/名称并暂存 interrupt 事件；
2. 等根 `AGENT_END`，确认 AgentScope 已保存 `ASKING`；
3. 当前适配流终止并完成 Workspace 审计后，在事件缓冲区临界区写快照，再发布 interrupt；
4. ChatRun 状态始终为 `RUNNING`，快照 `pendingTools` 让页面恢复确认卡片；
5. 用户确认时要求旧 phase 已排空，并校验 snapshot、decision、AgentScope `ASKING` 三方工具 ID；
6. 以 `phaseNo` 为幂等键推进到下一 phase，同事务清空持久化 `pendingTools`，随后启动新的 AgentScope 调用。

确认上下文缺失、state store 不可用或三方不一致时不伪造 `ToolUseBlock`，不推进 phase，Run 保持 `RUNNING` 并返回明确
业务错误。确认没有业务层自动超时；用户可以稍后继续确认或显式停止。

详细契约见 [工具确认上下文恢复设计](chat-run-tool-confirm-context-recovery.md)。

## 8. 停止与重试

停止是业务终结，不是分布式接管：

1. 实例锁内先提交 `STOPPED / USER_STOP`；
2. 本机有活动源流时，在锁外尽力调用 AgentScope interrupt；
3. 宽限期后仍未结束时，仅对本地源流执行 dispose；
4. 本机没有实例时只提交业务终态，不恢复活动 Agent、不发送远程停止；
5. 快照含待确认工具时，可恢复 AgentScope state 并拒绝未决 `ASKING` 工具。

用户重试必须创建新 Run。旧 Run 终态阻止迟到结果提交，但已经发生的外部工具副作用不会自动撤销；工具自身应按业务风险
实现幂等、事务或补偿。

## 9. 故障语义

| 故障 | 结果 |
| :--- | :--- |
| 浏览器断开 | Agent 继续；重连从实例或数据库快照恢复 |
| 本地事件 TTL 到期 | 使用数据库 bootstrap |
| Agent 启动/执行失败 | `FAILED`，保留可用的部分输出 |
| 当前节点容量不足 | `FAILED / START_FAILED`，不排队扫描 |
| 应用节点失效 | 不接管旧流；保留最后检查点，用户停止后新建 Run |
| 用户停止与旧节点完成竞争 | 先提交的数据库终态获胜 |
| AgentScope 确认上下文不可用 | 保持 `RUNNING`，确认失败，可重试或停止 |

## 10. 配置

```yaml
lambda:
  fusion:
    ai:
      chat:
        run:
          connection-timeout-seconds: 300
          max-run-duration-seconds: 1800
          stop-grace-seconds: 30
          terminal-ttl-seconds: 600
          max-events: 4096
          max-bytes: 8388608
          max-active-runs: 200
          max-active-runs-per-user: 4
          subscriber-queue-size: 256
          snapshot-every-events: 100
          snapshot-interval-seconds: 15
```

不存在节点心跳、租约、确认超时、Redis 事件或接管相关配置。

## 11. 安全与租户

- Controller 链路先校验 Run 属于目标 Session 和当前用户；
- 异步回调使用已校验 Session 的 `tenantId` 恢复租户上下文；
- 日志、SSE、快照和错误消息不得包含完整提示词、凭据或敏感工具参数；
- Run 快照只保存展示所需的脱敏确认信息，完整工具块留在 AgentScope state。

## 12. 验证

至少覆盖：

- SSE 断开不取消 Agent；
- 相同 `clientRequestId` 不重复启动；
- dirty 快照由实例延迟任务写入；
- 终态事件缓冲自行到期；
- `RUNNING + pendingTools` 能 bootstrap 确认卡片；
- 确认只推进一次并清空旧投影；
- 显式 `STOPPED` 不被迟到 `COMPLETED` 覆盖；
- 无本地实例时停止不调用远程 interrupt。

执行：

```shell
mvn -pl lambda-fusion-ai test
mvn -pl lambda-fusion-ai -am compile
```
