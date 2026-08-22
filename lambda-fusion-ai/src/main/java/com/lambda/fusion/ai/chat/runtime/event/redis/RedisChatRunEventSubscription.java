package com.lambda.fusion.ai.chat.runtime.event.redis;

import com.lambda.fusion.ai.chat.runtime.event.ChatRunControlEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.event.spi.ChatRunEventBackend;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 后端的跨节点事件订阅：任意节点对某 Run 的事件序列做「有界轮询取数 + 空转 DB 复核」。
 * 历史先经 {@code readAfter} 回放，随后循环取增量；每轮空转时回调 {@code recheck} 复核 DB 的
 * {@code (status, phaseNo, leaseEpoch)}——变化（含 owner 易主 / 进入待确认 / 终结）即发送
 * {@link ChatRunControlEvent#TYPE_RESYNC_REQUIRED} 控制事件并断开，由前端重新 bootstrap。
 *
 * <p>本实现以「有界休眠轮询」逼近 {@code XREAD} 有界阻塞语义（§6.5），避免阻塞虚拟线程占用连接；
 * 终态事件送达后即完成订阅。
 *
 * @author Jin
 */
@Slf4j
public final class RedisChatRunEventSubscription implements ChatRunEventSubscription {

    /** 空轮询间隔（毫秒），逼近 XREAD 有界阻塞的唤醒粒度。 */
    private static final long POLL_INTERVAL_MS = 200L;

    private final String runId;
    private final ChatRunEventBackend backend;
    private final Consumer<ChatRunEvent> consumer;
    private final Consumer<Throwable> failureConsumer;
    private final BooleanSupplier recheck;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Runnable drainedAction;

    /**
     * 创建 Redis 订阅并启动后台轮询。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param backend 事件后端
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @param recheck DB 复核回调；返回 {@code false} 表示状态/阶段/epoch 已变化，应 RESYNC_REQUIRED；{@code null} 表示终态即止
     * @param executor 轮询执行器
     */
    public RedisChatRunEventSubscription(
            String runId,
            long afterSeq,
            ChatRunEventBackend backend,
            Consumer<ChatRunEvent> consumer,
            Consumer<Throwable> failureConsumer,
            BooleanSupplier recheck,
            Executor executor) {
        this.runId = runId;
        this.backend = backend;
        this.consumer = consumer;
        this.failureConsumer = failureConsumer;
        this.recheck = recheck;
        long[] cursor = {afterSeq};
        executor.execute(() -> loop(cursor));
    }

    /** 关闭订阅。 */
    @Override
    public void close() {
        closed.set(true);
    }

    /** {@inheritDoc} */
    @Override
    public void whenDrained(Runnable action) {
        this.drainedAction = action;
    }

    private void loop(long[] cursor) {
        try {
            while (!closed.get()) {
                List<ChatRunEvent> batch = backend.readAfter(runId, cursor[0]);
                if (!batch.isEmpty()) {
                    for (ChatRunEvent event : batch) {
                        if (closed.get()) {
                            return;
                        }
                        consumer.accept(event);
                        cursor[0] = event.seq();
                    }
                    continue;
                }
                // 空转：先查最新水位判断是否仍有未读（readAfter 与 latestSeq 之间可能有新事件），再 DB 复核。
                long latest = backend.latestSeq(runId, cursor[0]);
                if (latest <= cursor[0]) {
                    if (recheck == null) {
                        // 无复核（理论上 Redis 后端均带复核）；保守地继续轮询。
                    } else if (!recheck.getAsBoolean()) {
                        consumer.accept(ChatRunControlEvent.resyncRequired(runId));
                        close();
                        return;
                    }
                }
                sleepQuietly();
            }
        } catch (RuntimeException failure) {
            if (!closed.get()) {
                try {
                    failureConsumer.accept(failure);
                } catch (RuntimeException ignored) {
                    log.debug("事件订阅失败通知异常: runId={}", runId);
                }
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            closed.set(true);
        }
    }
}
