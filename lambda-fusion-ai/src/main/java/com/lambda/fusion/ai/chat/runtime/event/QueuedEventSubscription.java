package com.lambda.fusion.ai.chat.runtime.event;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于有界队列的事件订阅实现；历史事件和实时事件由同一发送任务按顺序消费。
 *
 * @author Jin
 */
@Slf4j
final class QueuedEventSubscription implements ChatRunEventSubscription {

    private final String subscriptionId;
    private final String runId;
    private final int capacity;
    private final Consumer<ChatRunEvent> consumer;
    private final Consumer<Throwable> failureConsumer;
    private final ChatRunEventBuffer owner;
    private final Executor senderExecutor;
    private final ArrayDeque<ChatRunEvent> replay;
    private final ArrayDeque<ChatRunEvent> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean closed;

    /**
     * 创建事件订阅。
     *
     * @param subscriptionId 订阅标识
     * @param runId 运行标识
     * @param capacity 实时事件队列容量
     * @param replay 待回放的历史事件
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @param owner 所属运行缓冲区
     * @param senderExecutor 事件发送执行器
     */
    QueuedEventSubscription(
            String subscriptionId,
            String runId,
            int capacity,
            List<ChatRunEvent> replay,
            Consumer<ChatRunEvent> consumer,
            Consumer<Throwable> failureConsumer,
            ChatRunEventBuffer owner,
            Executor senderExecutor) {
        this.subscriptionId = subscriptionId;
        this.runId = runId;
        this.capacity = capacity;
        this.replay = new ArrayDeque<>(replay);
        this.consumer = consumer;
        this.failureConsumer = failureConsumer;
        this.owner = owner;
        this.senderExecutor = senderExecutor;
        draining = true;
        senderExecutor.execute(this::drain);
    }

    /**
     * 获取订阅标识。
     *
     * @return 订阅标识
     */
    String id() {
        return subscriptionId;
    }

    /**
     * 将实时事件加入发送队列。
     *
     * @param event 执行事件
     * @return 投递结果
     */
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

    /** 关闭订阅并从运行缓冲区注销。 */
    @Override
    public void close() {
        owner.detach(subscriptionId, this);
        closeWithoutDetach();
    }

    /** 关闭订阅但不修改所属运行缓冲区。 */
    synchronized void closeWithoutDetach() {
        closed = true;
        queue.clear();
    }

    /**
     * 关闭订阅并异步通知发送失败。
     *
     * @param error 发送失败原因
     */
    void fail(Throwable error) {
        closeWithoutDetach();
        try {
            senderExecutor.execute(() -> failureConsumer.accept(error));
        } catch (RejectedExecutionException shutdown) {
            log.debug("事件发送线程池已关闭: runId={}", runId);
        }
    }

    /**
     * 发送任务主体：在锁内先取回放事件、再取实时事件，锁外逐条投递，直到两个队列都排空。
     *
     * <p>{@code draining} 标志保证同一时刻最多一个发送任务在跑：排空后置回 {@code false}，
     * 由下一次 {@code offer} 在有新事件时重新调度；避免无事件时空转。投递抛错时注销订阅并
     * 回调失败消费者，不再继续消费。
     */
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

    /** 实时事件投递结果。 */
    enum OfferResult {
        ACCEPTED,
        CLOSED,
        FULL
    }
}
