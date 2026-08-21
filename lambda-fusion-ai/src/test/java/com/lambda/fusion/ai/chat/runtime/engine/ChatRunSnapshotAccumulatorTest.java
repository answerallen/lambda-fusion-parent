package com.lambda.fusion.ai.chat.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotDelta;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRunSnapshotAccumulatorTest {

    @Test
    void shouldAccumulateCompletedToolSnapshot() {
        ChatRunSnapshotAccumulator accumulator =
                new ChatRunSnapshotAccumulator(ChatRunSnapshot.empty("run-1", "phase-1", 1));

        accumulator.apply(new ChatRunSnapshotDelta(
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                List.of(new ChatRunSnapshotDelta.ToolDelta("tool-1", "search", null, null, "running", false, false)),
                List.of()));
        accumulator.apply(toolDelta("tool-1", "search", "{\"q\":", null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", "\"x\"}", null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, "result-", "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, "text", "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, null, "complete"));

        assertThat(accumulator.buildSnapshot().tools())
                .containsExactly(
                        new ChatRunSnapshot.ToolCall("tool-1", "search", "{\"q\":\"x\"}", "result-text", "complete"));
    }

    @Test
    void shouldKeepToolOrderWhenLaterDeltasUpdateAnExistingTool() {
        ChatRunSnapshotAccumulator accumulator =
                new ChatRunSnapshotAccumulator(ChatRunSnapshot.empty("run-1", "phase-1", 1));

        accumulator.apply(toolDelta("tool-1", "first", null, null, "running"));
        accumulator.apply(toolDelta("tool-2", "second", null, null, "running"));
        accumulator.apply(toolDelta("tool-1", "first", "{}", null, "running"));

        assertThat(accumulator.buildSnapshot().tools())
                .extracting(ChatRunSnapshot.ToolCall::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }

    private static ChatRunSnapshotDelta toolDelta(
            String toolCallId, String toolCallName, String argsDelta, String resultDelta, String status) {
        return new ChatRunSnapshotDelta(
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                List.of(new ChatRunSnapshotDelta.ToolDelta(
                        toolCallId, toolCallName, argsDelta, resultDelta, status, false, false)),
                List.of());
    }
}
