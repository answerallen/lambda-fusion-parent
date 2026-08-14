package com.lambda.fusion.ai.chat.execution.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import org.junit.jupiter.api.Test;

class ExecutionSnapshotAccumulatorTest {

    @Test
    void shouldAccumulateCompletedToolSnapshot() {
        ExecutionSnapshotAccumulator accumulator =
                new ExecutionSnapshotAccumulator(ExecutionSnapshot.empty("run-1", "phase-1", 1));

        accumulator.startTool("tool-1", "search");
        accumulator.appendToolArgs("tool-1", "search", "{\"q\":");
        accumulator.appendToolArgs("tool-1", "search", "\"x\"}");
        accumulator.finishToolArgs("tool-1", "search");
        accumulator.appendToolResult("tool-1", "search", "result-");
        accumulator.appendToolResult("tool-1", "search", "text");
        accumulator.finishTool("tool-1", "search");

        assertThat(accumulator.snapshot().tools())
                .containsExactly(
                        new ExecutionSnapshot.Tool("tool-1", "search", "{\"q\":\"x\"}", "result-text", "complete"));
    }

    @Test
    void shouldKeepToolOrderWhenLaterDeltasUpdateAnExistingTool() {
        ExecutionSnapshotAccumulator accumulator =
                new ExecutionSnapshotAccumulator(ExecutionSnapshot.empty("run-1", "phase-1", 1));
        accumulator.startTool("tool-1", "first");
        accumulator.startTool("tool-2", "second");
        accumulator.appendToolArgs("tool-1", "first", "{}");

        assertThat(accumulator.snapshot().tools())
                .extracting(ExecutionSnapshot.Tool::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }
}
