# ChatRun 多节点部署边界

> 本文定义 Lambda Fusion 业务层与 AgentScope 执行层的职责边界。断线展示恢复见
> [对话后台续跑与断线恢复设计](chat-run-resume.md)，Agent 调用语义见
> [ChatRun 与 AgentScope 执行语义](chat-run-agentscope-execution.md)。

## 1. 结论

一次 ChatRun 的一次活动调用最终只在一个 Lambda Fusion 应用实例中执行。AgentScope 负责 Agent 状态、同会话串行、
HITL 和它自身提供的持久化/多节点能力；Lambda Fusion 只保存业务请求结果和页面恢复投影，不再实现第二套分布式执行框架。

因此 `chat.runtime` 明确不实现：

- 节点注册、节点心跳和存活判定；
- owner、epoch、lease、fencing；
- Redis Stream、Pub/Sub 远程停止和跨节点事件转发；
- 运行中模型流或工具调用的自动接管；
- 启动恢复扫描、全局状态维护扫描和确认超时扫描。

部署能力应表述为：**多实例部署、单 Run 单实例执行、浏览器断线后基于数据库快照恢复展示**，而不是“单 Run 多节点
无感故障转移”。

## 2. 两层职责

| 层次 | 权威事实 | 职责 |
| :--- | :--- | :--- |
| AgentScope | `(userId, sessionId)` Agent state、`ASKING` 工具块、调用串行与中断 | Agent 执行会话与 HITL |
| Lambda Fusion | ChatRun 四态、最终消息、展示快照、短期本地事件 | 业务请求、HTTP/SSE 和页面恢复 |

ChatRun 不复制 AgentScope 的执行状态机。它只有四个状态：

```text
RUNNING -> COMPLETED
        -> STOPPED
        -> FAILED
```

- `RUNNING`：这次业务请求尚未终结；既可能正在生成，也可能 AgentScope 已处于 `ASKING`。
- `COMPLETED`：正常回答已提交。
- `STOPPED`：用户显式放弃旧 Run。
- `FAILED`：当前请求执行失败，可由用户决定是否新建 Run 重试。

待确认不是第五个 ChatRun 状态。数据库快照中的 `pendingTools` 只是 UI 投影；可执行的确认上下文必须从 AgentScope
的 `ASKING` state 读取并校验。

## 3. 正常执行与浏览器断线

```text
HTTP chat
  -> 同一事务创建 RUNNING Run 和用户消息
  -> 当前节点注册本地 ChatRunInstance
  -> 异步调用 AgentScope
  -> 本地事件窗口推送 SSE
  -> 实例按事件数或自身延迟任务写数据库快照
  -> 根 AGENT_END 提交最终消息和终态
```

SSE 只是订阅者。浏览器刷新、关闭或网络断开只关闭订阅，不取消 AgentScope 调用。用户再次打开页面时：

1. 本节点仍有实例时，从实例快照生成 bootstrap，再接本地实时事件；
2. 请求落到其他节点或本地事件已过期时，从数据库快照生成 bootstrap 后关闭 SSE；
3. 用户至少能看到最近一次成功检查点和最终结果。

未到检查点的最后少量增量允许在节点失效时丢失；本设计不为 token 级零丢失引入分布式事件总线。

## 4. 节点失效后的行为

假设 A 正在执行，浏览器重连到 B：

| 情况 | B 的行为 |
| :--- | :--- |
| A 正常、请求仅未路由回 A | 返回数据库 bootstrap；不访问 A 的内存事件或发送远程命令 |
| A 已失效 | 返回最后持久化快照；不重放旧模型流或工具调用 |
| Run 正在等待确认 | 从快照恢复确认卡片；提交确认时从 AgentScope state store 读取真实 `ASKING` 上下文 |
| 用户选择停止/重试 | 先把旧 Run 终结为 `STOPPED`，再创建一个新的 `RUNNING` Run |

数据库中的旧 Run 可能仍显示 `RUNNING`，因为业务层不根据心跳猜测节点是否死亡。此时由用户显式停止最清楚：它既避免
误杀只是短暂网络抖动的调用，也不需要业务层再造节点故障检测。

## 5. “停止后重试”的精确定义

“重试”不是 B 接管 A 的旧 Run，而是显式放弃旧请求并创建新请求：

```text
old Run: RUNNING --user stop--> STOPPED
new Run:                       RUNNING --> COMPLETED / FAILED / STOPPED
```

停止先提交数据库 `STOPPED`。若当前节点持有活动实例，再在锁外尽力调用 AgentScope interrupt，并在本地宽限期后释放仍未
结束的源流；当前节点没有实例时不恢复活动 Agent、不发送跨节点停止命令。

如果 A 其实仍活着，它之后提交完成结果时会看到数据库已经是终态，不能把 `STOPPED` 覆盖为 `COMPLETED`。但在停止前
已经发生的邮件发送、支付、文件写入等外部工具副作用无法由 ChatRun 回滚。需要强一致的工具必须自行提供幂等键、事务或
补偿机制；这不是节点接管能自动解决的问题。

待确认 Run 没有活动模型源流。停止时可以恢复 AgentScope 持久化状态并拒绝未决 `ASKING` 工具，避免阻塞同一会话；
若 AgentScope state 不可用，则仍以业务 `STOPPED` 为准，不伪造执行上下文。

## 6. 启动、容量和幂等

- `createOrLoad` 返回 `created` 标记；只有本次新建的 Run 才启动。
- 相同 `clientRequestId` 的 HTTP 重放只重新挂接展示，不重复启动 Agent。
- 创建时直接写 `RUNNING`，不保留 `CREATED` 队列或后台认领任务。
- 当前节点容量不足或装配失败时立即把 Run 终结为 `FAILED / START_FAILED`，由用户决定是否重试。
- 确认使用 `phaseNo` 做幂等键；同一来源 phase 只推进一次，ChatRun 状态仍为 `RUNNING`。

进程在“Run 已创建但 Agent 尚未订阅”之间退出时，记录会保持 `RUNNING`。系统不自动猜测并重启它，因为重复启动模型或
工具比要求用户显式重试更危险。

## 7. 本地事件与快照

`ChatRunEventStore` 是当前 JVM 的有界实时窗口，不是跨节点日志：

- 每个 Run 的正式事件在本地严格递增；
- 快照覆盖事件后可以收缩窗口；
- 终态缓冲由事件存储自身安排 TTL 释放，不需要全局清理扫描；
- 事件不存在时客户端退化到数据库 bootstrap。

`ChatRunInstance` 在产生新展示内容后把快照标记为 dirty：达到事件阈值时立即写入，否则由该实例自己的单次延迟任务写入。
不存在遍历所有 Run 的定时检查点任务。

## 8. 后续扩展原则

只有出现明确的“旧调用必须跨节点无感接续”需求，并且 AgentScope 公开能力无法覆盖时，才应另立专项设计。专项至少要处理
模型流可恢复点、工具幂等、外部副作用和成本上限，不能在现有业务层逐步堆叠 owner/lease/heartbeat 形成隐式调度器。
