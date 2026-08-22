# ChatRun 工具确认上下文恢复设计

> 本文说明 HITL 在 AgentScope 执行层和 Lambda Fusion 业务层之间的边界。ChatRun 不再维护独立的待确认状态。

## 1. 核心结论

页面能恢复一张确认卡片，不等于服务端仍有可继续执行的工具上下文：

- `ChatRunSnapshot.pendingTools` 只保存脱敏的工具 ID 和名称，用于 UI 恢复；
- AgentScope state 中 `ToolCallState.ASKING` 的 `ToolUseBlock` 才是可执行上下文；
- 确认必须同时校验快照、用户 decision 和 AgentScope 当前 `ASKING` 工具集合；
- 任一事实不可用或不一致时拒绝确认，不能根据展示快照伪造工具块。

ChatRun 在生成和等待确认期间都保持 `RUNNING`。`phaseNo` 负责确认幂等，AgentScope `ASKING` 负责执行状态。

## 2. 数据职责

| 数据 | 存储 | 用途 | 是否可执行 |
| :--- | :--- | :--- | :--- |
| `pendingTools(toolCallId, toolName)` | ChatRun snapshot | 页面确认卡片 | 否 |
| 完整 `ToolUseBlock`、参数、`ASKING` | AgentScope state store | 构造确认结果并继续 Agent | 是 |
| `phaseNo`、`aguiRunId` | ChatRun | HTTP 幂等和 AG-UI phase | 否 |
| 用户 decisions | 当前确认请求 | 同意/拒绝选择 | 否 |

快照不得复制完整敏感工具参数，也不得成为 AgentScope state 的替代品。

## 3. 进入待确认投影

```text
AgentScope REQUIRE_USER_CONFIRM
  -> 累加 pendingTools
  -> 暂存 RUN_FINISHED(interrupt)，尚不对订阅者可见
  -> 等根 AGENT_END（AgentScope 已保存 ASKING）
  -> 当前 HITL 适配流终止
  -> Workspace 审计
  -> 在事件缓冲区锁内写 ChatRun snapshot
  -> 数据库成功后发布 interrupt；失败则丢弃暂存事件
```

旧 phase 完整排空前，`sourceActive=true`，确认请求返回 `CHAT_RUN_STATE_CONFLICT`。这保证两个 AgentScope phase 不重叠。

数据库写入只更新仍为 `RUNNING` 的 Run 的快照和事件水位，不发生业务状态迁移，也没有确认截止时间。

## 4. 确认校验

确认入口在 `ChatRunInstance` 的实例锁内按顺序执行：

1. `phaseNo` 不能为空；
2. 当前 phase 大于请求 phase 时，按旧请求幂等重放返回 `resumed=false`；
3. 当前 phase 必须等于请求 phase，Run 必须是 `RUNNING`，源流必须已排空；
4. 快照必须存在非空 `pendingTools`；
5. decisions 的工具 ID 必须唯一、完整；
6. 从 AgentScope 读取 `(userId, sessionId)` state；
7. 只取最后一条 assistant message 中状态为 `ASKING` 的工具块；
8. snapshot ID、decision ID、AgentScope ID 三个集合必须严格相等。

只读取最后一条 assistant message，是为了与 AgentScope 当前待确认批次的口径一致。更早消息可能残留已处理的 `ASKING`，
扫描整个历史会把旧工具错误并入本次确认。

## 5. Phase 推进

三方校验成功后，持久化服务锁定 Session 和 Run：

1. 复核 Session 用户归属和 Run 归属；
2. 当前 `phaseNo > sourcePhaseNo` 时返回幂等结果；
3. 要求 `status=RUNNING` 且快照 phase 与来源 phase 一致；
4. 以 `(id, status=RUNNING, phaseNo=sourcePhaseNo)` 为条件更新到 `phaseNo+1` 和新的 `aguiRunId`；
5. 同一事务写入 `snapshot.beginPhase(...)`，清空 `pendingTools` 并关闭旧消息块；
6. 更新成功后构造 AgentScope confirm message，立即启动下一 phase。

同事务清空持久化投影很重要：若数据库 phase 已推进但旧 `pendingTools` 仍在，其他节点的页面重连会错误展示已经处理的确认卡片。

## 6. 错误契约

| 错误 | 含义 | Run 结果 |
| :--- | :--- | :--- |
| `CHAT_RUN_STATE_CONFLICT` | phase 不匹配、旧 phase 未排空或 Run 已终结 | 不推进 |
| `CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE` | AgentScope state/context/当前 ASKING 不可读取 | 保持 `RUNNING` |
| `CHAT_RUN_CONFIRM_CONTEXT_MISMATCH` | 三方工具 ID 不一致 | 保持 `RUNNING` |
| `INVALID_PARAMETER` | phase/decision 字段非法或重复 | 保持 `RUNNING` |

state store 短暂不可用时，用户稍后可提交相同 `phaseNo` 重试。上下文永久丢失时，用户只能显式停止旧 Run 并创建新 Run；
服务端不得用快照参数替代 AgentScope state 继续工具调用。

## 7. 跨节点行为

等待确认时没有活动模型源流，因此请求落到另一个 Lambda Fusion 节点时可以：

1. 从数据库快照重建确认卡片；
2. 按应用和租户取得 Agent；
3. 从共享 AgentScope state store 读取 `(userId, sessionId)` 的 `ASKING`；
4. 完成相同的三方校验和 phase CAS；
5. 在当前节点启动新的 phase。

这是“下一次业务调用从另一个节点开始”，不是接管旧模型流。若 AgentScope state store 不是共享持久化后端，确认上下文自然
无法跨节点读取，接口必须返回 unavailable。

## 8. 停止

快照含 `pendingTools` 且当前节点没有本地实例时，停止路径可以恢复 AgentScope 状态并把未决工具补成拒绝结果，避免同一会话
持续阻塞。该实例只做上下文清理和业务终结，不启动模型流，也不发送远程 interrupt。

即使 AgentScope 恢复失败，ChatRun 仍可提交 `STOPPED / USER_STOP`。清理失败记录日志，不得阻止用户放弃旧请求。

## 9. 明确删除的复杂度

本设计不需要：

- `AWAITING_CONFIRM` ChatRun 状态；
- 确认截止时间和全表超时扫描；
- 确认命令流水表、事务 outbox；
- Redis Stream 或跨节点 interrupt 事件；
- owner、lease、heartbeat；
- 用 Run 快照恢复完整工具参数。

## 10. 验证

- `RUNNING + pendingTools` 能生成 interrupt bootstrap；
- 旧 phase 未排空时确认被拒绝；
- 只读取最后一条 assistant message 的 `ASKING`；
- 三方 ID 不一致时数据库 phase 不变化；
- phase CAS 只成功一次，重放返回 `resumed=false`；
- phase 推进与清空 `pendingTools` 同事务完成；
- state store 暂时失败后，同一确认请求可重试；
- 跨节点确认不依赖旧节点内存事件；
- 停止待确认 Run 不调用远程 interrupt。
