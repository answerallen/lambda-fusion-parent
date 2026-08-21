package com.lambda.fusion.ai.chat.runtime.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRunSnapshotTest {

    @Test
    void shouldRedactCommonSecretFieldsFromPersistedToolState() {
        ChatRunSnapshot snapshot = new ChatRunSnapshot(
                "run-1",
                "phase-1",
                1,
                "",
                "",
                null,
                null,
                false,
                false,
                List.of(new ChatRunSnapshot.ToolCall(
                        "tool-1",
                        "request",
                        "{\"apiKey\":\"secret-value\",\"nested\":{\"token\":\"bearer\"},\"query\":\"ok\"}",
                        "{\"password\":\"hidden\",\"value\":1}",
                        "complete")),
                List.of());

        ChatRunSnapshot.ToolCall tool = snapshot.tools().getFirst();
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
        ChatRunSnapshot original = new ChatRunSnapshot(
                "run-1",
                "phase-1",
                2,
                "answer",
                "reasoning",
                "message-1",
                "reasoning-1",
                true,
                false,
                List.of(new ChatRunSnapshot.ToolCall("tool-1", "search", "{}", "result", "complete")),
                List.of(new ChatRunSnapshot.ToolCall("tool-2", "confirm", "ignored", "ignored", "complete")));

        String json = ChatRunSnapshotCodec.encode(original);
        ChatRunSnapshot restored = ChatRunSnapshotCodec.decode(json);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("\"runId\":\"run-1\"", "\"pendingTools\"");
    }

    @Test
    void shouldRecoverEmptySnapshotFromInvalidJson() {
        ChatRunSnapshot restored = ChatRunSnapshotCodec.decode("{invalid");

        assertThat(restored).isEqualTo(ChatRunSnapshot.empty(null, null, 1));
    }

    @Test
    void shouldIgnoreNullPendingTools() {
        ChatRunSnapshot snapshot = new ChatRunSnapshot(
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
                java.util.Arrays.asList((ChatRunSnapshot.ToolCall) null));

        assertThat(snapshot.pendingTools()).isEmpty();
    }
}
