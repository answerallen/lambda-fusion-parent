package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.chat.runtime.event.memory.MemoryChatRunEventBackend;
import com.lambda.fusion.ai.chat.runtime.event.redis.RedisChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.event.spi.ChatRunEventBackend;
import io.agentscope.core.agui.event.AguiEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 对话执行事件门面：对写路径与协调器暴露统一入口，把事件存储委派给可插拔后端
 * （内存 / Redis Streams），并统一编排订阅分发。后端在启动时按 {@code chat.run.event-backend} 唯一选定，
 * 运行期不回退；本类不再是 Spring Bean，由 {@code AiConfigure} 以后端 + 门面方式装配。
 *
 * <p>内存后端沿用本地缓冲推送订阅；Redis 后端任意节点均可订阅——历史经后端 {@code readAfter} 回放、
 * 实时经有界轮询取数，空转时由 {@code recheck} 复核 DB 的 {@code (status, phaseNo, leaseEpoch)}，
 * 变化即触发 RESYNC_REQUIRED 后断开。
 *
 * @author Jin
 */
public class ChatRunEventStore {

    private final ChatRunEventBackend backend;
    private final ExecutorService senderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建事件门面。
     *
     * @param backend 事件后端
     */
    public ChatRunEventStore(ChatRunEventBackend backend) {
        this.backend = backend;
    }

    /**
     * 初始化运行的事件序号。
     *
     * @param runId 运行标识
     * @param latestSeq 已持久化的最新事件序号；{@code null} 按 {@code 0} 处理
     */
    public void initialize(String runId, Long latestSeq) {
        backend.initialize(runId, latestSeq);
    }

    /**
     * 批量追加同一 Agent 事件映射出的 AG-UI 事件。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents AG-UI 事件列表
     * @return 缓冲区超过容量限制时返回 {@code true}
     */
    public boolean appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents) {
        return backend.appendAll(runId, aguiRunId, aguiEvents);
    }

    /**
     * 在缓冲实例锁内原子地完成「暂存事件 → 数据库迁移 → 按成败发布或丢弃」。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents 待暂存的 AG-UI 事件（待确认中断事件）
     * @param dbAction 数据库迁移；返回 {@code true} 表示已提交并应发布，{@code false} 表示并发落败应丢弃
     * @return {@code dbAction} 的结果
     */
    public boolean runExclusive(String runId, String aguiRunId, List<AguiEvent> aguiEvents, BooleanSupplier dbAction) {
        return backend.runExclusive(runId, aguiRunId, aguiEvents, dbAction);
    }

    /**
     * 追加终态事件；终态已存在时返回原事件。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiJson 终态事件 JSON
     * @return 新增或已存在的终态事件
     */
    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson) {
        return backend.appendTerminalIfAbsent(runId, aguiRunId, aguiJson);
    }

    /**
     * 删除已由持久化快照覆盖的超量事件（仅内存后端生效；Redis 后端由 EXPIRE 接管）。
     *
     * @param runId 运行标识
     * @param snapshotSeq 快照覆盖的最大事件序号
     */
    public void compact(String runId, long snapshotSeq) {
        backend.compact(runId, snapshotSeq);
    }

    /**
     * 订阅指定游标之后的事件。内存后端要求执行节点本地缓冲在场（否则抛事件过期）；Redis 后端任意节点可订阅。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 订阅句柄
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return subscribe(runId, afterSeq, consumer, failureConsumer, null);
    }

    /**
     * 订阅并附带 DB 复核回调（仅 Redis 后端使用）：订阅空转时回调复核运行状态/阶段/epoch，
     * 返回 {@code false} 表示运行状态或所有权已变化，订阅端触发 RESYNC_REQUIRED 后断开。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @param recheck DB 复核回调；{@code null} 表示订阅到终态事件即止（内存后端忽略）
     * @return 订阅句柄
     */
    public ChatRunEventSubscription subscribe(
            String runId,
            long afterSeq,
            Consumer<ChatRunEvent> consumer,
            Consumer<Throwable> failureConsumer,
            BooleanSupplier recheck) {
        if (backend instanceof MemoryChatRunEventBackend memory) {
            return memory.subscribe(runId, afterSeq, consumer, failureConsumer);
        }
        return new RedisChatRunEventSubscription(
                runId, afterSeq, backend, consumer, failureConsumer, recheck, senderExecutor);
    }

    /**
     * 查询运行的最新事件序号。
     *
     * @param runId 运行标识
     * @param fallback 缓冲区不存在时使用的序号
     * @return 最新事件序号
     */
    public long latestSeq(String runId, Long fallback) {
        return backend.latestSeq(runId, fallback);
    }

    /**
     * 标记终态事件的保留时长。
     *
     * @param runId 运行标识
     * @param retention 终态事件保留时长
     */
    public void markTerminal(String runId, Duration retention) {
        backend.markTerminal(runId, retention);
    }

    /** 删除所有已到期的终态缓冲区（内存后端；Redis 后端为空操作）。 */
    public void purgeExpired() {
        backend.purgeExpired();
    }

    /** 关闭事件订阅并释放发送线程池。 */
    public void shutdown() {
        backend.shutdown();
        senderExecutor.shutdownNow();
    }
}
