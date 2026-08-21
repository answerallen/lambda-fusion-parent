package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.exception.AiBusinessException;
import io.agentscope.core.agui.event.AguiEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionEventStoreTest {

    private ChatRunEventStore store;

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.shutdown();
        }
    }

    @Test
    void shouldReplayThenContinueWithLiveEventsWithoutGap() throws Exception {
        store = newStore(64, 64);
        store.initialize("run-1", 0L);
        append("run-1", "phase-1", "a");
        append("run-1", "phase-1", "b");

        List<ChatRunEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        ChatRunEventSubscription subscription = store.subscribe(
                "run-1",
                1,
                event -> {
                    received.add(event);
                    latch.countDown();
                },
                error -> {});
        append("run-1", "phase-1", "c");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).extracting(ChatRunEvent::seq).containsExactly(2L, 3L);
        assertThat(received.getLast().data())
                .contains("\"chatRunId\":\"run-1\"")
                .contains("\"runId\":\"phase-1\"")
                .contains("\"seq\":3");
        subscription.close();
    }

    @Test
    void shouldNotReplayOldEventsAgainAfterSubscriberBecomesIdle() throws Exception {
        store = newStore(64, 64);
        append("run-1", "phase-1", "a");
        List<Long> received = new CopyOnWriteArrayList<>();
        CountDownLatch replayed = new CountDownLatch(1);
        ChatRunEventSubscription subscription = store.subscribe(
                "run-1",
                0,
                event -> {
                    received.add(event.seq());
                    replayed.countDown();
                },
                error -> {});
        assertThat(replayed.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(20);

        append("run-1", "phase-1", "b");
        awaitSize(received, 2);
        append("run-1", "phase-1", "c");
        awaitSize(received, 3);

        assertThat(received).containsExactly(1L, 2L, 3L);
        subscription.close();
    }

    @Test
    void shouldRunDrainedActionAfterReplayIsConsumed() throws Exception {
        store = newStore(64, 64);
        append("run-1", "phase-1", "a");
        append("run-1", "phase-1", "b");
        List<String> timeline = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        ChatRunEventSubscription subscription = store.subscribe(
                "run-1",
                0,
                event -> {
                    timeline.add("event-" + event.seq());
                    latch.countDown();
                },
                error -> {});
        subscription.whenDrained(() -> {
            timeline.add("drained");
            latch.countDown();
        });

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(timeline).containsExactly("event-1", "event-2", "drained");
        subscription.close();
    }

    @Test
    void shouldNotifyAndDetachSlowSubscriberWhenQueueIsFull() throws Exception {
        store = newStore(64, 1);
        store.initialize("run-1", 0L);
        List<Long> received = new CopyOnWriteArrayList<>();
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ChatRunEventSubscription subscription = store.subscribe(
                "run-1",
                0,
                event -> {
                    received.add(event.seq());
                    consumerEntered.countDown();
                    try {
                        releaseConsumer.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                },
                error -> {
                    failure.set(error);
                    failed.countDown();
                });

        try {
            append("run-1", "phase-1", "a");
            assertThat(consumerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            append("run-1", "phase-1", "b");
            append("run-1", "phase-1", "c");

            assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).hasMessageContaining("Run订阅者消费过慢");
            append("run-1", "phase-1", "d");
        } finally {
            releaseConsumer.countDown();
            subscription.close();
        }

        Thread.sleep(20);
        assertThat(received).containsExactly(1L);
    }

    @Test
    void shouldAppendTerminalOnlyOnceForSameKind() {
        store = newStore(64, 64);
        ChatRunEvent first = store.appendTerminalIfAbsent("run-1", "phase-1", "{\"type\":\"RUN_FINISHED\"}");
        ChatRunEvent retried = store.appendTerminalIfAbsent("run-1", "phase-1", "{\"type\":\"RUN_FINISHED\"}");

        assertThat(retried).isEqualTo(first);
        assertThat(store.latestSeq("run-1", 0L)).isEqualTo(1L);
    }

    @Test
    void shouldRejectCursorOlderThanRetainedWindow() {
        store = newStore(2, 64);
        append("run-1", "phase-1", "a");
        append("run-1", "phase-1", "b");
        append("run-1", "phase-1", "c");
        store.compact("run-1", 3);

        assertThatThrownBy(() -> store.subscribe("run-1", 0, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldRejectCursorAheadOfLatestEvent() {
        store = newStore(64, 64);
        append("run-1", "phase-1", "a");

        assertThatThrownBy(() -> store.subscribe("run-1", 2, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldRejectSubscribeWhenBufferIsMissing() {
        store = newStore(2, 64);

        assertThatThrownBy(() -> store.subscribe("missing", 0, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldPublishStagedEventsOnlyAfterCommit() throws Exception {
        store = newStore(64, 64);
        store.initialize("run-1", 0L);
        List<Long> received = new CopyOnWriteArrayList<>();
        CountDownLatch published = new CountDownLatch(1);
        ChatRunEventSubscription subscription = store.subscribe(
                "run-1",
                0,
                event -> {
                    received.add(event.seq());
                    published.countDown();
                },
                error -> {});

        List<AguiEvent> interrupts =
                List.of(new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "interrupt"));
        // 数据库迁移成功：暂存事件发布，订阅者可见，序号被分配。
        boolean committed = store.runExclusive("run-1", "phase-1", interrupts, () -> true);

        assertThat(committed).isTrue();
        assertThat(published.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsExactly(1L);
        assertThat(store.latestSeq("run-1", 0L)).isEqualTo(1L);
        subscription.close();
    }

    @Test
    void shouldDiscardStagedEventsWhenCommitFails() throws Exception {
        store = newStore(64, 64);
        store.initialize("run-1", 0L);
        List<Long> received = new CopyOnWriteArrayList<>();
        ChatRunEventSubscription subscription =
                store.subscribe("run-1", 0, event -> received.add(event.seq()), error -> {});

        List<AguiEvent> interrupts =
                List.of(new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "interrupt"));
        // 数据库迁移落败：暂存事件丢弃，订阅者不可见，但序号已分配（快照覆盖语义保留）。
        boolean committed = store.runExclusive("run-1", "phase-1", interrupts, () -> false);

        assertThat(committed).isFalse();
        Thread.sleep(50);
        assertThat(received).isEmpty();
        assertThat(store.latestSeq("run-1", 0L)).isEqualTo(1L);
        subscription.close();
    }

    @Test
    void shouldDiscardStagedEventsWhenCommitThrows() throws Exception {
        store = newStore(64, 64);
        store.initialize("run-1", 0L);
        List<Long> received = new CopyOnWriteArrayList<>();
        ChatRunEventSubscription subscription =
                store.subscribe("run-1", 0, event -> received.add(event.seq()), error -> {});
        List<AguiEvent> interrupts =
                List.of(new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "interrupt"));

        assertThatThrownBy(() -> store.runExclusive("run-1", "phase-1", interrupts, () -> {
                    throw new IllegalStateException("db failed");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db failed");

        // 前一次失败暂存不得混入后续成功批次；仅后续事件可见。
        assertThat(store.runExclusive("run-1", "phase-1", interrupts, () -> true))
                .isTrue();
        awaitSize(received, 1);
        assertThat(received).containsExactly(2L);
        subscription.close();
    }

    @Test
    void shouldRejectStageAfterTerminal() {
        store = newStore(64, 64);
        store.appendTerminalIfAbsent("run-1", "phase-1", "{\"type\":\"RUN_FINISHED\"}");

        assertThatThrownBy(() -> store.runExclusive(
                        "run-1",
                        "phase-1",
                        List.of(new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "x")),
                        () -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已终结");
    }

    @Test
    void shouldCompactOnlyAfterBatchIsCoveredBySnapshot() {
        store = newStore(2, 64);
        boolean checkpointRequired = store.appendAll(
                "run-1",
                "phase-1",
                List.of(
                        new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "a"),
                        new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "b"),
                        new AguiEvent.TextMessageContent("session-1", "phase-1", "m-1", "c")));

        assertThat(checkpointRequired).isTrue();
        assertThatThrownBy(() -> store.compact("run-1", 0)).isInstanceOf(IllegalStateException.class);
        store.compact("run-1", 3);
        assertThat(store.latestSeq("run-1", 0L)).isEqualTo(3L);
    }

    @Test
    void shouldPurgeTerminalBufferAfterRetention() throws Exception {
        store = newStore(64, 64);
        append("run-1", "phase-1", "a");
        store.markTerminal("run-1", Duration.ofMillis(1));
        Thread.sleep(5);
        store.purgeExpired();

        assertThatThrownBy(() -> store.subscribe("run-1", 0, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    private void append(String runId, String aguiRunId, String delta) {
        store.appendAll(
                runId, aguiRunId, List.of(new AguiEvent.TextMessageContent("session-1", aguiRunId, "m-1", delta)));
    }

    private static ChatRunEventStore newStore(int maxEvents, int queueSize) {
        AiProperties properties = new AiProperties();
        properties.getChat().getRun().setMaxEvents(maxEvents);
        properties.getChat().getRun().setMaxBytes(65_536);
        properties.getChat().getRun().setSubscriberQueueSize(queueSize);
        return new ChatRunEventStore(properties);
    }

    private static void awaitSize(List<?> values, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (values.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(values).hasSize(expected);
    }
}
