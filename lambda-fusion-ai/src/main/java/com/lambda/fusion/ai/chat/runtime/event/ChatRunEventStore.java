package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.agui.event.AguiEvent;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 当前 JVM 的实时事件广播器。事件窗口只服务本地恢复衔接，不是持久化事件日志。
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
    private final ScheduledExecutorService expiryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-run-event-expiry");
        thread.setDaemon(true);
        return thread;
    });

    public ChatRunEventStore(AiProperties properties) {
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        this.subscriberQueueSize = properties.getChat().getRun().getSubscriberQueueSize();
    }

    /** 为当前节点刚创建的 Run 建立空缓冲，使浏览器可在首个 Agent 事件前完成订阅。 */
    public void registerLocalRun(String runId) {
        buffer(runId);
    }

    /**
     * 追加一批标准化 AG-UI 事件到运行缓冲并发布给本地订阅者。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiEvents 标准化 AG-UI 事件批次
     */
    public void appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents) {
        buffer(runId).append(aguiEvents, aguiRunId);
    }

    /**
     * 幂等补写终态事件：已有终态时直接返回既有事件，避免重复发布。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param aguiJson 已编码的终态 AG-UI 事件 JSON
     * @return 写入或已存在的终态事件
     */
    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson) {
        return buffer(runId).appendTerminal(aguiJson, aguiRunId);
    }

    /**
     * 从指定游标订阅运行事件，先回放缓冲窗口内的事件再消费实时事件。
     *
     * @param runId 运行标识
     * @param cursor 起始游标（不含），从该游标之后开始回放
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败或消费过慢时的失败消费者
     * @return 事件订阅句柄
     * @throws AiBusinessException 本节点没有该运行的事件缓冲（过期或不在本节点）时抛出
     */
    public ChatRunEventSubscription subscribe(
            String runId, long cursor, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(cursor, consumer, failureConsumer);
    }

    /**
     * 查询运行缓冲的最新游标。
     *
     * @param runId 运行标识
     * @return 最新游标；本节点没有该运行缓冲时返回 {@code 0}
     */
    public long latestCursor(String runId) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        return buffer == null ? 0L : buffer.latestCursor();
    }

    /**
     * 判断本节点是否持有该运行的事件缓冲。
     *
     * @param runId 运行标识
     * @return 存在缓冲返回 {@code true}
     */
    public boolean contains(String runId) {
        return buffers.containsKey(runId);
    }

    /**
     * 标记运行已到达终态：设置缓冲过期时间并调度到期清理，
     * 到期后按缓冲身份比对再删除，避免误删终态后新开的缓冲。
     *
     * @param runId 运行标识
     * @param retention 终态后的本地保留时长
     */
    public void markTerminal(String runId, Duration retention) {
        ChatRunEventBuffer identity = buffer(runId);
        long delayMillis = Math.max(0L, retention.toMillis());
        identity.markExpiresAt(System.currentTimeMillis() + delayMillis);
        expiryExecutor.schedule(
                () -> {
                    if (identity.expired(System.currentTimeMillis())) {
                        clear(runId, identity);
                    }
                },
                delayMillis,
                TimeUnit.MILLISECONDS);
    }

    /** 停机清理：关闭全部运行缓冲（断开所有订阅者）并关闭内部线程池。 */
    @PreDestroy
    public void shutdown() {
        List.copyOf(buffers.keySet()).forEach(this::clear);
        expiryExecutor.shutdownNow();
        senderExecutor.shutdownNow();
    }

    /** 移除并清空指定运行的缓冲。 */
    private void clear(String runId) {
        ChatRunEventBuffer removed = buffers.remove(runId);
        if (removed != null) {
            removed.clear();
        }
    }

    /** 仅当缓冲仍是登记的同一实例时移除并清空，防止误删后来者。 */
    private void clear(String runId, ChatRunEventBuffer identity) {
        if (buffers.remove(runId, identity)) {
            identity.clear();
        }
    }

    /** 获取或创建运行缓冲（按需懒建）。 */
    private ChatRunEventBuffer buffer(String runId) {
        return buffers.computeIfAbsent(
                runId, id -> new ChatRunEventBuffer(id, maxEvents, maxBytes, subscriberQueueSize, senderExecutor));
    }
}
