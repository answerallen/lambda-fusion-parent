package com.lambda.fusion.ai.chat.execution.event;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** 通过有界队列异步发送历史回放与实时事件的订阅实现。 */
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

    String id() {
        return subscriptionId;
    }

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

    enum OfferResult {
        ACCEPTED,
        CLOSED,
        FULL
    }
}
