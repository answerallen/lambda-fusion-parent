package com.lambda.fusion.ai.chat.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunBootstrapEncoderTest {

    @Test
    void shouldBuildCompleteBootstrapWithoutCommittedSequenceNumbers() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        RunSnapshot snapshot = new RunSnapshot(
                run.getId(),
                run.getAguiRunId(),
                2,
                "answer",
                "thought",
                "text-1",
                "reasoning-1",
                true,
                false,
                List.of(new RunSnapshot.Tool("tool-1", "search", "{q:1}", "result", "complete")),
                List.of());

        List<String> events = RunBootstrapEncoder.encode(run, snapshot, 17);

        assertThat(events.getFirst())
                .contains("\"type\":\"RUN_STARTED\"")
                .contains("\"bootstrap\":true")
                .contains("\"bootstrapSeq\":17")
                .contains("\"phaseNo\":2")
                .doesNotContain("\"seq\":");
        assertThat(events)
                .anyMatch(event -> event.contains("\"type\":\"TEXT_MESSAGE_CONTENT\"") && event.contains("answer"));
        assertThat(events)
                .anyMatch(event -> event.contains("\"type\":\"REASONING_START\"")
                        && event.contains("\"messageId\":\"reasoning-1\""));
        assertThat(events)
                .anyMatch(event -> event.contains("\"type\":\"REASONING_END\"")
                        && event.contains("\"messageId\":\"reasoning-1\""));
        assertThat(events)
                .anyMatch(event -> event.contains("\"type\":\"TOOL_CALL_RESULT\"") && event.contains("result"));
        assertThat(events)
                .anyMatch(event -> event.contains("\"type\":\"TEXT_MESSAGE_START\"")
                        && event.contains("\"messageId\":\"text-1\""));
        assertThat(events).noneMatch(event -> event.contains("\"type\":\"RAW\""));
    }

    @Test
    void shouldRestoreAwaitingConfirmationAsInterrupt() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        RunSnapshot snapshot = new RunSnapshot(
                run.getId(),
                run.getAguiRunId(),
                2,
                "partial",
                "",
                "text-1",
                null,
                false,
                false,
                List.of(),
                List.of(new RunSnapshot.Tool("tool-1", "dangerous", "", "", "asking")));

        assertThat(RunBootstrapEncoder.encode(run, snapshot, 9))
                .anyMatch(event -> event.contains("\"type\":\"RUN_FINISHED\"")
                        && event.contains("\"type\":\"interrupt\"")
                        && event.contains("tool-1"));
    }

    @Test
    void shouldLeaveInProgressToolOpenForFollowingDeltas() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        RunSnapshot snapshot = new RunSnapshot(
                run.getId(),
                run.getAguiRunId(),
                2,
                "",
                "",
                null,
                null,
                false,
                false,
                List.of(new RunSnapshot.Tool("tool-1", "search", "{\"q\":", "", "running")),
                List.of());

        List<String> events = RunBootstrapEncoder.encode(run, snapshot, 10);

        assertThat(events).anyMatch(event -> event.contains("\"type\":\"TOOL_CALL_ARGS\""));
        assertThat(events).noneMatch(event -> event.contains("\"type\":\"TOOL_CALL_END\""));
    }

    private static ChatRunEntity run(ChatRunStatus status) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setAguiRunId("phase-2");
        run.setPhaseNo(2);
        run.setStatus(status.name());
        return run;
    }
}
