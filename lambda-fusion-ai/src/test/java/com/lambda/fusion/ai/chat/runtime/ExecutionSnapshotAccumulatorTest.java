package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.runtime.model.ExecutionSnapshotDelta;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionSnapshotAccumulatorTest {

    @Test
    void shouldAccumulateCompletedToolSnapshot() {
        ChatRunSnapshotAccumulator accumulator =
                new ChatRunSnapshotAccumulator(ExecutionSnapshot.empty("run-1", "phase-1", 1));

        accumulator.apply(new ExecutionSnapshotDelta(
                null,
                null,
                null,
                null,
                false,
                false,
                true,
                List.of(new ExecutionSnapshotDelta.ToolDelta("tool-1", "search", null, null, "running", false, false)),
                List.of()));
        accumulator.apply(toolDelta("tool-1", "search", "{\"q\":", null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", "\"x\"}", null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, null, "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, "result-", "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, "text", "running"));
        accumulator.apply(toolDelta("tool-1", "search", null, null, "complete"));

        assertThat(accumulator.buildSnapshot().tools())
                .containsExactly(
                        new ExecutionSnapshot.Tool("tool-1", "search", "{\"q\":\"x\"}", "result-text", "complete"));
    }

    @Test
    void shouldKeepToolOrderWhenLaterDeltasUpdateAnExistingTool() {
        ChatRunSnapshotAccumulator accumulator =
                new ChatRunSnapshotAccumulator(ExecutionSnapshot.empty("run-1", "phase-1", 1));

        accumulator.apply(toolDelta("tool-1", "first", null, null, "running"));
        accumulator.apply(toolDelta("tool-2", "second", null, null, "running"));
        accumulator.apply(toolDelta("tool-1", "first", "{}", null, "running"));

        assertThat(accumulator.buildSnapshot().tools())
                .extracting(ExecutionSnapshot.Tool::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }

    private static ExecutionSnapshotDelta toolDelta(
            String toolCallId, String toolCallName, String argsDelta, String resultDelta, String status) {
        return new ExecutionSnapshotDelta(
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                List.of(new ExecutionSnapshotDelta.ToolDelta(
                        toolCallId, toolCallName, argsDelta, resultDelta, status, false, false)),
                List.of());
    }
}
