package com.lambda.fusion.ai.chat.execution.event;

import com.lambda.fusion.ai.chat.execution.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** 单个 Run 的有序事件缓冲区；以同一实例锁保证历史回放到实时订阅的无缝切换。 */
final class RunEventBuffer {

    private final String runId;
    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Executor senderExecutor;
    private final ArrayDeque<ExecutionEvent> events = new ArrayDeque<>();
    private final Map<String, QueuedEventSubscription> subscribers = new HashMap<>();
    private final Map<String, ExecutionEvent> terminals = new HashMap<>();
    private long nextSeq = 1;
    private long bytes;
    private long expiresAt;

    RunEventBuffer(String runId, int maxEvents, long maxBytes, int subscriberQueueSize, Executor senderExecutor) {
        this.runId = runId;
        this.maxEvents = maxEvents;
        this.maxBytes = maxBytes;
        this.subscriberQueueSize = subscriberQueueSize;
        this.senderExecutor = senderExecutor;
    }

    synchronized AppendOutcome append(List<String> aguiJsonEvents, String aguiRunId, String terminalKind) {
        if (terminalKind != null && terminals.containsKey(terminalKind)) {
            return new AppendOutcome(List.of(terminals.get(terminalKind)), overCapacity());
        }
        if (aguiJsonEvents == null || aguiJsonEvents.isEmpty()) {
            return new AppendOutcome(List.of(), overCapacity());
        }
        List<ExecutionEvent> appended = new ArrayList<>(aguiJsonEvents.size());
        long appendedBytes = 0;
        long seq = nextSeq;
        for (String aguiJson : aguiJsonEvents) {
            String data = AguiEventJsonCodec.withRunMetadata(aguiJson, runId, aguiRunId, seq);
            int size = data.getBytes(StandardCharsets.UTF_8).length;
            if (size > maxBytes) {
                throw new IllegalStateException("单个Run事件超过缓冲容量: " + runId);
            }
            appended.add(new ExecutionEvent(seq, runId + ":" + seq, data));
            appendedBytes += size;
            seq++;
        }
        events.addAll(appended);
        bytes += appendedBytes;
        nextSeq = seq;
        if (terminalKind != null) {
            terminals.put(terminalKind, appended.getFirst());
        }
        List<QueuedEventSubscription> slowSubscribers = new ArrayList<>();
        for (ExecutionEvent event : appended) {
            for (QueuedEventSubscription subscriber : subscribers.values()) {
                if (!slowSubscribers.contains(subscriber)
                        && subscriber.offer(event) == QueuedEventSubscription.OfferResult.FULL) {
                    slowSubscribers.add(subscriber);
                }
            }
        }
        for (QueuedEventSubscription subscriber : slowSubscribers) {
            subscribers.remove(subscriber.id(), subscriber);
            subscriber.fail(new SlowEventSubscriberException(runId));
        }
        return new AppendOutcome(List.copyOf(appended), overCapacity());
    }

    synchronized ExecutionEventSubscription subscribe(
            long afterSeq, Consumer<ExecutionEvent> consumer, Consumer<Throwable> failureConsumer) {
        long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
        long latestSeq = nextSeq - 1;
        if (afterSeq < minSeq - 1 || afterSeq > latestSeq) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
        }
        List<ExecutionEvent> replay =
                events.stream().filter(event -> event.seq() > afterSeq).toList();
        String subscriptionId = UUID.randomUUID().toString();
        QueuedEventSubscription subscriber = new QueuedEventSubscription(
                subscriptionId, runId, subscriberQueueSize, replay, consumer, failureConsumer, this, senderExecutor);
        subscribers.put(subscriptionId, subscriber);
        return subscriber;
    }

    synchronized long latestSeq() {
        return nextSeq - 1;
    }

    synchronized ExecutionEventCursorWindow cursorWindow() {
        long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
        return new ExecutionEventCursorWindow(minSeq, nextSeq - 1);
    }

    synchronized void initialize(long latestSeq) {
        nextSeq = Math.max(nextSeq, latestSeq + 1);
    }

    synchronized void compact(long snapshotSeq) {
        while (overCapacity() && !events.isEmpty() && events.getFirst().seq() <= snapshotSeq) {
            ExecutionEvent evicted = events.removeFirst();
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
        List<QueuedEventSubscription> current = List.copyOf(subscribers.values());
        subscribers.clear();
        events.clear();
        terminals.clear();
        bytes = 0;
        current.forEach(QueuedEventSubscription::closeWithoutDetach);
    }

    synchronized void detach(String subscriptionId, QueuedEventSubscription identity) {
        subscribers.remove(subscriptionId, identity);
    }

    private boolean overCapacity() {
        return events.size() > maxEvents || bytes > maxBytes;
    }

    private static int eventBytes(ExecutionEvent event) {
        return event.data().getBytes(StandardCharsets.UTF_8).length;
    }

    record AppendOutcome(List<ExecutionEvent> events, boolean checkpointRequired) {}
}
