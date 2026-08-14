package com.lambda.fusion.ai.chat.execution.event;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于有界队列的事件订阅实现。
 *
 * <p>历史事件和实时事件由同一发送任务按顺序消费。
 *
 * @author Jin
 */
@Slf4j
final class QueuedEventSubscription implements ExecutionEventSubscription {

    private final String subscriptionId;
    private final String runId;
    private final int capacity;
    private final Consumer<ExecutionEvent> consumer;
    private final Consumer<Throwable> failureConsumer;
    private final RunEventBuffer owner;
    private final Executor senderExecutor;
    private final ArrayDeque<ExecutionEvent> replay;
    private final ArrayDeque<ExecutionEvent> queue = new ArrayDeque<>();
    private boolean draining;
    private boolean closed;
    private Runnable drainedAction;

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
            List<ExecutionEvent> replay,
            Consumer<ExecutionEvent> consumer,
            Consumer<Throwable> failureConsumer,
            RunEventBuffer owner,
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
    synchronized OfferResult offer(ExecutionEvent event) {
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

    /**
     * {@inheritDoc}
     *
     * @param action 队列排空回调
     */
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

    private void drain() {
        while (true) {
            ExecutionEvent next;
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

    /** 实时事件投递结果。 */
    enum OfferResult {
        ACCEPTED,
        CLOSED,
        FULL
    }
}
