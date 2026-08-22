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

    public void appendAll(String runId, String aguiRunId, List<AguiEvent> aguiEvents) {
        buffer(runId).append(aguiEvents, aguiRunId);
    }

    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String aguiJson) {
        return buffer(runId).appendTerminal(aguiJson, aguiRunId);
    }

    public ChatRunEventSubscription subscribe(
            String runId, long cursor, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(cursor, consumer, failureConsumer);
    }

    public long latestCursor(String runId) {
        ChatRunEventBuffer buffer = buffers.get(runId);
        return buffer == null ? 0L : buffer.latestCursor();
    }

    public boolean contains(String runId) {
        return buffers.containsKey(runId);
    }

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

    @PreDestroy
    public void shutdown() {
        List.copyOf(buffers.keySet()).forEach(this::clear);
        expiryExecutor.shutdownNow();
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
