# 对话后台续跑与断线恢复设计

> 目标：解决用户在 LLM 输出过程中切换会话、关闭页面或网络断开后，生成被取消、助手回复丢失的问题。
>
> 当前范围：单实例、内存事件缓冲。浏览器连接断开后后台继续执行；服务进程重启后不继续原 LLM/工具调用，而是将遗留 Run 收敛为失败并保存已有快照。

## 1. 源码结论

改造前的 `ChatServiceImpl` 把三个不同生命周期绑在了一起：

1. HTTP SSE 连接；
2. Agent Flux 订阅；
3. 助手消息最终落库。

原实现存在以下确定问题：

- `emitter.onCompletion/onTimeout` 会执行 `Disposable.dispose()`。浏览器切换会话、关闭页面或网络断开后，Spring 完成异步请求，进而取消 Agent Flux。
- 助手文本仅累积在请求内的 `StringBuilder`，只在正常完成路径落库。连接中断或发送事件失败时，已经生成的内容可能全部丢失。
- 前端的“切换页面”和“用户主动停止”都调用 `abortChat()`，后端无法区分“只断开订阅”和“停止业务运行”。
- 前端同时使用 TDesign 内置 AG-UI adapter 和第二个手工 adapter，存在重复解析和状态覆盖。

因此，用户的判断是正确的：原实现确实可能在前端断开后终止生成，并丢失尚未落库的助手输出。修复不能只增加 SSE 重连；必须同时完成后台续跑、事件恢复和最终落库。

## 2. 目标与边界

### 2.1 必须保证

- SSE 断开只移除当前订阅者，不取消 Agent Flux。
- 同一会话同一时刻只允许一个非终态 Run。
- 首次发送使用 `clientRequestId` 幂等；网络重试不能重复保存用户消息或重复执行 Agent。
- 助手消息、Run 终态和会话最后消息时间在同一事务中提交。
- 数据库提交成功后才发送业务终态事件。
- `STOPPED` 或 `FAILED` 时，只要已经生成文本或工具结果，也保存部分助手消息。
- 页面刷新、切换会话后切回、网络重连都能从 Run 快照恢复当前展示状态。
- HITL 等待确认、确认续跑和停止都以具体 `runId` 为目标。

### 2.2 明确不保证

- 不保证 JVM 崩溃后从中间 token 继续同一次模型调用。
- 不保证工具已经产生的外部副作用可以回滚。
- 当前不支持多实例之间转移正在运行的 Agent Flux。
- 当前不实现 Redis Stream、执行节点租约、跨节点停止命令或事务 outbox。
- 当前前端不启用严格增量游标恢复，统一使用 bootstrap 重建，原因见第 8 节。

## 3. 最小组件与职责

实现只保留四个核心职责：

| 组件 | 类型 | 职责 |
| :--- | :--- | :--- |
| `ChatServiceImpl` | `@Service` | 唯一对话入口；创建/查询 Run，建立 SSE 订阅，处理确认与停止编排 |
| `ChatRunServiceImpl` | `@Service` | Run 单表 Service；负责事务、状态迁移、所有权校验和最终落库 |
| `ChatRunManager` | `@Component` | 持有 Agent Flux，处理事件、快照、后台生命周期、停止和启动恢复 |
| `ChatRunEventStore` | `@Component` | 为每个 Run 分配序号，提供有界内存缓冲、回放和实时订阅 |

这些类都属于当前模块自有且只有一个实现，由既有组件扫描发现：

- 普通业务实现使用 `@Service` / `@Component`，由模块已有组件扫描发现。
- 当前没有第二种事件存储实现，不为未来 Redis 预留接口和条件 Bean。
- 不在业务 Service 上添加 `@ConditionalOnProperty`。
- 不创建 `ChatServiceImpl` / `ResumableChatServiceImpl` 两套实现。
- 不增加只为该功能服务的 `ExceptionAdvice`；继续使用项目统一业务异常和错误码。

基础框架的 `SseEmitterManager` 已审计。它适合按 clientId 管理连接、心跳和广播，但不提供 Run 级严格序号、有界历史窗口、按游标回放、快照检查点以及“回放到实时”的原子切换，不能直接替代 `ChatRunEventStore`。为它再包一层仍需实现同样的 Run 存储语义，反而增加耦合，因此本功能仅复用 Spring `SseEmitter`。

## 4. Session 与 Run 的事实边界

`ChatSessionEntity` 已经包含：

- `tenantId`
- `userId`
- `appId`
- 会话 ID

Run 不复制 `userId` 和 `appId`，而是通过 `session_id` 使用 `ChatSession` 这一既有事实来源。`la_ai_chat_run.tenant_id` 仅作为租户插件要求的隔离列，由框架自动填充和过滤，不由业务代码手工设置，也不作为用户、应用或会话归属的事实来源。

标识含义：

- `sessionId`：多轮会话。
- `runId`：一次用户消息触发的逻辑回合，也是查询、续看、确认和停止的稳定标识。
- `aguiRunId`：一个 AG-UI 执行阶段。首次执行和每次 HITL 确认后的阶段各自使用新的值。
- `clientRequestId`：前端为一次“发送消息”生成的幂等键。
- `phaseNo`：HITL 阶段号，同时用于确认操作的自然幂等控制。

### 4.1 状态机

```text
CREATED ──认领──► RUNNING
   │               ├──正常结束────────────► COMPLETED
   │               ├──需要人工确认────────► AWAITING_CONFIRM
   │               │                           ├──确认──► RUNNING（phaseNo + 1）
   │               │                           ├──停止──► STOPPED
   │               │                           └──超时──► STOPPED
   │               ├──停止请求────────────► STOPPING ──► STOPPED
   │               └──执行/落库失败────────► FAILED
   ├──停止─────────────────────────────────► STOPPED
   └──启动失败─────────────────────────────► FAILED
```

终态为 `COMPLETED`、`STOPPED`、`FAILED`。终态不可逆。

## 5. 数据库设计

只新增一张表 `la_ai_chat_run`，不增加确认命令账本。

| 字段 | 用途 |
| :--- | :--- |
| `id` | 稳定业务 Run ID |
| `tenant_id` | 由 MyBatis 租户插件在插入时自动处理 |
| `session_id` | 关联 `ChatSession`，身份、应用和租户均以 Session 为准 |
| `client_request_id` / `request_hash` | 首次消息请求幂等及冲突检测 |
| `user_message_id` / `assistant_message_id` | 关联已落库消息 |
| `status` / `finish_reason` | 状态和业务结束原因 |
| `phase_no` / `agui_run_id` | HITL 阶段及当前 AG-UI 阶段 |
| `await_confirm_deadline_at` | 确认超时扫描 |
| `snapshot_seq` / `snapshot_json` | 已持久化展示快照及其事件水位 |
| `error_code` / `error_message` | 失败原因，错误消息限制长度且不记录凭据 |
| `started_at` / `finished_at` | 生命周期时间 |
| `created_*` / `updated_*` | `BaseEntity` 审计字段 |

索引保持最少必要集合：

- 唯一索引 `(session_id, client_request_id)`：保证发送请求幂等。
- 普通索引 `(tenant_id, session_id, created_at)`：会话 Run 查询。
- 普通索引 `(status, updated_at)`：启动和维护扫描。

同会话单活不使用 `active_session_id` 冗余列。创建 Run、确认、终结和删除会话都先锁定 Session 行；同一会话的创建事务因此串行，查询到非终态 Run 时拒绝新建。

`snapshot_json` 使用 `longtext`，保存展示恢复所需的文本、推理、工具结果、内容块开闭状态和脱敏后的待确认工具 ID/名称。为避免 Run 快照能保存而最终消息因旧 `text` 容量失败，另以追加 changeSet 将 `ai_chat_message.content/tool_call` 扩为 `longtext`。它不保存完整待确认工具参数；完整 `ToolUseBlock` 仍由当前 Agent 状态持有。因为本方案不承诺进程重启后继续原 Agent，所以没有必要把可能敏感的完整工具输入再复制到 Run 表。

旧的 `ai_chat_session.pending_confirm` 不再是新流程的事实来源，也不再读写；保留旧列只属于既有数据库兼容范围。

## 6. 后端执行流程

### 6.1 创建与认领

`POST /sessions/{sessionId}/chat` 的事务流程：

1. 按当前用户加载并 `FOR UPDATE` 锁定 Session；MyBatis 租户插件自动追加租户条件。
2. 按 `(sessionId, clientRequestId)` 查询已有 Run：
   - 请求摘要相同：返回原 Run，不重复写消息。
   - 请求摘要不同：返回 `CHAT_RUN_REQUEST_CONFLICT`。
3. 仅在确需新建 Run 时，校验 Session 绑定应用仍可用。
4. 查询当前会话是否已有非终态 Run；有则返回 `CHAT_RUN_ALREADY_ACTIVE`。
5. 在同一事务内插入 `CREATED` Run、用户消息，绑定附件并更新 Session 的 `last_message_at`。
6. 事务提交后，`ChatRunManager` 通过 `CREATED -> RUNNING` 条件更新认领 Run；只有认领成功的一方订阅 Agent Flux。

当前附件模型没有顺序字段，后台按消息重新查询附件，因此附件在本领域中按集合处理；请求摘要对附件 ID 去重、排序后再计算，后台查询按附件 ID 固定排序，避免同一附件集合因客户端排列、重复项或数据库返回顺序产生伪冲突。这里不为未定义的附件顺序新增数据库字段。

如果进程在创建事务提交后、认领前退出，启动扫描可以安全启动仍为 `CREATED` 的 Run。已进入 `RUNNING` 后不自动重新执行，避免重复模型调用和工具副作用。

### 6.2 Agent 与 SSE 解耦

`ChatRunManager.Execution` 持有唯一 Agent `Disposable`。`ChatServiceImpl` 创建的 SSE 连接只持有 `ChatRunEventStore.Subscription`。

SSE 的 completion、timeout 和 error 回调只执行：

```text
subscription.close()
```

不会调用 Agent `Disposable.dispose()`。所以一个订阅者断开不影响 Run，也不影响其他订阅者。

Agent 每产生一条事件时：

1. `AguiEventMapper` 映射为 AG-UI 事件；
2. `RunSnapshot.Accumulator` 更新规范展示状态；
3. `ChatRunEventStore` 追加事件并分配严格递增的 `seq`；
4. 已连接订阅者异步消费事件；慢订阅者只断开自身；
5. 按事件数或时间间隔写入快照检查点。

### 6.3 最终落库

正常完成、停止和失败竞争同一个终结门闩。终结流程为：

1. 关闭仍打开的文本/推理内容块并取得最终快照。
2. 开启 `REQUIRES_NEW` 事务，依次锁定 Session 和 Run。
3. 若 Run 已终态，读取已有结果，不重复插入助手消息。
4. 若数据库中的最新状态已是 `STOPPING`，即使 Agent 完成/异常回调同时到达，也优先收敛为 `STOPPED`。
5. `COMPLETED` 总是保存助手消息；`STOPPED` / `FAILED` 在已有文本或工具结果时保存部分助手消息。
6. 同一事务更新 Run 终态、快照、助手消息 ID、错误信息和 Session `last_message_at`。
7. 事务提交后，才追加 `RUN_FINISHED` 或 `RUN_ERROR`，再记录包含终态事件的 `snapshot_seq`。

数据库或终态事件记录短暂失败时，管理器以最大 30 秒间隔继续重试，直到成功或进程停止。若业务终态事务已提交而后续事件记录失败，重试会读取已提交 Run 和快照，不重复写助手消息，也不会用旧尝试覆盖真实终态。

### 6.4 HITL

收到 `REQUIRE_USER_CONFIRM` 时：

1. 累积器保存脱敏的工具 ID/名称和当前展示快照。
2. 不取消 Agent Flux，等待当前 Agent 调用自然结束，使 AgentScope 完成 ASKING 状态保存。
3. Run 条件更新为 `AWAITING_CONFIRM`，写入确认截止时间。
4. 事件流发送 `RUN_FINISHED(interrupt)`，结束当前 AG-UI 阶段，但逻辑 Run 不终结，Agent 实例仍保留。

确认请求必须提交当前 `phaseNo` 和全部工具决策：

- 事务锁定 Session 和 Run；
- 校验 Run 正处于相同阶段的 `AWAITING_CONFIRM`；
- 校验决策 ID 与快照中的待确认 ID 完全一致且不重复；
- 将状态改为 `RUNNING`、`phaseNo + 1`，生成新的 `aguiRunId`；
- 事务提交后，管理器从 Agent 状态读取完整 `ToolUseBlock` 并继续同一逻辑 Run。

如果相同旧 `phaseNo` 再次提交，而 Run 已进入更高阶段，则只返回当前 Run 并重新挂接，不再次应用确认。这里使用阶段号保证“状态迁移至多一次”，不额外建立命令流水表。

### 6.5 停止

停止是显式业务操作：

```http
POST /v1/ai/sessions/{sessionId}/runs/{runId}/stop
```

流程为：

1. 条件更新 `CREATED/RUNNING/AWAITING_CONFIRM -> STOPPING`。
2. 有运行中 Agent 时先调用协作式 `interrupt(userId, sessionId)`。
3. 宽限期内未结束时才 dispose Flux。
4. 最终以 `STOPPED` 落库，并保存已有部分输出。

切换会话、关闭页面和普通网络断开不调用停止接口。

### 6.6 进程重启

当前事件存储和 Agent 执行都在单实例内存中，因此启动恢复必须诚实处理：

- `CREATED`：尚未认领，可从已落库用户消息和附件首次启动。
- `RUNNING`：标记为 `FAILED / INSTANCE_LOST`，用最后快照保存部分助手输出。
- `AWAITING_CONFIRM`：仅当 AgentScope state store 为持久化实现且确认上下文可在重启后重新校验时保留；否则收敛为 `FAILED / INSTANCE_LOST`。
- `STOPPING`：收敛为 `STOPPED / USER_STOP`，避免用户已提交的停止意图在重启后变成执行失败。
- 确认超时：运行期间扫描 `AWAITING_CONFIRM`，收敛为 `STOPPED / CONFIRM_TIMEOUT`。

不把 Agent 状态存储可持久化等同于“模型流可从 token 中间继续”。对 `AWAITING_CONFIRM` 的保留也不等于承认任意版本、任意工具集合都能安全续跑；实现必须保证恢复后仍能校验 Agent、工具和权限上下文一致，否则应按失败边界处理。

## 7. SSE 事件与恢复

### 7.1 正式事件

事件存储保存映射后的 AG-UI JSON，并在顶层增加：

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

SSE `id` 为 `{chatRunId}:{seq}`。`seq` 同时放在 JSON 顶层，因为当前 TDesign 上层回调不能可靠取得 SSE parser 的 `id:` 字段。

普通续看使用 `afterSeq` 或 `Last-Event-ID`，服务端只发送 `seq > afterSeq`。回放列表与实时订阅者的注册在同一缓冲锁内完成，避免两者切换时漏事件。

内存缓冲同时限制事件数、总字节数和单订阅者队列。淘汰旧事件前必须先同步持久化覆盖这些事件的规范快照。普通游标早于保留窗口时返回 `CHAT_RUN_CURSOR_EXPIRED`；Run 缓冲已经过终态 TTL 清理时，普通续看返回 `CHAT_RUN_EVENTS_EXPIRED`。

### 7.2 Bootstrap 恢复

新页面或重建后的 ChatEngine 没有旧 adapter 状态，不能直接从某个文本增量继续。`bootstrap=true` 会：

1. 在 Execution 串行边界内取得最新累积快照和事件高水位 `H`；
2. 生成一组不写回正式事件存储的合成 AG-UI 事件；
3. 重建推理、工具、文本及其开闭状态；
4. 若 Run 等待确认，补发 interrupt；若已终态，补发对应终态；
5. 再订阅所有 `seq > H` 的实时事件。

生成过程中追加的新事件会在第 5 步作为回放事件送达，因此 bootstrap 与实时流之间没有空洞。bootstrap 事件不占用正式 `seq`，重复 bootstrap 只重建临时气泡，不污染事件窗口。

终态缓冲 TTL 过期后，bootstrap 仍可从数据库最终快照和历史消息恢复；它不依赖旧内存事件仍存在。

## 8. “TDesign 缺少严格游标提交钩子”的含义

当前锁定版本为 `tdesign-web-components 1.3.1-alpha.13`。其 AG-UI 处理顺序是：

```text
内置 AGUIAdapter 解析事件
        ↓
业务 onMessage 回调
        ↓
processMessageResult 写入 messageStore
```

业务代码在 `onMessage` 中看到 `seq` 时，消息还没有写入 UI store。如果此时把该序号记录为“已消费”，随后页面崩溃、store 更新失败或连接异常，重连从该序号之后开始，就会永久跳过一个实际上没有渲染成功的事件。

反过来，当前 API 又没有“`processMessageResult` 已成功提交”后的回调。因此业务层无法维护严格的 `lastAppliedSeq`。这里所谓“缺少严格游标提交钩子”，就是缺少一个能确认“这个事件已经真正写入前端状态，现可安全推进游标”的时点。

所以当前前端采用保守策略：

- 后端保留标准增量 `afterSeq` 能力，供具备严格游标的客户端使用；
- 当前 TDesign 页面所有恢复都使用 `afterSeq=0&bootstrap=true`；
- 每次恢复先删除未完成的临时助手气泡，再完整重建一次；
- 不维护看似精确、实际可能越过未应用事件的 `lastSeenSeq`。

只有 TDesign 将“事件应用成功”回调暴露出来，或项目在 ChatEngine 内部增加 `onMessageApplied(seq)` 后，当前页面才适合启用增量恢复。

## 9. HTTP API

| 方法 | 路径 | 用途 |
| :--- | :--- | :--- |
| `POST` | `/v1/ai/sessions/{id}/chat` | 使用 `clientRequestId` 创建或幂等挂接 Run，并返回 SSE |
| `GET` | `/v1/ai/sessions/{id}/runs/active` | 查询会话当前非终态 Run；没有时返回 200 空响应 |
| `GET` | `/v1/ai/sessions/{id}/runs/{runId}` | 查询状态、快照摘要、阶段和待确认工具 |
| `GET` | `/v1/ai/sessions/{id}/runs/{runId}/events` | 使用 `afterSeq` / `Last-Event-ID` 续看，可选 bootstrap |
| `POST` | `/v1/ai/sessions/{id}/runs/{runId}/confirm` | 提交 `phaseNo + decisions`，以 SSE 续接下一阶段 |
| `POST` | `/v1/ai/sessions/{id}/runs/{runId}/stop` | 显式停止一个 Run |

所有入口先按当前用户加载 Session，再按 `sessionId + runId` 查询 Run。Controller 不调用 Mapper，也不单独增加异常 Advice。

## 10. 前端流程

### 10.1 首次发送

- 每次用户主动发送生成新的 `clientRequestId`。
- 当前 TDesign 的 `sendUserMessage()` 只触发 `sendRequest()`，不会等待该请求完成；前端因此先以 `sendRequest=false` 创建消息，再显式 `await sendRequest()`，避免过早进入恢复判断。
- TDesign `onRequest` 将本次固定的 `clientRequestId` 和附件 ID 快照随内容一起发送；切换会话不会把旧请求改写成新会话的附件或幂等键。
- 收到任一事件后，从顶层 `chatRunId` 记录活动 Run。
- 如果连接在第一个事件到达前就失败，前端查询 `/runs/active` 发现服务端是否已经创建 Run，避免再次用新请求键重复发送。

### 10.2 切换、刷新与断网

- 切换会话、切换应用和组件卸载只执行本地 `abortChat()`，不调用后端 stop。
- 加载会话时先读取 active Run，再读取历史消息；若 Run 恰好在两次查询之间终结，后一次历史查询能够看到同事务落库的最终消息。
- 存在 active Run 时，先查询 Run 详情，移除旧的未完成助手气泡，创建稳定的 `run-{runId}` 空气泡，再发 GET bootstrap 请求。
- 意外断线后指数退避重试，最大间隔 10 秒；每次先查 Run 状态。
- Run 已终态时清理临时状态并重新加载持久化历史。
- `AWAITING_CONFIRM` 时从 Run 返回的脱敏待确认工具重建确认卡片。

### 10.3 主动停止与确认

- 停止按钮先中断本地 SSE，再查询具体 active Run 并调用 stop API；这样即使点击发生在首个事件到达前，也不会只断开页面而漏停后台 Run。随后继续查询，直到看到终态并刷新历史。
- 确认时提交当前 `phaseNo` 和全部工具决策；响应固定使用 bootstrap 重建当前快照，再接新阶段实时事件。
- 只使用 TDesign 内置 AG-UI adapter；业务 `onMessage` 只读取 Run 元数据，并返回内置 adapter 已生成的结果。

## 11. 多租户处理

请求线程中的租户隔离完全由现有框架处理：

- `TenantContextInterceptor` 建立租户上下文；
- MyBatis 租户插件为 Run、Session、Message、Attachment 查询自动追加 `tenant_id` 条件，并在插入时填充租户列；
- 业务代码不调用 `entity.setTenantId(...)`；
- 业务 Wrapper 不重复拼 `.eq(Entity::getTenantId, tenantId)`；
- 不使用 `@InterceptorIgnore(tenantLine = "true")` 绕过插件。

Agent 回调发生在请求结束之后，必须恢复线程租户上下文。管理器使用已经校验过的 `ChatSessionEntity.tenantId` 设置 `TenantContextHolder`，执行完成后清理并恢复原上下文。这是异步上下文传播，不是手工数据库租户过滤。

启动和维护任务没有登录请求上下文，会先执行受控的系统级 Run 状态扫描，再按全局唯一 Session ID 加载真实 Session；从该 Session 恢复租户上下文后，才执行具体 Run 的数据库、Agent 和审计操作。

## 12. 配置

配置统一位于 `lambda.fusion.ai.chat.run`，没有 `enabled`、`buffer` 或 Redis 占位配置：

```yaml
lambda:
  fusion:
    ai:
      chat:
        run:
          connection-timeout-seconds: 300
          max-run-duration-seconds: 1800
          await-confirm-timeout-seconds: 86400
          stop-grace-seconds: 10
          terminal-ttl-seconds: 600
          max-events: 4096
          max-bytes: 8388608
          max-active-runs: 200
          max-active-runs-per-user: 4
          subscriber-queue-size: 256
          snapshot-every-events: 100
          snapshot-interval-seconds: 2
```

这些配置只控制超时和资源上限，不改变 Bean 拓扑。默认值已定义在
`AiProperties.Chat.Run`，启动模块无需逐项重复配置；只有部署确需调整时才覆盖对应项。

## 13. 失败语义与安全

- Agent 启动失败：`FAILED / START_FAILED`。
- 实例容量超限：`FAILED / RUN_CAPACITY_EXCEEDED`。
- 运行超时或一般异常：`FAILED / ERROR`，保存已有部分输出。
- 进程重启遗留执行：`FAILED / INSTANCE_LOST`。
- 用户停止：`STOPPED / USER_STOP`。
- HITL 超时：`STOPPED / CONFIRM_TIMEOUT`。
- 慢 SSE 订阅者：只断开该订阅者，Run 继续。
- 快照中的工具参数和结果按常见 secret/token/password 字段脱敏；日志不输出提示词、完整工具输入或凭据。
- 删除 Session 前检查是否存在非终态 Run；存在时拒绝删除。

## 14. 工程审计结论

本轮最终采用的约束：

1. 只有一个 `ChatServiceImpl`，不保留 legacy/resumable 双实现。
2. 业务 Service 保持 `@Service`，不在 Service 上使用条件注解。
3. 当前唯一事件存储使用 `@Component`，不在 `AiConfigure` 增加无实际替换需求的条件 Bean。
4. 不增加 `ChatRunExceptionAdvice`，复用统一异常机制。
5. 不手工设置实体租户字段，不重复拼租户 Wrapper，不使用租户忽略注解。
6. Run 复用 `ChatSession` 的用户和应用信息；`tenant_id` 只保留框架隔离列，由租户插件自动处理。
7. 不增加确认命令流水表；`phaseNo` 和行锁足以保证状态迁移不重复。
8. 不增加 `active_session_id`、`last_seq` 等与现有状态/快照重复的字段。
9. 不预埋未实现的 Redis、多节点 owner 或 feature flag。
10. 助手消息写入复用现有 `ChatMessageService`，不在 Run Service 重复构造消息持久化逻辑。

## 15. 验证要求与当前结果

必须覆盖：

- 事件严格递增、回放后无缝接实时流；
- 缓冲淘汰前先写快照；
- bootstrap 能重建文本、推理、工具和 HITL；
- 工具参数/结果脱敏且工具顺序稳定；
- 重复终态追加不产生第二条终态事件；
- 浏览器断开不 dispose Agent；
- 相同 `clientRequestId` 不重复写用户消息；
- 最终消息和 Run 终态事务幂等；
- 切换会话后切回可恢复；
- 主动停止保存部分输出。

当前已完成的验证：

- `mvn -pl lambda-fusion-ai -DskipTests compile` 通过，AI 模块 Spotless 全量检查通过；父 POM 当前配置将 SpotBugs 标记为 skip。
- `mvn -pl lambda-fusion-ai test` 通过：共 98 个测试，0 failure / 0 error / 0 skipped；覆盖 Run 事件存储、bootstrap、快照、停止竞态与终态游标删除竞态。
- Liquibase changelog XML 解析通过，根仓库与前端仓库 `git diff --check` 通过。
- 前端本次修改的 `chat.ts`、`chat-panel.vue` ESLint 通过。
- 前端 `pnpm --filter @vben/web-tdesign typecheck` 通过。

其中“浏览器断开不 dispose Agent”、请求幂等、终态事务、切换恢复和停止部分输出属于联调验收项；
当前自动化测试主要覆盖事件窗口、bootstrap 与快照，不把尚未完成的浏览器/数据库集成场景写成已自动验证。

当前实现的部署声明只能写为“单实例浏览器断线续跑与恢复”，不能写成“多实例高可用”或“服务重启后继续生成”。
