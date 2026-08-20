package com.lambda.fusion.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** 验证 Fusion 记忆模型边界能阻止 AgentScope 提交截断的 consolidation 结果。 */
class MemoryModelGuardFeatureTest {

    @Test
    void lengthDoesNotOverwriteMemoryOrAdvanceWatermark(@TempDir Path workspace) throws Exception {
        RuntimeContext context = RuntimeContext.empty();
        LocalFilesystem filesystem = new LocalFilesystem(workspace);
        try (WorkspaceManager manager = new WorkspaceManager(workspace, filesystem)) {
            manager.writeUtf8WorkspaceRelative(context, "MEMORY.md", "# original memory");
            manager.writeUtf8WorkspaceRelative(context, "memory/2026-08-20.md", "durable new fact");
            MemoryModelGuard guard = new MemoryModelGuard(new LengthModel(), 32768, Duration.ofMinutes(1));
            MemoryConsolidator consolidator = new MemoryConsolidator(manager, guard);

            StepVerifier.create(consolidator.consolidate(context))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("incomplete finish reason: length"))
                    .verify();

            assertThat(manager.readManagedWorkspaceFileUtf8(context, "MEMORY.md"))
                    .isEqualTo("# original memory");
            String watermark = manager.readManagedWorkspaceFileUtf8(context, "memory/.consolidation_state");
            assertThat(watermark == null || watermark.isBlank()).isTrue();
        }
    }

    private static final class LengthModel implements Model {

        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.just(ChatResponse.builder()
                    .content(List.of(
                            TextBlock.builder().text("# truncated replacement").build()))
                    .finishReason("length")
                    .build());
        }

        @Override
        public String getModelName() {
            return "length-model";
        }
    }
}
