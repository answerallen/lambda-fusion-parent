package com.lambda.fusion.ai.chat.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunSnapshotTest {

    @Test
    void shouldKeepToolOrderWhenLaterDeltasUpdateAnExistingTool() {
        RunSnapshot.Accumulator accumulator = new RunSnapshot.Accumulator(RunSnapshot.empty("run-1", "phase-1", 1));
        accumulator.startTool("tool-1", "first");
        accumulator.startTool("tool-2", "second");
        accumulator.appendToolArgs("tool-1", "first", "{}");

        assertThat(accumulator.snapshot().tools())
                .extracting(RunSnapshot.Tool::toolCallId)
                .containsExactly("tool-1", "tool-2");
    }

    @Test
    void shouldRedactCommonSecretFieldsFromPersistedToolState() {
        RunSnapshot snapshot = new RunSnapshot(
                "run-1",
                "phase-1",
                1,
                "",
                "",
                null,
                null,
                false,
                false,
                List.of(new RunSnapshot.Tool(
                        "tool-1",
                        "request",
                        "{\"apiKey\":\"secret-value\",\"nested\":{\"token\":\"bearer\"},\"query\":\"ok\"}",
                        "{\"password\":\"hidden\",\"value\":1}",
                        "complete")),
                List.of());

        RunSnapshot.Tool tool = snapshot.tools().getFirst();
        assertThat(tool.args())
                .contains("\"apiKey\":\"***\"")
                .contains("\"token\":\"***\"")
                .contains("ok");
        assertThat(tool.result()).contains("\"password\":\"***\"").contains("\"value\":1");
        assertThat(tool.args()).doesNotContain("secret-value", "bearer");
        assertThat(tool.result()).doesNotContain("hidden");
    }
}
