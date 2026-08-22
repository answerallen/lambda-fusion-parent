package com.lambda.fusion.ai.chat.runtime.event.memory;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.event.spi.ChatRunEventBackend;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.agui.event.AguiEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 内存事件后端：以 JVM 本地 {@link ChatRunEventBuffer} 承载事件，仅供单机部署。本类是历史
 * {@code ChatRunEventStore} 的存储面下沉，语义与既有单机实现完全一致；订阅分发仍由门面统一编排。
 *
 * @author Jin
 */
public final class MemoryChatRunEventBackend implements ChatRunEventBackend {

    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Map<String, ChatRunEventBuffer> buffers = new ConcurrentHashMap<>();
    private final ExecutorService senderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建内存事件后端。
     *
     * @param properties AI 模块配置
     */
    public MemoryChatRunEventBackend(AiProperties properties) {
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        this.subscriberQueueSize = properties.getChat().getRun().getSubscriberQueueSize();
    }

    @Override
    public void initialize(String runId, Long latestSeq) {
        buffer(runId).initialize(latestSeq);
    }

    @Override
    public boolean appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents) {
        return buffer(runId).append(aguiEvents, aguiRunId);
    }

    @Override
    public boolean runExclusive(String runId, String aguiRunId, List<AguiEvent> aguiEvents, BooleanSupplier dbAction) {
        ChatRunEventBuffer buffer = buffer(runId);
        synchronized (buffer) {
            buffer.stage(aguiEvents, aguiRunId);
            try {
                boolean committed = dbAction.getAsBoolean();
                if (committed) {
                    buffer.publishStaged();
                } else {
                    buffer.discardStaged();
                }
                return committed;
            } catch (RuntimeException failure) {
                buffer.discardStaged();
                throw failure;
            }
        }
    }

    @Override
    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson) {
        return buffer(runId).appendTerminal(aguiJson, aguiRunId);
    }

    @Override
    public long latestSeq(String runId, Long fallback) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        return buffer == null ? (fallback == null ? 0L : fallback) : buffer.latestSeq();
    }

    @Override
    public void compact(String runId, long snapshotSeq) {
        ChatRunEventBuffer current = buffers.get(runId);
        if (current != null) {
            current.compact(snapshotSeq);
        }
    }

    @Override
    public List<ChatRunEvent> readAfter(String runId, long afterSeq) {
        ChatRunEventBuffer current = buffers.get(runId);
        return current == null ? List.of() : current.readAfter(afterSeq);
    }

    /**
     * 订阅本节点本地缓冲（仅当运行在本节点执行、缓冲在场时）。缓冲不存在表示该运行非本节点执行
     * 或已过期清理，抛 {@code CHAT_RUN_EVENTS_EXPIRED}，由调用方引导客户端走 bootstrap/resync。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 订阅句柄
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(afterSeq, consumer, failureConsumer);
    }

    @Override
    public void markTerminal(String runId, Duration retention) {
        buffer(runId).markExpiresAt(System.currentTimeMillis() + retention.toMillis());
    }

    @Override
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

    @Override
    public void shutdown() {
        List.copyOf(buffers.keySet()).forEach(this::clear);
        senderExecutor.shutdownNow();
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

    private ChatRunEventBuffer buffer(String runId) {
        return buffers.computeIfAbsent(
                runId, id -> new ChatRunEventBuffer(id, maxEvents, maxBytes, subscriberQueueSize, senderExecutor));
    }
}
