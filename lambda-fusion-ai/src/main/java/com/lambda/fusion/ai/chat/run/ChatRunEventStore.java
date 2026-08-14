package com.lambda.fusion.ai.chat.run;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 单实例 Run 事件存储；锁内完成回放到实时订阅的切换，网络发送在有界订阅队列中异步执行。 */
@Slf4j
@Component
public class ChatRunEventStore {

    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Map<String, Buffer> buffers = new HashMap<>();
    private final ExecutorService senderExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatRunEventStore(AiProperties properties) {
        this.maxEvents = properties.getChat().getRun().getMaxEvents();
        this.maxBytes = properties.getChat().getRun().getMaxBytes();
        this.subscriberQueueSize = properties.getChat().getRun().getSubscriberQueueSize();
    }

    public void initialize(String runId, long latestSeq) {
        buffer(runId).initialize(latestSeq);
    }

    public ChatRunEvent append(String runId, String aguiRunId, String aguiJson) {
        return buffer(runId).append(List.of(aguiJson), aguiRunId, null).events().getFirst();
    }

    /** 批量写入同一个 Agent 事件映射出的 AG-UI 事件；返回是否需要先持久化快照再收缩缓冲。 */
    public boolean appendAll(String runId, String aguiRunId, List<String> aguiJsonEvents) {
        return buffer(runId).append(aguiJsonEvents, aguiRunId, null).checkpointRequired();
    }

    public ChatRunEvent appendTerminalIfAbsent(String runId, String aguiRunId, String terminalKind, String aguiJson) {
        return buffer(runId)
                .append(List.of(aguiJson), aguiRunId, terminalKind)
                .events()
                .getFirst();
    }

    /** 仅淘汰已被持久化快照覆盖的旧事件。 */
    public void compact(String runId, long snapshotSeq) {
        Buffer current;
        synchronized (buffers) {
            current = buffers.get(runId);
        }
        if (current != null) {
            current.compact(snapshotSeq);
        }
    }

    public CursorWindow cursorWindow(String runId) {
        Buffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.cursorWindow();
    }

    public Subscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        Buffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        if (buffer == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_EVENTS_EXPIRED, runId);
        }
        return buffer.subscribe(afterSeq, consumer, failureConsumer);
    }

    public long latestSeq(String runId, Long fallback) {
        Buffer buffer;
        synchronized (buffers) {
            buffer = buffers.get(runId);
        }
        return buffer == null ? (fallback == null ? 0L : fallback) : buffer.latestSeq();
    }

    public void markTerminal(String runId, Duration retention) {
        buffer(runId).markExpiresAt(System.currentTimeMillis() + retention.toMillis());
    }

    private void clear(String runId) {
        Buffer removed;
        synchronized (buffers) {
            removed = buffers.remove(runId);
        }
        if (removed != null) {
            removed.clear();
        }
    }

    private void clear(String runId, Buffer identity) {
        Buffer removed = null;
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
        Map<String, Buffer> current;
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

    private Buffer buffer(String runId) {
        synchronized (buffers) {
            return buffers.computeIfAbsent(runId, Buffer::new);
        }
    }

    private final class Buffer {
        private final String runId;
        private final ArrayDeque<ChatRunEvent> events = new ArrayDeque<>();
        private final Map<String, QueuedSubscriber> subscribers = new HashMap<>();
        private final Map<String, ChatRunEvent> terminals = new HashMap<>();
        private long nextSeq = 1;
        private long bytes;
        private long expiresAt;

        private Buffer(String runId) {
            this.runId = runId;
        }

        synchronized AppendOutcome append(List<String> aguiJsonEvents, String aguiRunId, String terminalKind) {
            if (terminalKind != null && terminals.containsKey(terminalKind)) {
                return new AppendOutcome(List.of(terminals.get(terminalKind)), overCapacity());
            }
            if (aguiJsonEvents == null || aguiJsonEvents.isEmpty()) {
                return new AppendOutcome(List.of(), overCapacity());
            }
            List<ChatRunEvent> appended = new ArrayList<>(aguiJsonEvents.size());
            long appendedBytes = 0;
            long seq = nextSeq;
            for (String aguiJson : aguiJsonEvents) {
                String data = AguiJson.withRunMetadata(aguiJson, runId, aguiRunId, seq);
                int size = data.getBytes(StandardCharsets.UTF_8).length;
                if (size > maxBytes) {
                    throw new IllegalStateException("单个Run事件超过缓冲容量: " + runId);
                }
                appended.add(new ChatRunEvent(seq, runId + ":" + seq, data));
                appendedBytes += size;
                seq++;
            }
            events.addAll(appended);
            bytes += appendedBytes;
            nextSeq = seq;
            if (terminalKind != null) {
                terminals.put(terminalKind, appended.getFirst());
            }
            List<QueuedSubscriber> slowSubscribers = new ArrayList<>();
            for (ChatRunEvent event : appended) {
                for (QueuedSubscriber subscriber : subscribers.values()) {
                    if (!slowSubscribers.contains(subscriber) && subscriber.offer(event) == OfferResult.FULL) {
                        slowSubscribers.add(subscriber);
                    }
                }
            }
            for (QueuedSubscriber subscriber : slowSubscribers) {
                subscribers.remove(subscriber.id(), subscriber);
                subscriber.fail(new SlowSubscriberException(runId));
            }
            return new AppendOutcome(List.copyOf(appended), overCapacity());
        }

        synchronized Subscription subscribe(
                long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
            long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
            long latestSeq = nextSeq - 1;
            if (afterSeq < minSeq - 1 || afterSeq > latestSeq) {
                throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
            }
            List<ChatRunEvent> replay =
                    events.stream().filter(event -> event.seq() > afterSeq).toList();
            String subscriptionId = UUID.randomUUID().toString();
            QueuedSubscriber subscriber =
                    new QueuedSubscriber(subscriptionId, subscriberQueueSize, replay, consumer, failureConsumer, this);
            subscribers.put(subscriptionId, subscriber);
            return subscriber;
        }

        synchronized long latestSeq() {
            return nextSeq - 1;
        }

        synchronized CursorWindow cursorWindow() {
            long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
            return new CursorWindow(minSeq, nextSeq - 1);
        }

        synchronized void initialize(long latestSeq) {
            nextSeq = Math.max(nextSeq, latestSeq + 1);
        }

        synchronized void compact(long snapshotSeq) {
            while (overCapacity() && !events.isEmpty() && events.getFirst().seq() <= snapshotSeq) {
                ChatRunEvent evicted = events.removeFirst();
                bytes -= eventBytes(evicted);
            }
            if (overCapacity()) {
                throw new IllegalStateException("Run快照未覆盖待淘汰事件: " + runId);
            }
        }

        synchronized void markExpiresAt(long value) {
            expiresAt = value;
        }

        synchronized boolean expired(long now) {
            return expiresAt > 0 && expiresAt <= now;
        }

        synchronized void clear() {
            List<QueuedSubscriber> current = List.copyOf(subscribers.values());
            subscribers.clear();
            events.clear();
            terminals.clear();
            bytes = 0;
            current.forEach(QueuedSubscriber::closeWithoutDetach);
        }

        private boolean overCapacity() {
            return events.size() > maxEvents || bytes > maxBytes;
        }

        private synchronized void detach(String subscriptionId, QueuedSubscriber identity) {
            subscribers.remove(subscriptionId, identity);
        }
    }

    private final class QueuedSubscriber implements Subscription {
        private final String subscriptionId;
        private final int capacity;
        private final Consumer<ChatRunEvent> consumer;
        private final Consumer<Throwable> failureConsumer;
        private final Buffer owner;
        private final ArrayDeque<ChatRunEvent> replay;
        private final ArrayDeque<ChatRunEvent> queue = new ArrayDeque<>();
        private boolean draining;
        private boolean closed;
        private Runnable drainedAction;

        private QueuedSubscriber(
                String subscriptionId,
                int capacity,
                List<ChatRunEvent> replay,
                Consumer<ChatRunEvent> consumer,
                Consumer<Throwable> failureConsumer,
                Buffer owner) {
            this.subscriptionId = subscriptionId;
            this.capacity = capacity;
            this.replay = new ArrayDeque<>(replay);
            this.consumer = consumer;
            this.failureConsumer = failureConsumer;
            this.owner = owner;
            draining = true;
            senderExecutor.execute(this::drain);
        }

        String id() {
            return subscriptionId;
        }

        synchronized OfferResult offer(ChatRunEvent event) {
            if (closed) {
                return OfferResult.CLOSED;
            }
            if (queue.size() >= capacity) {
                return OfferResult.FULL;
            }
            queue.addLast(event);
            if (!draining) {
                draining = true;
                try {
                    senderExecutor.execute(this::drain);
                } catch (RejectedExecutionException shutdown) {
                    draining = false;
                    return OfferResult.CLOSED;
                }
            }
            return OfferResult.ACCEPTED;
        }

        @Override
        public void close() {
            owner.detach(subscriptionId, this);
            closeWithoutDetach();
        }

        @Override
        public void whenDrained(Runnable action) {
            boolean runNow;
            synchronized (this) {
                if (closed) {
                    return;
                }
                runNow = queue.isEmpty() && !draining;
                if (!runNow) {
                    drainedAction = action;
                }
            }
            if (runNow) {
                senderExecutor.execute(action);
            }
        }

        synchronized void closeWithoutDetach() {
            closed = true;
            queue.clear();
        }

        void fail(Throwable error) {
            closeWithoutDetach();
            try {
                senderExecutor.execute(() -> failureConsumer.accept(error));
            } catch (RejectedExecutionException shutdown) {
                log.debug("事件发送线程池已关闭: runId={}", owner.runId);
            }
        }

        private void drain() {
            while (true) {
                ChatRunEvent next;
                synchronized (this) {
                    if (closed) {
                        draining = false;
                        return;
                    }
                    next = replay.pollFirst();
                    if (next == null) {
                        next = queue.pollFirst();
                    }
                    if (next == null) {
                        draining = false;
                        Runnable action = drainedAction;
                        drainedAction = null;
                        if (action != null) {
                            senderExecutor.execute(action);
                        }
                        return;
                    }
                }
                try {
                    consumer.accept(next);
                } catch (RuntimeException sendFailure) {
                    owner.detach(subscriptionId, this);
                    fail(sendFailure);
                    return;
                }
            }
        }
    }

    private static int eventBytes(ChatRunEvent event) {
        return event.data().getBytes(StandardCharsets.UTF_8).length;
    }

    private record AppendOutcome(List<ChatRunEvent> events, boolean checkpointRequired) {}

    public record CursorWindow(long minSeq, long latestSeq) {}

    public interface Subscription extends AutoCloseable {

        void whenDrained(Runnable action);

        @Override
        void close();
    }

    private enum OfferResult {
        ACCEPTED,
        CLOSED,
        FULL
    }

    private static final class SlowSubscriberException extends RuntimeException {
        private SlowSubscriberException(String runId) {
            super("Run订阅者消费过慢，已断开: " + runId);
        }
    }
}
