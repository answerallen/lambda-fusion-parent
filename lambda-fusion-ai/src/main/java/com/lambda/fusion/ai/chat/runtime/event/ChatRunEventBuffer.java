package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
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

/**
 * 单个对话运行的事件缓冲区。
 *
 * <p>事件追加和订阅注册使用同一实例锁，保证历史回放与实时订阅之间不存在事件缺口。
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
    private final Map<String, ChatRunEvent> terminals = new HashMap<>();
    private long nextSeq = 1;
    private long bytes;
    private long expiresAt;

    /**
     * 创建运行事件缓冲区。
     *
     * @param runId 运行标识
     * @param maxEvents 最大事件数
     * @param maxBytes 最大事件字节数
     * @param subscriberQueueSize 单个订阅的实时事件队列容量
     * @param senderExecutor 事件发送执行器
     */
    ChatRunEventBuffer(String runId, int maxEvents, long maxBytes, int subscriberQueueSize, Executor senderExecutor) {
        this.runId = runId;
        this.maxEvents = maxEvents;
        this.maxBytes = maxBytes;
        this.subscriberQueueSize = subscriberQueueSize;
        this.senderExecutor = senderExecutor;
    }

    /**
     * 追加一组 AG-UI 事件。
     *
     * @param aguiJsonEvents 事件 JSON 列表
     * @param aguiRunId AG-UI 运行标识
     * @param terminalKind 终态类型；非终态事件传入 {@code null}
     * @return 追加结果
     * @throws IllegalStateException 单个事件超过缓冲区字节限制
     */
    synchronized ChatRunEventOutcome append(List<String> aguiJsonEvents, String aguiRunId, String terminalKind) {
        if (terminalKind != null && terminals.containsKey(terminalKind)) {
            return new ChatRunEventOutcome(List.of(terminals.get(terminalKind)), overCapacity());
        }
        if (aguiJsonEvents == null || aguiJsonEvents.isEmpty()) {
            return new ChatRunEventOutcome(List.of(), overCapacity());
        }
        List<ChatRunEvent> appended = new ArrayList<>(aguiJsonEvents.size());
        long appendedBytes = 0;
        long seq = nextSeq;
        for (String aguiJson : aguiJsonEvents) {
            String data = AguiEventJsonCodec.withRunMetadata(aguiJson, runId, aguiRunId, seq);
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
        return new ChatRunEventOutcome(List.copyOf(appended), overCapacity());
    }

    /**
     * 订阅指定序号之后的事件。
     *
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 订阅句柄
     * @throws AiBusinessException 游标不在当前事件窗口内
     */
    synchronized ChatRunEventSubscription subscribe(
            long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
        long latestSeq = nextSeq - 1;
        if (afterSeq < minSeq - 1 || afterSeq > latestSeq) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
        }
        List<ChatRunEvent> replay =
                events.stream().filter(event -> event.seq() > afterSeq).toList();
        String subscriptionId = UUID.randomUUID().toString();
        QueuedEventSubscription subscriber = new QueuedEventSubscription(
                subscriptionId, runId, subscriberQueueSize, replay, consumer, failureConsumer, this, senderExecutor);
        subscribers.put(subscriptionId, subscriber);
        return subscriber;
    }

    /**
     * 获取最新事件序号。
     *
     * @return 最新事件序号
     */
    synchronized long latestSeq() {
        return nextSeq - 1;
    }

    /**
     * 获取当前可订阅的游标窗口。
     *
     * @return 游标窗口
     */
    synchronized ChatRunEventCursor cursorWindow() {
        long minSeq = events.isEmpty() ? nextSeq : events.getFirst().seq();
        return new ChatRunEventCursor(minSeq, nextSeq - 1);
    }

    /**
     * 根据已持久化序号初始化下一事件序号。
     *
     * @param latestSeq 已持久化的最新事件序号
     */
    synchronized void initialize(long latestSeq) {
        nextSeq = Math.max(nextSeq, latestSeq + 1);
    }

    /**
     * 删除快照已覆盖的超量事件。
     *
     * @param snapshotSeq 快照覆盖的最大事件序号
     * @throws IllegalStateException 快照未覆盖需要删除的事件
     */
    synchronized void compact(long snapshotSeq) {
        while (overCapacity() && !events.isEmpty() && events.getFirst().seq() <= snapshotSeq) {
            ChatRunEvent evicted = events.removeFirst();
            bytes -= eventBytes(evicted);
        }
        if (overCapacity()) {
            throw new IllegalStateException("Run快照未覆盖待淘汰事件: " + runId);
        }
    }

    /**
     * 设置缓冲区过期时间。
     *
     * @param value Unix 时间戳，单位毫秒
     */
    synchronized void markExpiresAt(long value) {
        expiresAt = value;
    }

    /**
     * 判断缓冲区是否过期。
     *
     * @param now 当前 Unix 时间戳，单位毫秒
     * @return 已设置过期时间且到期时返回 {@code true}
     */
    synchronized boolean expired(long now) {
        return expiresAt > 0 && expiresAt <= now;
    }

    /** 清空事件并关闭全部订阅。 */
    synchronized void clear() {
        List<QueuedEventSubscription> current = List.copyOf(subscribers.values());
        subscribers.clear();
        events.clear();
        terminals.clear();
        bytes = 0;
        current.forEach(QueuedEventSubscription::closeWithoutDetach);
    }

    /**
     * 注销指定订阅。
     *
     * @param subscriptionId 订阅标识
     * @param identity 订阅实例
     */
    synchronized void detach(String subscriptionId, QueuedEventSubscription identity) {
        subscribers.remove(subscriptionId, identity);
    }

    private boolean overCapacity() {
        return events.size() > maxEvents || bytes > maxBytes;
    }

    private static int eventBytes(ChatRunEvent event) {
        return event.data().getBytes(StandardCharsets.UTF_8).length;
    }
}
