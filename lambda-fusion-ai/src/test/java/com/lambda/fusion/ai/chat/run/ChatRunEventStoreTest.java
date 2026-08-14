package com.lambda.fusion.ai.chat.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatRunEventStoreTest {

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
        store.initialize("run-1", 0);
        store.append("run-1", "phase-1", "{\"type\":\"RUN_STARTED\"}");
        store.append("run-1", "phase-1", "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"delta\":\"a\"}");

        List<ChatRunEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        ChatRunEventStore.Subscription subscription = store.subscribe(
                "run-1",
                1,
                event -> {
                    received.add(event);
                    latch.countDown();
                },
                error -> {});
        store.append("run-1", "phase-1", "{\"type\":\"TEXT_MESSAGE_CONTENT\",\"delta\":\"b\"}");

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
        store.append("run-1", "phase-1", "{\"type\":\"A\"}");
        List<Long> received = new CopyOnWriteArrayList<>();
        CountDownLatch replayed = new CountDownLatch(1);
        ChatRunEventStore.Subscription subscription = store.subscribe(
                "run-1",
                0,
                event -> {
                    received.add(event.seq());
                    replayed.countDown();
                },
                error -> {});
        assertThat(replayed.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(20);

        store.append("run-1", "phase-1", "{\"type\":\"B\"}");
        awaitSize(received, 2);
        store.append("run-1", "phase-1", "{\"type\":\"C\"}");
        awaitSize(received, 3);

        assertThat(received).containsExactly(1L, 2L, 3L);
        subscription.close();
    }

    @Test
    void shouldAppendTerminalOnlyOnceForSameKind() {
        store = newStore(64, 64);
        ChatRunEvent first =
                store.appendTerminalIfAbsent("run-1", "phase-1", "COMPLETED", "{\"type\":\"RUN_FINISHED\"}");
        ChatRunEvent retried =
                store.appendTerminalIfAbsent("run-1", "phase-1", "COMPLETED", "{\"type\":\"RUN_FINISHED\"}");

        assertThat(retried).isEqualTo(first);
        assertThat(store.latestSeq("run-1", 0L)).isEqualTo(1L);
    }

    @Test
    void shouldRejectCursorOlderThanRetainedWindow() {
        store = newStore(2, 64);
        store.append("run-1", "phase-1", "{\"type\":\"A\"}");
        store.append("run-1", "phase-1", "{\"type\":\"B\"}");
        store.append("run-1", "phase-1", "{\"type\":\"C\"}");
        store.compact("run-1", 3);

        assertThatThrownBy(() -> store.subscribe("run-1", 0, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldRejectCursorAheadOfLatestEvent() {
        store = newStore(64, 64);
        store.append("run-1", "phase-1", "{\"type\":\"A\"}");

        assertThatThrownBy(() -> store.subscribe("run-1", 2, event -> {}, error -> {}))
                .isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldExposeCurrentCursorWindowWithoutCreatingMissingBuffer() {
        store = newStore(2, 64);
        store.append("run-1", "phase-1", "{\"type\":\"A\"}");
        store.append("run-1", "phase-1", "{\"type\":\"B\"}");
        store.append("run-1", "phase-1", "{\"type\":\"C\"}");
        store.compact("run-1", 3);

        assertThat(store.cursorWindow("run-1")).isEqualTo(new ChatRunEventStore.CursorWindow(2, 3));
        assertThatThrownBy(() -> store.cursorWindow("missing")).isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldCompactOnlyAfterBatchIsCoveredBySnapshot() {
        store = newStore(2, 64);
        boolean checkpointRequired = store.appendAll(
                "run-1", "phase-1", List.of("{\"type\":\"A\"}", "{\"type\":\"B\"}", "{\"type\":\"C\"}"));

        assertThat(checkpointRequired).isTrue();
        assertThat(store.cursorWindow("run-1")).isEqualTo(new ChatRunEventStore.CursorWindow(1, 3));
        assertThatThrownBy(() -> store.compact("run-1", 0)).isInstanceOf(IllegalStateException.class);
        store.compact("run-1", 3);
        assertThat(store.cursorWindow("run-1")).isEqualTo(new ChatRunEventStore.CursorWindow(2, 3));
    }

    @Test
    void shouldPurgeTerminalBufferAfterRetention() throws Exception {
        store = newStore(64, 64);
        store.append("run-1", "phase-1", "{\"type\":\"RUN_STARTED\"}");
        store.markTerminal("run-1", Duration.ofMillis(1));
        Thread.sleep(5);
        store.purgeExpired();

        assertThatThrownBy(() -> store.cursorWindow("run-1")).isInstanceOf(AiBusinessException.class);
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
