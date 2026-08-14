package com.lambda.fusion.ai.chat.execution.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionSnapshotTest {

    @Test
    void shouldRedactCommonSecretFieldsFromPersistedToolState() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                "run-1",
                "phase-1",
                1,
                "",
                "",
                null,
                null,
                false,
                false,
                List.of(new ExecutionSnapshot.Tool(
                        "tool-1",
                        "request",
                        "{\"apiKey\":\"secret-value\",\"nested\":{\"token\":\"bearer\"},\"query\":\"ok\"}",
                        "{\"password\":\"hidden\",\"value\":1}",
                        "complete")),
                List.of());

        ExecutionSnapshot.Tool tool = snapshot.tools().getFirst();
        assertThat(tool.args())
                .contains("\"apiKey\":\"***\"")
                .contains("\"token\":\"***\"")
                .contains("ok");
        assertThat(tool.result()).contains("\"password\":\"***\"").contains("\"value\":1");
        assertThat(tool.args()).doesNotContain("secret-value", "bearer");
        assertThat(tool.result()).doesNotContain("hidden");
    }

    @Test
    void shouldPreserveSnapshotJsonShapeAcrossCodecRoundTrip() {
        ExecutionSnapshot original = new ExecutionSnapshot(
                "run-1",
                "phase-1",
                2,
                "answer",
                "reasoning",
                "message-1",
                "reasoning-1",
                true,
                false,
                List.of(new ExecutionSnapshot.Tool("tool-1", "search", "{}", "result", "complete")),
                List.of(new ExecutionSnapshot.Tool("tool-2", "confirm", "ignored", "ignored", "complete")));

        String json = ExecutionSnapshotCodec.encode(original);
        ExecutionSnapshot restored = ExecutionSnapshotCodec.decode(json);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"runId\":\"run-1\"", "\"pendingTools\"");
    }

    @Test
    void shouldRecoverEmptySnapshotFromInvalidJson() {
        ExecutionSnapshot restored = ExecutionSnapshotCodec.decode("{invalid");

        assertThat(restored).isEqualTo(ExecutionSnapshot.empty(null, null, 1));
    }

    @Test
    void shouldIgnoreNullPendingTools() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                "run-1",
                "phase-1",
                1,
                "",
                "",
                null,
                null,
                false,
                false,
                List.of(),
                java.util.Arrays.asList((ExecutionSnapshot.Tool) null));

        assertThat(snapshot.pendingTools()).isEmpty();
    }
}
