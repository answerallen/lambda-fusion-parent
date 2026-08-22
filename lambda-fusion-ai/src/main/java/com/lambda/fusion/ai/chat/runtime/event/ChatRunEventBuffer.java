package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import io.agentscope.core.agui.event.AguiEvent;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 单个 Run 的进程内事件窗口。游标只用于衔接“快照之后”的本地事件，不进入数据库或协议载荷。
 *
 * @author Jin
 */
final class ChatRunEventBuffer {

    private final String runId;
    private final int maxEvents;
    private final long maxBytes;
    private final int subscriberQueueSize;
    private final Executor senderExecutor;
    private final ArrayDeque<ChatRunEvent> events = new ArrayDeque<>();
    private final Map<String, QueuedEventSubscription> subscribers = new HashMap<>();

    private ChatRunEvent terminal;
    private long nextCursor = 1;
    private long bytes;
    private long expiresAt;

    ChatRunEventBuffer(String runId, int maxEvents, long maxBytes, int subscriberQueueSize, Executor senderExecutor) {
        this.runId = runId;
        this.maxEvents = maxEvents;
        this.maxBytes = maxBytes;
        this.subscriberQueueSize = subscriberQueueSize;
        this.senderExecutor = senderExecutor;
    }

    synchronized void append(List<AguiEvent> aguiEvents, String aguiRunId) {
        if (aguiEvents == null || aguiEvents.isEmpty()) {
            return;
        }
        List<ChatRunEvent> appended = new ArrayList<>(aguiEvents.size());
        long cursor = nextCursor;
        for (AguiEvent event : aguiEvents) {
            appended.add(new ChatRunEvent(
                    cursor, event.getType().name(), AguiEventJsonCodec.encodeRunEvent(event, runId, aguiRunId)));
            cursor++;
        }
        commit(appended, false);
    }

    synchronized ChatRunEvent appendTerminal(String aguiJson, String aguiRunId) {
        if (terminal != null) {
            return terminal;
        }
        ChatRunEvent appended = new ChatRunEvent(
                nextCursor,
                AguiEventJsonCodec.readEventType(aguiJson),
                AguiEventJsonCodec.withRunMetadata(aguiJson, runId, aguiRunId));
        commit(List.of(appended), true);
        return appended;
    }

    private void commit(List<ChatRunEvent> appended, boolean terminalEvent) {
        long appendedBytes = 0;
        for (ChatRunEvent event : appended) {
            int size = eventBytes(event);
            if (size > maxBytes) {
                throw new IllegalStateException("单个 Run 事件超过本地缓冲容量: " + runId);
            }
            appendedBytes += size;
        }
        events.addAll(appended);
        bytes += appendedBytes;
        nextCursor = appended.getLast().cursor() + 1;
        if (terminalEvent) {
            terminal = appended.getFirst();
        }
        publish(appended);
        trimWindow();
    }

    private void publish(List<ChatRunEvent> appended) {
        List<QueuedEventSubscription> slowSubscribers = new ArrayList<>();
        for (ChatRunEvent event : appended) {
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
    }

    private void trimWindow() {
        while ((events.size() > maxEvents || bytes > maxBytes) && events.size() > 1) {
            ChatRunEvent evicted = events.removeFirst();
            bytes -= eventBytes(evicted);
        }
    }

    synchronized ChatRunEventSubscription subscribe(
            long afterCursor, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        long latestCursor = nextCursor - 1;
        long cursor = Math.max(0, Math.min(afterCursor, latestCursor));
        List<ChatRunEvent> replay =
                events.stream().filter(event -> event.cursor() > cursor).toList();
        String subscriptionId = UUID.randomUUID().toString();
        QueuedEventSubscription subscriber = new QueuedEventSubscription(
                subscriptionId, runId, subscriberQueueSize, replay, consumer, failureConsumer, this, senderExecutor);
        subscribers.put(subscriptionId, subscriber);
        return subscriber;
    }

    synchronized long latestCursor() {
        return nextCursor - 1;
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
        terminal = null;
        bytes = 0;
        current.forEach(QueuedEventSubscription::closeWithoutDetach);
    }

    synchronized void detach(String subscriptionId, QueuedEventSubscription identity) {
        subscribers.remove(subscriptionId, identity);
    }

    private static int eventBytes(ChatRunEvent event) {
        return event.data().getBytes(StandardCharsets.UTF_8).length;
    }

    private static final class SlowEventSubscriberException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private SlowEventSubscriberException(String runId) {
            super("Run 订阅者消费过慢，已断开: " + runId);
        }
    }
}
