package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 会话源流尾链测试：固定「前驱源流排空后才启动后继」的非阻塞排序语义。
 *
 * @author Jin
 */
class SessionSourceTailChainTest {

    /** 同步执行器：让尾链回调在当前线程即时执行，便于断言顺序。 */
    private final Executor directExecutor = Runnable::run;

    @Test
    void shouldStartImmediatelyWhenNoPredecessor() {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        SessionKey key = new SessionKey("t", "u", "s");
        CompletableFuture<Void> drained = new CompletableFuture<>();
        AtomicInteger started = new AtomicInteger();

        chain.enqueue(key, directExecutor, started::incrementAndGet, drained);

        assertThat(started.get()).isEqualTo(1);
    }

    @Test
    void shouldDeferSuccessorUntilPredecessorDrained() {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        SessionKey key = new SessionKey("t", "u", "s");
        CompletableFuture<Void> firstDrained = new CompletableFuture<>();
        CompletableFuture<Void> secondDrained = new CompletableFuture<>();
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

        chain.enqueue(key, directExecutor, () -> order.add("first-start"), firstDrained);
        chain.enqueue(key, directExecutor, () -> order.add("second-start"), secondDrained);

        // 第一节点无前驱立即启动；第二节点在前驱排空前不得启动。
        assertThat(order).containsExactly("first-start");

        firstDrained.complete(null);
        assertThat(order).containsExactly("first-start", "second-start");
    }

    @Test
    void shouldNotBlockSuccessorWhenPredecessorCompletesExceptionally() {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        SessionKey key = new SessionKey("t", "u", "s");
        CompletableFuture<Void> firstDrained = new CompletableFuture<>();
        CompletableFuture<Void> secondDrained = new CompletableFuture<>();
        AtomicInteger secondStarted = new AtomicInteger();

        chain.enqueue(key, directExecutor, () -> {}, firstDrained);
        chain.enqueue(key, directExecutor, secondStarted::incrementAndGet, secondDrained);

        // 前驱异常完成（源流 error/cancel）也必须释放后继。
        firstDrained.completeExceptionally(new RuntimeException("boom"));
        assertThat(secondStarted.get()).isEqualTo(1);
    }

    @Test
    void shouldSerializeThreeSuccessiveSources() {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        SessionKey key = new SessionKey("t", "u", "s");
        CompletableFuture<Void> d1 = new CompletableFuture<>();
        CompletableFuture<Void> d2 = new CompletableFuture<>();
        CompletableFuture<Void> d3 = new CompletableFuture<>();
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();

        chain.enqueue(key, directExecutor, () -> order.add("s1"), d1);
        chain.enqueue(key, directExecutor, () -> order.add("s2"), d2);
        chain.enqueue(key, directExecutor, () -> order.add("s3"), d3);

        // s1 无前驱立即启动；s2 等 d1，s3 等 d2（各等紧邻前驱的排空信号）。
        assertThat(order).containsExactly("s1");
        d1.complete(null);
        assertThat(order).containsExactly("s1", "s2");
        d2.complete(null);
        assertThat(order).containsExactly("s1", "s2", "s3");
    }

    @Test
    void shouldContinueChainWhenStartActionThrows() {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        SessionKey key = new SessionKey("t", "u", "s");
        CompletableFuture<Void> d1 = new CompletableFuture<>();
        CompletableFuture<Void> d2 = new CompletableFuture<>();
        AtomicInteger secondStarted = new AtomicInteger();

        chain.enqueue(
                key,
                directExecutor,
                () -> {
                    throw new RuntimeException("start failure");
                },
                d1);
        chain.enqueue(key, directExecutor, secondStarted::incrementAndGet, d2);

        // 启动失败不阻塞后继：d1 完成后第二节点仍启动。
        d1.complete(null);
        assertThat(secondStarted.get()).isEqualTo(1);
    }

    @Test
    void shouldIsolateDifferentSessions() throws InterruptedException {
        SessionSourceTailChain chain = new SessionSourceTailChain();
        CompletableFuture<Void> aDrained = new CompletableFuture<>();
        CountDownLatch bStarted = new CountDownLatch(1);

        chain.enqueue(new SessionKey("t", "u", "session-a"), directExecutor, () -> {}, aDrained);
        // 不同会话键不入同一条尾链：b 的无前驱入队立即启动，无需另起节点验证。
        chain.enqueue(
                new SessionKey("t", "u", "session-b"), directExecutor, bStarted::countDown, new CompletableFuture<>());

        assertThat(bStarted.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
