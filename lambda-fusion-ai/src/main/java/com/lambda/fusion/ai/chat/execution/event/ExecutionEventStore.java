package com.lambda.fusion.ai.chat.execution.event;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** 单实例 Run 事件存储门面；管理各 Run 的事件缓冲区及其生命周期。 */
@Component
public class ExecutionEventStore {

    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Map<String, RunEventBuffer> buffers = new HashMap<>();
    private final ExecutorService senderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutionEventStore(AiProperties properties) {
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        this.subscriberQueueSize = properties.getChat().getRun().getSubscriberQueueSize();
    }

    public void initialize(String runId, long latestSeq) {
        buffer(runId).initialize(latestSeq);
    }

    public ExecutionEvent append(String runId, String aguiRunId, String aguiJson) {
        return buffer(runId).append(List.of(aguiJson), aguiRunId, null).events().getFirst();
    }

    /** 批量写入同一个 Agent 事件映射出的 AG-UI 事件；返回是否需要先持久化快照再收缩缓冲。 */
    public boolean appendAll(String runId, String aguiRunId, List<String> aguiJsonEvents) {
        return buffer(runId).append(aguiJsonEvents, aguiRunId, null).checkpointRequired();
    }

    public ExecutionEvent appendTerminalIfAbsent(String runId, String aguiRunId, String terminalKind, String aguiJson) {
        return buffer(runId)
                .append(List.of(aguiJson), aguiRunId, terminalKind)
                .events()
                .getFirst();
    }

    /** 仅淘汰已被持久化快照覆盖的旧事件。 */
    public void compact(String runId, long snapshotSeq) {
        RunEventBuffer current;
        synchronized (buffers) {
            current = buffers.get(runId);
        }
        if (current != null) {
            current.compact(snapshotSeq);
        }
    }

    public ExecutionEventCursorWindow cursorWindow(String runId) {
        RunEventBuffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.cursorWindow();
    }

    public ExecutionEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ExecutionEvent> consumer, Consumer<Throwable> failureConsumer) {
        RunEventBuffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(afterSeq, consumer, failureConsumer);
    }

    public long latestSeq(String runId, Long fallback) {
        RunEventBuffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        return buffer == null ? (fallback == null ? 0L : fallback) : buffer.latestSeq();
    }

    public void markTerminal(String runId, Duration retention) {
        buffer(runId).markExpiresAt(System.currentTimeMillis() + retention.toMillis());
    }

    private void clear(String runId) {
        RunEventBuffer removed;
        synchronized (buffers) {
            removed = buffers.remove(runId);
        }
        if (removed != null) {
            removed.clear();
        }
    }

    private void clear(String runId, RunEventBuffer identity) {
        RunEventBuffer removed = null;
        synchronized (buffers) {
            if (buffers.remove(runId, identity)) {
                removed = identity;
            }
        }
        if (removed != null) {
            removed.clear();
        }
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        Map<String, RunEventBuffer> current;
        synchronized (buffers) {
            current = Map.copyOf(buffers);
        }
        List<String> expired = new ArrayList<>();
        current.forEach((runId, buffer) -> {
            if (buffer.expired(now)) {
                expired.add(runId);
            }
        });
        expired.forEach(runId -> clear(runId, current.get(runId)));
    }

    @PreDestroy
    public void shutdown() {
        List<String> runIds;
        synchronized (buffers) {
            runIds = List.copyOf(buffers.keySet());
        }
        runIds.forEach(this::clear);
        senderExecutor.shutdownNow();
    }

    private RunEventBuffer buffer(String runId) {
        synchronized (buffers) {
            return buffers.computeIfAbsent(
                    runId, id -> new RunEventBuffer(id, maxEvents, maxBytes, subscriberQueueSize, senderExecutor));
        }
    }
}
