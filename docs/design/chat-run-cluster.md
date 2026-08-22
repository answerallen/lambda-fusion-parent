# ChatRun 多节点部署边界

> 本文不是一套应用层集群执行协议，而是说明 Lambda Fusion 业务层与 AgentScope 执行底座之间的边界。
> 后台续跑与页面恢复见 [对话后台续跑与断线恢复设计](chat-run-resume.md)，调用语义见
> [ChatRun 与 AgentScope 执行边界设计](chat-run-agentscope-execution.md)。

## 1. 结论

`chat.runtime` 是 AgentScope 之上的业务编排层，只负责：

- ChatRun 的创建、状态、快照、最终消息和 HITL 阶段；
- 把本次 AgentScope 调用的事件转换成 AG-UI；
- 当前进程内的短期事件回放和 SSE 订阅；
- 当前进程内执行实例的启动、停止与资源释放。

AgentScope 负责 Agent 执行、状态存储、会话并发保护、Workspace 和它自身的多节点部署能力。Lambda Fusion 不在
`chat.runtime` 重复实现节点发现、执行所有权、心跳、租约、fencing、运行中调用接管或跨节点 Agent 控制。

当前部署模型是：**一个 ChatRun 的一次活动调用落在一个应用实例；节点失效后不接管旧调用，由用户显式重试并创建新
ChatRun。** 多实例部署可以提高新请求承载能力，但不宣称正在生成的单个 Run 可无损故障转移。

## 2. 保证与非保证

### 2.1 保证

- SSE 断开只解除浏览器订阅，不取消仍在当前进程运行的 AgentScope 调用。
- 浏览器刷新、关闭后重新打开，可以从数据库中的 `snapshot_json` 和 Run 状态恢复已检查点的展示内容。
- 同一 `clientRequestId` 幂等，不因 HTTP 重试重复创建同一业务 Run。
- `CREATED -> RUNNING` 使用数据库 CAS，多个应用实例同时看到尚未启动的 Run 时只有一个能够开始调用。
- Run 终态提交幂等；一个 Run 已被停止后，迟到的完成结果不能覆盖该终态或重复保存最终助手消息。
- 停止请求落到持有本地执行实例的节点时，会中断本地 AgentScope 调用；否则只结束业务 Run，不伪造远程 Agent 实例。
  `AWAITING_CONFIRM` 没有活动源流，可读取 AgentScope 持久化状态并拒绝未决工具，但不会调用 interrupt。

### 2.2 不保证

- 不保证节点宕机后从 token 中间继续旧模型流。
- 不保证任意应用节点都能继续订阅另一节点内存中的实时增量。
- 不保证非执行节点能够远程中断执行节点中的 Agent。
- 不保证“用户重试”与旧节点上的外部工具调用具备 exactly-once 语义。
- 不用 Redis Stream、Pub/Sub、owner、epoch、lease 或 heartbeat 构造第二套 AgentScope 运行时。

## 3. 运行与恢复路径

### 3.1 正常运行

```text
浏览器
  -> Lambda Fusion：创建/加载 ChatRun
  -> 当前应用实例：CAS 认领 CREATED
  -> AgentScope：streamEvents
  -> 本地 ChatRunEventStore：短期事件窗口
  -> 数据库：周期快照、业务终态和最终消息
```

本地事件窗口用于低成本实时流和短期重放，不是跨节点事件总线。数据库快照是页面恢复事实，但不是可恢复执行上下文；
AgentScope state store 才承载 Agent 状态。

### 3.2 浏览器断开后回到同一实例

若本地事件窗口仍存在，服务端先发 bootstrap，再从该水位继续回放和订阅实时事件。后台执行不依赖 SSE 生命周期。

### 3.3 浏览器断开后落到另一实例

新实例没有旧实例的事件窗口时：

1. 从数据库加载最新 Run 和持久化快照；
2. 合成 bootstrap，恢复已检查点的文本、推理和工具展示；
3. 关闭本次 SSE，不等待不存在于本机的实时流；
4. 前端根据 Run 仍非终态且实时流不可用，提供重新连接或“放弃旧 Run 并重试”。

这里不创建 Redis 事件总线，也不根据“本机没有事件”推断执行节点已经死亡。负载均衡配置粘性路由可以改善实时体验，
但不是业务正确性的前提。

## 4. A 假死、B 收到重试时的行为

“重试”不是 B 接管 A 的旧 Run，而是两个明确步骤：

| 时刻 | 业务行为 |
| --- | --- |
| A 执行旧 Run | DB 为 `RUNNING`，A 持有本地 AgentScope 调用和实时事件窗口 |
| 浏览器转到 B | B 只能返回旧 Run 的持久化快照；不恢复旧模型流，也不操作 A 的内存对象 |
| 用户选择重试 | B 先把旧 Run 收敛为 `STOPPED`，再以新的请求 ID 创建新 Run |
| B 执行新 Run | B 发起一次新的 AgentScope 调用；它与旧 Run 有不同的 `runId` |
| A 其实仍存活 | A 的旧 Run 最终提交看到 DB 已是终态，只能读取既有结果，不能覆盖为 `COMPLETED` |

B 对旧 Run 的停止是**业务状态停止**，不是远程停止 A。若 A 仍在执行，进程内计算或外部工具调用可能继续，直到 A
自身结束、失败或触发本地超时。
数据库终态只能阻止旧 Run 再提交 Lambda Fusion 的最终消息，不能撤销已经发生、也不能天然阻止尚在进行的外部系统副作用。

因此，涉及支付、发信、删改数据等非幂等工具时，工具边界仍需使用业务幂等键。推荐至少使用
`(chatRunId, toolCallId)` 或目标系统自己的请求幂等键；高风险动作继续通过 HITL 确认。若业务要求在网络分区下仍严格阻止
旧执行继续调用外部系统，就必须由工具服务提供 fencing/idempotency，单靠 ChatRun 数据库终态无法做到。

## 5. 停止语义

停止接口按本地实例和业务状态处理：

- 本机存在该 Run 的活动实例：CAS 为 `STOPPING`，请求 AgentScope 中断，并在本地宽限期后停止源流；
- 本机不存在活动实例且 Run 正在执行：使用无 Agent 的终结器把业务 Run 收敛为 `STOPPED`，不恢复 Agent、不发送跨节点命令；
- Run 为 `AWAITING_CONFIRM`：可构造只用于确认上下文清理的 AgentScope 适配器，把持久化 `ASKING` 工具补成拒绝结果，
  随后收敛业务终态。该路径没有活动源流，不调用 interrupt。

应用停机时对本机活动实例执行检查点和本地中断属于资源释放，不是远程故障转移协议。

## 6. 保留的数据库并发边界

业务层仍需要少量数据库原子性，但它们与节点所有权无关：

- `CREATED -> RUNNING`：防止同一个业务 Run 被重复启动；
- `AWAITING_CONFIRM -> RUNNING`：按 `phaseNo` 保证确认幂等；
- `* -> STOPPING`：让已接受的用户停止优先于迟到的完成；
- `非终态 -> 终态`：只提交一次最终消息和终态；
- 快照更新：只允许非终态 Run 写入，解析损坏的快照直接失败，不用空快照静默覆盖。

本地实例容量检查和注册在同一临界区完成。容量已满时保留 `CREATED`，后续本地维护再次尝试；不再增加调度超时错误码或
跨节点容量协议。

## 7. 数据库结构

开发基线已压平，不再创建 `owner_instance_id`、`lease_until`、`lease_epoch`、`heartbeat_at` 及对应索引。
`ChatRunEntity` 和运行时代码也不存在这些字段。

## 8. 部署声明

对外应表述为：

> 支持多实例部署、浏览器断线后台继续执行，以及基于数据库快照的页面恢复；单个正在生成的 ChatRun 不提供应用层无损接管，
> 节点异常时由用户显式放弃旧 Run 并重试。

不得表述为“ChatRun 多节点高可用执行”“任意节点无缝续流”或“外部工具 exactly-once”。

## 9. 验收项

- 代码中不存在 ChatRun 节点 ID、heartbeat、owner、epoch、lease、失效扫描和远程停止通道。
- 旧 `RUNNING` Run 在非执行节点停止时不构建带 Agent 的执行实例，也不调用 Agent 中断；待确认上下文清理同样不调用中断。
- 无本地事件窗口的恢复返回持久化 bootstrap 后关闭 SSE。
- SSE completion、timeout 和 error 只关闭订阅，不停止后台 Run。
- 并发注册不会突破本地全局/用户容量上限。
- 无效快照不会退化为空快照并覆盖持久化内容。
- `mvn -pl lambda-fusion-ai test` 与 `mvn compile` 通过。
