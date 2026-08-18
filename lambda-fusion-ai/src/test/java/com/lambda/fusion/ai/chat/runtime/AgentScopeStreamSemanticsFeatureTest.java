package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

/**
 * 特征测试：用真实 {@link HarnessAgent}（内部包装真实 {@code ReActAgent}）+ 桩 {@link Model}， 固定 AgentScope 2.0.0
 * 已确认的流式语义。
 *
 * <p>固定的语义（与 agentscope-core / agentscope-harness 2.0.0 源码核对）：
 *
 * <ol>
 *   <li>{@code ReActAgent#buildAgentStream} 在 {@code doFinally} 中先发根 {@code AgentEndEvent} 再
 *       {@code complete()}，因此根 AGENT_END 严格早于整条 Flux 的 complete 信号。
 *   <li>{@code MemoryFlushMiddleware#onAgent} 用 {@code next.apply(input).concatWith(flush)} 拼接记忆冲刷，
 *       因此记忆模型调用发生在根 AGENT_END 之后、整条流 complete 之前（冲刷本身不产生事件）。
 *   <li>正常完成后 {@code (userId, sessionId)} 槽位的状态已持久化到 {@code AgentStateStore}。
 * </ol>
 */
class AgentScopeStreamSemanticsFeatureTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path workspace;

    @Test
    void rootAgentEndIsLastEventBeforeStreamCompletion() {
        RecordingModel mainModel = new RecordingModel("最终答复");
        RecordingModel memoryModel = new RecordingModel("NO_REPLY");
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore();

        try (HarnessAgent agent = newAgent(mainModel, memoryModel, stateStore)) {
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId("u-feature-end")
                    .sessionId("s-feature-end")
                    .build();

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicReference<AgentEvent> lastSeen = new AtomicReference<>();
            List<AgentEvent> events = agent.streamEvents(new UserMessage("你好"), ctx)
                    .doOnNext(lastSeen::set)
                    .doOnComplete(() -> completed.set(true))
                    .collectList()
                    .block(TIMEOUT);

            // collectList 返回即代表流已 complete
            assertThat(completed).isTrue();
            assertThat(events).isNotEmpty();

            // 末位事件是根 AGENT_END（source == null 表示根 Agent，而非子 Agent）
            AgentEvent last = events.get(events.size() - 1);
            assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
            assertThat(last.getSource()).isNull();
            // complete 前最后一个 onNext 就是该根 AGENT_END
            assertThat(lastSeen.get()).isSameAs(last);

            // 恰好一个根 AGENT_END，且 AGENT_RESULT 紧邻其前
            assertThat(events.stream().filter(e -> e.getType() == AgentEventType.AGENT_END && e.getSource() == null))
                    .hasSize(1);
            assertThat(events.get(events.size() - 2).getType()).isEqualTo(AgentEventType.AGENT_RESULT);

            // 桩模型只返回纯文本（无 ToolUseBlock），ReAct 循环一轮即正常结束
            assertThat(mainModel.invocations()).isEqualTo(1);
        }
    }

    @Test
    void memoryFlushRunsAfterRootAgentEndAndBeforeStreamCompletion() {
        RecordingModel mainModel = new RecordingModel("最终答复");
        RecordingModel memoryModel = new RecordingModel("提取到一条记忆");
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore();

        try (HarnessAgent agent = newAgent(mainModel, memoryModel, stateStore)) {
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId("u-feature-flush")
                    .sessionId("s-feature-flush")
                    .build();

            AtomicLong rootAgentEndAt = new AtomicLong(-1);
            List<AgentEvent> events = agent.streamEvents(new UserMessage("记住：我喜欢咖啡"), ctx)
                    .doOnNext(e -> {
                        if (e.getType() == AgentEventType.AGENT_END && e.getSource() == null) {
                            rootAgentEndAt.set(System.nanoTime());
                        }
                    })
                    .collectList()
                    .block(TIMEOUT);

            // 记忆尾部确实执行了：独立桩记忆模型被调用（证明 flush / maintenance 发生）
            assertThat(memoryModel.invocations()).isGreaterThanOrEqualTo(1);
            // concatWith 语义：记忆模型调用严格发生在根 AGENT_END 发出之后
            assertThat(rootAgentEndAt.get()).isPositive();
            assertThat(memoryModel.firstInvocationAt()).isGreaterThan(rootAgentEndAt.get());

            // 冲刷不产生事件：collectList 完成（流 complete）时，末位事件仍是根 AGENT_END
            assertThat(events).isNotEmpty();
            AgentEvent last = events.get(events.size() - 1);
            assertThat(last.getType()).isEqualTo(AgentEventType.AGENT_END);
            assertThat(last.getSource()).isNull();
        }
    }

    @Test
    void stateIsSavedAfterNormalCompletion() {
        RecordingModel mainModel = new RecordingModel("最终答复");
        RecordingModel memoryModel = new RecordingModel("NO_REPLY");
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore();
        String userId = "u-feature-state";
        String sessionId = "s-feature-state";

        try (HarnessAgent agent = newAgent(mainModel, memoryModel, stateStore)) {
            RuntimeContext ctx =
                    RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
            List<AgentEvent> events =
                    agent.streamEvents(new UserMessage("你好"), ctx).collectList().block(TIMEOUT);

            assertThat(events).isNotEmpty();
            assertThat(events.get(events.size() - 1).getType()).isEqualTo(AgentEventType.AGENT_END);
            // 正常完成后 (userId, sessionId) 槽位状态已保存
            assertThat(stateStore.exists(userId, sessionId)).isTrue();
        }
    }

    /**
     * 构建最小 HarnessAgent：真实 {@code ReActAgent} 委托 + 独立桩记忆模型； 关闭与语义无关的周边能力
     * （工具、压缩、技能、子 Agent 等），仅保留记忆钩子以固定 AGENT_END / flush 顺序。 镜像生产关闭项见 {@code
     * GatewayRoutingTest}。
     */
    private HarnessAgent newAgent(Model mainModel, Model memoryModel, InMemoryAgentStateStore stateStore) {
        return HarnessAgent.builder()
                .agentId("feature-test-agent")
                .name("feature-test-agent")
                .sysPrompt("You are a test assistant.")
                .model(mainModel)
                .workspace(workspace)
                .stateStore(stateStore)
                .memory(MemoryConfig.builder()
                        .model(memoryModel)
                        .flushTrigger(MemoryConfig.FlushTrigger.always())
                        .build())
                .disableFilesystemTools()
                .disableShellTool()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableCompaction()
                .disableToolResultEviction()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableToolsConfig()
                .disableMemoryTools()
                .build();
    }

    /**
     * 桩 {@link Model}：返回单个 {@code finishReason="stop"}、仅含 {@link TextBlock} 的 {@link ChatResponse}；
     * 记录调用次数与首次调用的 {@code nanoTime}，用于固定事件顺序。
     */
    private static final class RecordingModel implements Model {

        private final String response;
        private final AtomicInteger invocations = new AtomicInteger(0);
        private final AtomicLong firstInvocationAt = new AtomicLong(-1);

        RecordingModel(String response) {
            this.response = response;
        }

        int invocations() {
            return invocations.get();
        }

        long firstInvocationAt() {
            return firstInvocationAt.get();
        }

        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.defer(() -> {
                invocations.incrementAndGet();
                firstInvocationAt.compareAndSet(-1, System.nanoTime());
                return Flux.just(ChatResponse.builder()
                        .id("msg_" + UUID.randomUUID())
                        .content(List.of(TextBlock.builder().text(response).build()))
                        .usage(new ChatUsage(1, 1, 2))
                        .finishReason("stop")
                        .build());
            });
        }

        @Override
        public String getModelName() {
            return "recording-stub-model";
        }
    }
}
