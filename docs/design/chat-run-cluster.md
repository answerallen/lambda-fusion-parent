# 对话后台运行集群化改造设计草案

> 目标：把当前「单实例重启恢复」的对话后台运行，演进为「多实例同时运行」的集群能力。
>
> 状态：**设计草案（未实施）**。本文描述目标架构，不是当前代码的事实描述；当前实现的权威说明仍以
> [对话后台续跑与断线恢复设计](chat-run-resume.md) 与 [ChatRun 与 AgentScope 执行边界设计](chat-run-agentscope-execution.md) 为准。
>
> 前置文档边界：本草案正是上述两份文档显式列为「本次不做」的边界——
> `chat-run-resume.md` §2.2 与 `chat-run-agentscope-execution.md` §2.2/§6.5 均声明当前不引入
> Redis Stream、执行节点租约、跨节点停止命令、事务 outbox 与多节点执行迁移。本文在其之上展开。

## 0. 范围与故障模型

**`chat.runtime` 的职责**：把 Agent 执行从一次 HTTP/SSE 连接解耦；管理 ChatRun 的启动、确认、停止、终结和恢复；把 Agent 事件
转成 AG-UI 事件；维护展示快照与短期事件回放；保证同一 Run 在应用层只有一个有效执行实例；页面断线后可 bootstrap 而非重跑 Agent。

**`chat.runtime` 不负责**：AgentState 的分布式事务管理、Workspace 的并发隔离、工具副作用的 exactly-once、通用分布式任务调度。
因此本设计的集群化**只解决这个包原本的本地状态问题**，不解决整个 AgentScope 的分布式隔离。

**集群化只需解决 5 件事**：

1. DB owner/lease 防止多节点同时认领同一 ChatRun；
2. Redis Stream 替换 JVM 本地事件缓冲，让任意节点能提供 SSE；
3. DB `STOPPING` + Pub/Sub 实现跨节点停止；
4. owner 丢失后用最后 DB 快照终结，不迁移、不续跑原模型调用；
5. 本地 Registry 只保存当前节点执行实例。

**AgentState / Workspace 只作为部署前提**：集群模式必须使用可跨节点访问的 StateStore/Workspace；它们自身的一致性、
工具副作用和旧调用残留**不由 `chat.runtime` 负责**，延续现有非目标「工具和 Workspace 已产生的副作用不保证回滚」。

**故障模型**：本期采用 **crash-stop**，异常失效节点不再继续产生事件；数据库短暂不可用时，owner 按租约安全边界自我隔离；
计划内停机走优雅 drain。在此基线上，跨 ownership generation 的严格连续序号、Redis 侧强 fencing、边界事件精确幂等、以及
AgentState/Workspace 的 generation 隔离，**都不是本设计的范围**（前者移入 §12 演进；后者属更高等级的分布式执行一致性，
本不属 `chat.runtime`）。

代价：节点失效前已经写入 Stream、但尚未进入最后 DB 快照的有限尾部，可能被客户端短暂展示，随后被 bootstrap reset；
这些增量不进入持久化历史。

## 1. 现状诊断

结论先行：**仅 Run 业务状态具备部分跨节点并发保护，整体仍是单 JVM 设计。**

| 层面 | 当前实现 | 集群能力 |
| :--- | :--- | :--- |
| Run 业务状态 | DB + CAS/行锁 | 部分支持 |
| Agent 状态 | 可配置 Redis/MySQL 等 state store | 可跨节点恢复（一致性由该组件负责） |
| 实时事件 | JVM `ConcurrentHashMap` 缓冲 | 不支持 |
| 执行实例/调度 | JVM 本地注册表与线程池 | 不支持 |
| 启动恢复 | 扫描全部中断态 Run | 多节点有风险 |

- **事件面**：缓冲、订阅者、发送线程池全在本地 JVM；A 节点执行时 B 节点续看只能拿快照或 `CHAT_RUN_EVENTS_EXPIRED`。
- **执行面**：实例注册表是本地 `ConcurrentMap`；`listInterruptedOnRestart` 只按状态过滤、无 owner/租约条件；
  `recoverInterrupted` 对 RUNNING Run 无条件 `INSTANCE_LOST`，滚动发布会误终结健康 Run。
- 现有写路径 `checkpoint`/`awaitConfirm`/`finalizeExecution` 只按「非终态」CAS，**无 owner/epoch 校验**（§5）。
- `ChatRunCoordinator.startIfCreatedInTenantContext` 容量满时把 `CREATED` 终结为 `RUN_CAPACITY_EXCEEDED`（§7.2）。
- 现有终结器把 Stream tail / 终态序号写进 `snapshot_seq` 并 `compact`——集群接管路径**不复用**它（§6.3）。

## 2. 目标与非目标

### 2.1 必须保证

- 任意 API 节点都能为任意 Run 提供续看与实时事件，与执行节点无关。
- Run 任一时刻**至多一个有效租约持有者**；过期节点对 `ai_chat_run` 的写被 fencing 拦截、不生效。
- 滚动发布（配合 drain）、节点宕机（crash-stop）、故障转移时，Run 不被误终结、不被并发执行。
- 启动与周期扫描只接管「所有权已失效」的 Run。
- 客户端在 INSTANCE_LOST / 边界缺失后能可靠重新 bootstrap，持久历史与最终展示一致。

### 2.2 明确不保证

- 不保证 JVM 崩溃后从中间 token 继续同一次模型调用。
- 不保证工具已产生的外部副作用可回滚；**共享 AgentState / Workspace 的一致性与旧调用残留不属本设计范围**（§0）。
- 不保证节点失效前已写入 Stream、但未进入最后 DB 快照的尾部增量不短暂出现在线订阅者（会被 bootstrap reset，永不进历史）。
- 不保证跨 phase / 跨接管的 `seq` 全局连续（只要求 phase 内递增）。

## 3. 最小改造点

1. Run 增加 `owner_instance_id` + `lease_until` + `lease_epoch`；
2. 只有租约过期/无主的 Run 才能被接管，接管、续约、DB 写经 fencing 闭环；
3. 事件缓冲改为 Redis Streams，按 phase 分 key；
4. 任意 API 节点按 bootstrap 游标 `(phaseNo, seq)` 从对应 phase Stream 订阅；
5. 本地 Registry 只管理当前节点持有效租约的 Run；
6. 优雅停机 drain（§8.4）。

## 4. 数据库设计

`ai_chat_run` 追加三列（追加式 changeSet）：

| 字段 | 用途 |
| :--- | :--- |
| `owner_instance_id` | 当前持有执行的节点标识；`NULL` 表示无主 |
| `lease_until` | 租约截止时间（数据库时间）；过期后他节点可接管 |
| `lease_epoch` | fencing 令牌，单调递增；认领/接管 +1，终态后保留不清零 |

索引追加：普通索引 `(status, lease_until)`。

约定：

- `owner_instance_id` 取启动节点唯一实例 ID（`spring.application.name` + 进程启动时自动生成的 boot UUID），不作为人工配置项。
- 租约与确认截止时间统一用**数据库时间**（`NOW()`）；确认超时 CAS 内比较 `deadline <= dbNow`。
- `lease_epoch` 设计为 **`NOT NULL DEFAULT 0`**（禁止新旧混部，无需 nullable + `COALESCE`）；认领/接管用 `lease_epoch + 1`。
- 终态只清空 `owner_instance_id` / `lease_until`，保留 `lease_epoch`。
- `phaseNo` / `aguiRunId` 已持久化，接管节点据此定位当前 phase Stream。

## 5. 所有权、租约与 fencing

### 5.0 租约参数

| 参数 | 含义 |
| :--- | :--- |
| `lease-ttl` | 租约时长（DB 时间） |
| `renew-interval` | 续约周期 |
| `scan-interval` | 失效租约扫描周期 |
| `safety-margin` | 本地自我隔离安全余量 |

- 启动校验：`lease-ttl >= 3 × renew-interval`，`safety-margin` 覆盖一次续约抖动；
- 节点在 `本地租约到期点 − safety-margin` 前未续约成功，即停止输出（不再 checkpoint / XADD / 提交终态）。

### 5.1 写操作矩阵（epoch 何时递增）

**只有「所有权转移」才递增 epoch；普通 owner 写只校验、不递增。**

| 操作 | epoch +1 | 说明 |
| :--- | :--- | :--- |
| `claimCreated`（CREATED→RUNNING） | **是** | 认领，写新租约 |
| 接管 takeover | **是** | 旧租约须仍过期，CAS 闭环见 §5.2 |
| 确认（AWAITING_CONFIRM→RUNNING） | **是** | 认领新 owner，同事务切 phase/快照/游标（§6.1） |
| 确认超时 / 无执行者 stop 认领 | **是** | **认领 + 转 STOPPING 一条 SQL**（§5.5） |
| 续约 renew | 否 | 旧租约须仍有效，过期不得原地复活 |
| `checkpoint` / `awaitConfirm` / `finalizeExecution` | 否 | owner/epoch + 租约有效校验 |
| 用户对 RUNNING 发起 stop | 否 | 保留 owner/epoch，CAS 状态，不要求请求节点是 owner |

约束：epoch 与状态迁移在**单条 SQL** 内原子完成。

### 5.2 接管条件与 CAS 闭环

```text
status in (RUNNING, STOPPING)           -- 不含 AWAITING_CONFIRM
AND (owner_instance_id IS NULL OR lease_until < dbNow)
```

接管 CAS 把「租约仍过期」+「旧 owner/epoch」放进**同一条 SQL**：

```text
UPDATE ai_chat_run
   SET owner_instance_id = me, lease_until = dbNow + leaseTtl, lease_epoch = lease_epoch + 1
 WHERE id = ?
   AND status in (RUNNING, STOPPING)
   AND (owner_instance_id IS NULL OR lease_until < dbNow)
   AND owner_instance_id <=> expectedOwner
   AND lease_epoch      <=> expectedEpoch
```

### 5.3 周期性失效租约接管

仅启动时扫描不够（A 在所有节点启动后宕机，B/C 不会再触发就绪事件）。新增周期任务：

```text
scanExpiredOwnedRuns() -> §5.2 条件扫描 -> 闭环 CAS 抢占 -> 按状态收敛
```

| 状态 | 接管动作 |
| :--- | :--- |
| `RUNNING` | 用**最后一次 DB 快照**经 lost-instance 路径终结为 `FAILED / INSTANCE_LOST`（§6.3），**不写 Stream、不重启模型调用** |
| `STOPPING` | 终结为 `STOPPED / USER_STOP` |

### 5.4 fencing

租约只决定「谁有资格接管」，`lease_epoch` 用于拒绝 ownership 变化后迟到的旧 DB 请求，**只在 DB 侧 fencing**：
所有 owner 写按 §5.1 校验 `owner/epoch` + 租约有效。Redis 事件面不做 fence；crash-stop 下失效 owner 不再继续输出，
最后 DB 快照未覆盖的有限 Stream 尾部由 bootstrap reset 清掉。

### 5.5 AWAITING_CONFIRM 的所有权

现有不变量 `AWAITING_CONFIRM => sourceActive == false`（phase 已排空）。**AWAITING_CONFIRM 不纳入 §5.3 失效扫描**。

| 触发 | 动作 |
| :--- | :--- |
| 进入待确认 | DB 原子写 `AWAITING_CONFIRM` + 快照 + 清 owner/lease；interrupt 事件提交后**尽力写**当前 phase Stream |
| 用户确认 | 任意节点原子 `AWAITING_CONFIRM -> RUNNING` + 认领 owner/epoch/lease，**同事务切 phase/快照/游标**，开新 phase Stream |
| 确认超时 | 任意节点**认领 + 转 `STOPPING` 一条 SQL**，再由 owner 终结为 `STOPPED / CONFIRM_TIMEOUT`；deadline 用 DB 时间 |
| 无执行者 stop | 任意节点**认领 + 转 STOPPING 一条 SQL**，再终结 |
| 单机 MEMORY 重启 | 走**独立启动恢复**校验并终结，不走租约接管 |

**注册表**：释放租约后按实例身份摘除；`confirm` 候选先恢复/校验，**DB 认领成功后再注册**。

## 6. 共享事件流（每 phase 一条 Stream）

### 6.1 分 key、游标与事件顺序

实时事件需**可重放 + 有水位**，选 Redis Streams。**按 phase 分 key**：

```text
chat-run:{runId}:phase:{phaseNo}
```

**游标模型** `cursor = (phaseNo, aguiRunId, seq)`，SSE `id = {chatRunId}:{phaseNo}:{seq}`，避免不同 phase 重复 ID。

**确认切 phase 原子闭环**（当前实现只改 `phase_no`/`agui_run_id`，累加器切 phase 在事务提交后，会让 bootstrap 跳过新 phase 事件，
必须改）：事务前基于当前快照构造不可变的 `nextPhaseSnapshot`（不修改当前累加器），确认事务**同一事务**内完成：

1. 更新 `phase_no` / `agui_run_id`；
2. 持久化 `nextPhaseSnapshot`；
3. **`snapshot_seq` 重置为 0**；
4. 写入 owner/epoch/lease；

提交成功后再用同一份 `nextPhaseSnapshot` 切换内存累加器；**新 Stream 第一条必须是 `1-0`**（Redis 不允许 `0-0`）。

**事件顺序（待确认与正常终态统一）**——DB 事实优先、事件尽力发布：

```text
应用 snapshot delta
    -> DB 原子写状态 + 快照 + 清 owner/lease
    -> 提交成功后尽力写边界事件（interrupt / 终态）到当前 phase Stream
```

- `snapshot_seq` 保持「最后**内容**事件水位」，**不含 interrupt / 终态事件**；
- 即便 Stream 写失败，bootstrap 也能根据 DB 快照**合成**边界事件，无需 outbox；
- 正常终态同原则：DB 终态优先、终态事件尽力发布、不再 `recordTerminalSeq`。

### 6.2 序号与幂等（最简）

- Stream entry ID 用 `{seq}-0`；同一 phase 内由当前 owner 单点写入，`seq` 从 1 递增；
- `XADD` 遇到重复 ID 时，用 `XRANGE id id` 读取原 entry：相同 payload 视为重试成功，不同 payload 视为冲突；
- **不需要** `fence_epoch` / `OPEN`/`SEALED` / `next_seq` 元数据，**不需要** Lua 原子 fencing。

### 6.3 接管终结：不写 Stream 的 lost-instance 路径

**接管节点终结 `RUNNING` Run 时完全不写 Stream**，只提交 DB 终态，由订阅端复核 DB 并 resync。接管终结不分配事件序号，
因此无需 tail 校准、Redis 序号分配器与 fence。

接管终结**只用最后一次 DB 快照**，丢失最后 checkpoint 后的增量——与单节点重启恢复一致。「回放 AG-UI Stream 尾部重建完整
accumulator」在当前事件模型下不可实现（工具结果增量只更新 `snapshotDelta`、不产生 AG-UI 事件）。

| 终结路径 | 快照来源 | `snapshot_seq` | 是否写 Stream |
| :--- | :--- | :--- | :--- |
| 正常 owner 终结 | 最终 accumulator | 内容水位（不含终态事件） | 终态事件尽力写 |
| **INSTANCE_LOST 接管终结** | **只用持久化 `snapshot_json`** | **保持原值不变** | **不写** |

`snapshot_seq` 永不被接管路径抬升；终态清理只按真正被 `snapshot_json` 覆盖的水位。

### 6.4 容量、TTL 与 Redis 故障

- **owner 本地保留每 phase 的总事件数 / 总字节上限**；超限时保存最后快照并统一收敛为 `FAILED / ERROR`；
- 单事件大小限制；安全 TTL；
- **活动 Stream 不得被静默淘汰**：所在 Redis 实例/集群使用 `noeviction` 并满足容量要求；是否独立部署由容量与 SLA 决定
  （逻辑 DB 不能隔离淘汰策略）；
- **孤儿 key**：RUNNING 时活动 key 设较长安全 TTL（续约时续期）；进入 AWAITING_CONFIRM 后已无 owner 续期，直接设 phase-closed TTL；
  终态缩短为 `terminal-ttl`；
- **Redis 故障**：活动 XADD 在重复 ID 核验后仍失败，则保存最后 DB 快照并终结，**禁止切换内存后端**。

### 6.5 订阅路由与跨节点停止

- 续看/订阅请求落任意节点：对对应 phase Stream 按游标 `XRANGE`/`XREAD` 即可，不要求路由到执行节点；
- 慢订阅者只断开自身；订阅水位是 bootstrap 后的内部游标 `(phaseNo, seq)`，**不暴露公开 `afterSeq` / `Last-Event-ID`**；
- `XREAD` 使用有界阻塞超时；当前 Stream 的有限尾部消费完且本轮无新事件时，复核 DB 的 `(status, phaseNo, leaseEpoch)`；
  发现状态或 generation 变化即触发 §6.6 resync；
- **跨节点停止**：DB `STOPPING` 为持久事实 + **Redis Pub/Sub 即时唤醒** + owner 周期复核 DB 兜底：
  任意节点 `... -> STOPPING` CAS（不要求请求节点是 owner）；同时向该 Run 的 Pub/Sub 频道发唤醒；执行节点复核发现
  `STOPPING` 即 `interrupt`；禁止非持有节点直接操作他节点本地 Agent 订阅；控制信号不进事件 Stream。

### 6.6 强制 resync 协议

**选定 `RESYNC_REQUIRED` 控制事件**（不用 `completeWithError`：已建立的 SSE 响应异常后浏览器通常拿不到后端异常类型）：

```text
发送 RESYNC_REQUIRED 控制事件（不入 Stream、不占业务 seq）
    -> 关闭 SSE
    -> 前端查 Run 状态 -> 删除临时气泡 -> bootstrap 或刷新历史
```

任何意外断线也执行相同状态检查（控制事件可能来不及送达）。覆盖两类：缺边界事件，以及 INSTANCE_LOST 时页面持有
未检查点尾部增量、必须 reset。

## 7. 启动恢复与定时维护

### 7.1 启动恢复

- `recoverOnStartup` 扫描条件改为 §5.2 的租约过期条件（仅 RUNNING/STOPPING）；
- `AWAITING_CONFIRM` 单机 MEMORY 重启的确认上下文校验走**独立启动恢复**，不走租约接管；
- `CREATED` 拉起多节点并发，靠 `claimCreated` CAS 幂等。

### 7.2 维护任务分片与容量

| 任务 | 多节点策略 |
| :--- | :--- |
| `purgeExpired` | 集群模式由 Stream `EXPIRE` 接管；仅内存后端保留本地清理 |
| `checkpointIfDue` | 仅对本节点持有效租约的实例执行，按 §5.1 校验 |
| `listCreated` 拉起 | `claimCreated` CAS 去重；**集群下载满节点跳过、不得终结** |
| `listExpiredConfirmations` | **认领 + 转 STOPPING 一条 SQL**，再走 owner 终结；deadline 用 DB 时间，不加失效租约条件 |
| **新增 `scanExpiredOwnedRuns`** | 周期扫描过期 RUNNING/STOPPING，闭环 CAS 抢占后按 §5.3 收敛 |

- **`listCreated` 与容量**：集群下满载节点**跳过该 Run（不改状态）**，留空闲节点认领；
- **全节点长期满载**：`CREATED` 超过 `schedule-timeout-seconds`（新配置）未被认领，收敛 `FAILED / SCHEDULE_TIMEOUT`（新错误码）；
- 注册表只承载本节点持有效租约的 Run。

## 8. 迁移、兼容与混部边界

### 8.1 单机新副本：可兼容

- 默认仍用**内存事件后端**，不强制连 Redis；
- `MEMORY`/`FILE` state-store 与 `LOCAL` Workspace 单机合法；
- 租约列按 §4 追加迁移；
- 租约逻辑统一启用时定义 **DB 短暂不可用的自我隔离**（§5.0）。

### 8.2 唯一模式开关与启动校验

- **只用「事件后端类型」作为唯一开关**，不加额外 feature flag；
- 选 **Redis 后端**启动即校验：分布式 state-store、非 `LOCAL` Workspace、Redis 可用、boot UUID、租约参数满足 §5.0；不满足则 fail-fast；
- 选 **内存后端**维持单机部署兼容：事件仍只在本 JVM，owner/lease 状态机与 Redis 后端一致；
- **后端只在启动时选定，禁止运行期回退**；
- `maxActiveRunsPerUser` 维持**每节点**限制并改配置说明，不被误读为集群全局上限。

### 8.3 旧版与集群版：不可安全混部

旧版不写/不校验 `owner/epoch`。升级边界：**升级窗口暂停创建新 Run + 禁止新旧共处理活动 Run + 发布前结束所有
`RUNNING`/`AWAITING_CONFIRM` Run**，不做旧状态迁移。

### 8.4 优雅停机 drain

「滚动发布不误终结」依赖 drain 约定：**未等活动 Run 结束就杀 owner，按本设计必然收敛为 INSTANCE_LOST**。节点收到停机信号时：

1. 停止认领新 Run（不再参与 `claimCreated` / 接管）；
2. 等待本节点持有的活动 Run 自然结束（或到停机上限），等待期间继续续约、checkpoint 和正常事件输出；
3. 到期仍未结束的 Run 主动按 `STOPPING`/`FAILED` 收敛，而非留给 takeover。

## 9. 技术选项与过度设计判断

合理且必要（本期保留）：

- DB 作为 Run 状态、owner、lease、epoch 唯一事实来源 + 写入 fencing 矩阵；
- 周期扫描失效 RUNNING/STOPPING；AWAITING_CONFIRM 主动无主、确认时原子切 phase + 认领；
- Redis Streams（按 phase 分 key）支撑任意节点实时订阅；Redis Pub/Sub 唤醒 stop、DB `STOPPING` 为持久事实；
- XREAD 空闲超时复核 DB + `RESYNC_REQUIRED` 控制事件；
- owner 本地每 phase 容量上限；Redis 使用 noeviction；
- 内存/Redis 双事件后端兼容无 Redis 单机；新旧版本禁止混部；优雅停机 drain。

本期不做（移入 §12）：

- Redis fence/seal 状态机、跨 epoch 全局严格 `seq`、活动裁剪、终态事件精确投递、集群级精确容量统计、Stream 尾部重建 accumulator。

## 10. 验证要求

- DB fencing：过期节点对 `ai_chat_run` 的写被 owner/epoch/租约校验拒绝；普通写不增 epoch、claim/takeover/confirm 增 epoch；
  接管 CAS 在 A 续约成功后失败；过期 owner 续约被拒。
- 接管：两节点同启同一中断 Run 只一方接管；`scanExpiredOwnedRuns` 接管「启动后宕机」的 RUNNING Run；接管终结只用
  `snapshot_json`、`snapshot_seq` 不抬升、不写 Stream、不重启模型调用。
- 切 phase 原子闭环：确认事务同事务更新 `phase_no`/`agui_run_id`/快照/`snapshot_seq=0`/owner/epoch/lease；新 Stream 第一条 `1-0`；
  SSE id `{chatRunId}:{phaseNo}:{seq}` 不重复。
- 事件顺序：DB 状态提交后才尽力写边界事件；Stream 写失败时 bootstrap 能从 DB 合成。
- AWAITING_CONFIRM：不进失效扫描；进入后 phase Stream 设 phase-closed TTL；释放租约后注册表摘除。
- 订阅恢复：当前 phase 有限尾部消费完后，XREAD 空闲超时复核 `(status, phaseNo, leaseEpoch)`，变化即 resync。
- 强制 resync：缺边界事件、INSTANCE_LOST 时页面持有未检查点尾部增量，两分支都触发恢复；意外断线同样检查。
- 停止：非执行节点 stop 经 Pub/Sub 唤醒或 DB 复核，执行节点 interrupt 收敛 `STOPPED`。
- 容量：owner 本地每 phase 上限生效，超限收敛 `FAILED / ERROR`；满载节点跳过 `CREATED`；全节点满载超时收敛
  `SCHEDULE_TIMEOUT`。
- Redis 故障：写持续失败保存 DB 快照并终结、不切内存；活动 key 在独立 noeviction 实例不静默淘汰；终态整组设 TTL。
- 单机：默认内存后端、不连 Redis、LOCAL Workspace 合法、租约列迁移通过；Redis 后端启动校验缺失 fail-fast。
- 优雅停机 drain：节点停机时停止认领，等待期间继续续约和执行，等活动 Run 收敛后退出，健康旧节点 Run 不被新节点误判遗失。

## 11. 部署声明

改造完成前，部署声明仍只能写为「单实例浏览器断线续跑与恢复」，不能写成「多实例高可用」。
完成第 5、6 节改造并通过第 10 节验证后，方可声明「多实例同时运行（crash-stop 故障模型）」。

## 12. 演进项（本期不做）

仅当业务不能接受 §0/§2.2 的限制时，才引入以下增强：

1. **严格 Redis fencing**：每 Run `fence_epoch` + `OPEN`/`SEALED` + Lua 原子「校验 fence + 分配 seq + XADD / 边界 seal」。
2. **跨 epoch 全局严格连续序号**：接管时 `next_seq = max(snapshot_seq, stream tail) + 1` 校准 + 边界序号预留协议。
3. **活动期间原子裁剪**：同一 Lua 原子「校验 checkpoint 水位 + 更新 `trimmed_through_seq` + XTRIM」+ 订阅端复核。
4. **从 Stream 尾部重建 accumulator**：Stream entry 携带可折叠 `snapshotDelta`，实现确定性 reducer。
5. **终态事件精确投递**：发布标记 / 事务 outbox，保证正式边界事件绝不缺失。
6. **集群级精确容量统计**。

> 注：AgentState / Workspace 的分布式一致性与 generation 隔离**不属于本设计范围**——它是更高等级的分布式执行一致性问题，
> 应由对应组件自身承担，而非塞进 `chat.runtime` 的集群化。
