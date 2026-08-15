package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.agui.event.AguiEvent;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 对话执行事件存储。
 *
 * <p>按运行标识管理内存事件缓冲区，并提供事件追加、游标查询、订阅和过期清理功能。
 *
 * @author Jin
 */
@Component
public class ChatRunEventStore {

    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Map<String, ChatRunEventBuffer> buffers = new ConcurrentHashMap<>();
    private final ExecutorService senderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建事件存储。
     *
     * @param properties AI 模块配置
     */
    public ChatRunEventStore(AiProperties properties) {
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        this.subscriberQueueSize = properties.getChat().getRun().getSubscriberQueueSize();
    }

    /**
     * 初始化运行的事件序号。
     *
     * @param runId 运行标识
     * @param latestSeq 已持久化的最新事件序号
     */
    public void initialize(String runId, long latestSeq) {
        buffer(runId).initialize(latestSeq);
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
        return buffer(runId).append(aguiEvents, aguiRunId).checkpointRequired();
    }

    /**
     * 在缓冲区实例锁内原子地完成「暂存事件 → 执行数据库迁移 → 按成败发布或丢弃」。
     *
     * <p>把两态操作收敛为单个原子方法，避免在锁外分散调用 stage/publish/discard 而被并发订阅
     * 或终结打断：暂存事件在迁移成功前不进入可见窗口、不推送订阅者；迁移成功才发布，失败则丢弃。
     * 序号在暂存时分配，故快照序号覆盖中断事件，且事实先于信号外发。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents 待暂存的 AG-UI 事件（待确认中断事件）
     * @param dbAction 数据库迁移；返回 {@code true} 表示已提交并应发布，{@code false} 表示并发落败应丢弃
     * @return {@code dbAction} 的结果；发布成功且缓冲区超容量时不影响返回值（容量由后续检查点收敛）
     */
    public boolean runExclusive(
            String runId, String aguiRunId, List<AguiEvent> aguiEvents, java.util.function.BooleanSupplier dbAction) {
        ChatRunEventBuffer buffer = buffer(runId);
        synchronized (buffer) {
            buffer.stage(aguiEvents, aguiRunId);
            boolean committed = dbAction.getAsBoolean();
            if (committed) {
                buffer.publishStaged();
            } else {
                buffer.discardStaged();
            }
            return committed;
        }
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
        return buffer(runId).appendTerminal(aguiJson, aguiRunId);
    }

    /**
     * 删除已由持久化快照覆盖的超量事件。
     *
     * @param runId 运行标识
     * @param snapshotSeq 快照覆盖的最大事件序号
     * @throws IllegalStateException 快照未覆盖需要删除的事件
     */
    public void compact(String runId, long snapshotSeq) {
        ChatRunEventBuffer current = buffers.get(runId);
        if (current != null) {
            current.compact(snapshotSeq);
        }
    }

    /**
     * 订阅指定游标之后的事件。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 订阅句柄
     * @throws AiBusinessException 运行事件不存在或游标无效
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(afterSeq, consumer, failureConsumer);
    }

    /**
     * 查询运行的最新事件序号。
     *
     * @param runId 运行标识
     * @param fallback 缓冲区不存在时使用的序号
     * @return 最新事件序号
     */
    public long latestSeq(String runId, Long fallback) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        return buffer == null ? (fallback == null ? 0L : fallback) : buffer.latestSeq();
    }

    /**
     * 标记终态缓冲区的过期时间。
     *
     * @param runId 运行标识
     * @param retention 终态事件保留时长
     */
    public void markTerminal(String runId, Duration retention) {
        buffer(runId).markExpiresAt(System.currentTimeMillis() + retention.toMillis());
    }

    private void clear(String runId) {
        ChatRunEventBuffer removed = buffers.remove(runId);
        if (removed != null) {
            removed.clear();
        }
    }

    private void clear(String runId, ChatRunEventBuffer identity) {
        if (buffers.remove(runId, identity)) {
            identity.clear();
        }
    }

    /** 删除所有已到期的终态缓冲区。 */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        buffers.forEach((runId, buffer) -> {
            if (buffer.expired(now)) {
                expired.add(runId);
            }
        });
        expired.forEach(runId -> clear(runId, buffers.get(runId)));
    }

    /** 关闭事件订阅并释放发送线程池。 */
    @PreDestroy
    public void shutdown() {
        List.copyOf(buffers.keySet()).forEach(this::clear);
        senderExecutor.shutdownNow();
    }

    private ChatRunEventBuffer buffer(String runId) {
        return buffers.computeIfAbsent(
                runId, id -> new ChatRunEventBuffer(id, maxEvents, maxBytes, subscriberQueueSize, senderExecutor));
    }
}
