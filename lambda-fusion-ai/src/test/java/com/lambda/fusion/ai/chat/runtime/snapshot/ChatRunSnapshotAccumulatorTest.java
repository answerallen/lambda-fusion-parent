package com.lambda.fusion.ai.chat.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.runtime.ChatExecutionSnapshotBuilder;
import io.agentscope.core.agui.event.AguiEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatRunSnapshotAccumulatorTest {

    @Test
    void shouldProjectCompletedToolFromOfficialAguiEvents() {
        ChatExecutionSnapshotBuilder accumulator = accumulator();

        accumulator.apply(List.of(
                new AguiEvent.ToolCallStart("session-1", "phase-1", "tool-1", "search"),
                new AguiEvent.ToolCallArgs("session-1", "phase-1", "tool-1", "{\"q\":"),
                new AguiEvent.ToolCallArgs("session-1", "phase-1", "tool-1", "\"x\"}"),
                new AguiEvent.ToolCallEnd("session-1", "phase-1", "tool-1"),
                new AguiEvent.ToolCallResult("session-1", "phase-1", "tool-1", "result-text", "tool", "reply-1")));

        assertThat(accumulator.buildSnapshot().tools())
                .containsExactly(
                        new ChatRunSnapshot.ToolCall("tool-1", "search", "{\"q\":\"x\"}", "result-text", "complete"));
    }

    @Test
    void shouldKeepToolOrderWhenExistingToolReceivesMoreArguments() {
        ChatExecutionSnapshotBuilder accumulator = accumulator();

        accumulator.apply(List.of(
                new AguiEvent.ToolCallStart("session-1", "phase-1", "tool-1", "first"),
                new AguiEvent.ToolCallStart("session-1", "phase-1", "tool-2", "second"),
                new AguiEvent.ToolCallArgs("session-1", "phase-1", "tool-1", "{}")));

        assertThat(accumulator.buildSnapshot().tools())
                .extracting(ChatRunSnapshot.ToolCall::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }

    @Test
    void shouldProjectPendingConfirmationFromInterruptOutcome() {
        ChatExecutionSnapshotBuilder accumulator = accumulator();
        AguiEvent.Interrupt interrupt = new AguiEvent.Interrupt(
                "tool-1",
                "human_confirmation_required",
                "confirm",
                "tool-1",
                null,
                null,
                Map.of("toolName", "dangerous"));

        accumulator.apply(List.of(new AguiEvent.RunFinished(
                "session-1", "phase-1", null, new AguiEvent.RunFinishedInterruptOutcome(List.of(interrupt)))));

        assertThat(accumulator.buildSnapshot().pendingTools())
                .containsExactly(new ChatRunSnapshot.ToolCall("tool-1", "dangerous", "", "", "asking"));
    }

    private static ChatExecutionSnapshotBuilder accumulator() {
        return new ChatExecutionSnapshotBuilder(ChatRunSnapshot.empty("run-1", "phase-1", 1));
    }
}
