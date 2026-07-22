package com.lambda.fusion.ai.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.ChannelManager;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 验证 {@link HarnessGateway} 装配：按 agentId 路由 + 同 canonicalKey 并发串行。用 {@link StubModel} 免真实 LLM。
 *
 * @author Jin
 */
class GatewayRoutingTest {

    private static final String CHANNEL_ID = "fusion-chat";

    @Test
    void routesByAgentId() {
        StubModel modelA = new StubModel("hello-A");
        StubModel modelB = new StubModel("hello-B");
        HarnessGateway gw = HarnessGateway.create(new ChannelManager());
        gw.registerAgent("app:A:t:T1", buildAgent("app:A:t:T1", modelA));
        gw.registerAgent("app:B:t:T1", buildAgent("app:B:t:T1", modelB));

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("hi").build();
        String replyA = collectText(gw.runStream(ctxFor("app:A:t:T1", "sess-A"), List.of(userMsg), outbound("sess-A")));
        String replyB = collectText(gw.runStream(ctxFor("app:B:t:T1", "sess-B"), List.of(userMsg), outbound("sess-B")));

        assertThat(replyA).contains("hello-A");
        assertThat(replyB).contains("hello-B");
    }

    @Test
    void serializesSameKeyTurns() {
        StubModel model = new StubModel("reply");
        HarnessGateway gw = HarnessGateway.create(new ChannelManager());
        gw.registerAgent("app:S:t:T1", buildAgent("app:S:t:T1", model));

        MsgContext ctx = ctxFor("app:S:t:T1", "sess-S");
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("hi").build();

        Flux<AgentEvent> run1 = gw.runStream(ctx, List.of(userMsg), outbound("sess-S"));
        Flux<AgentEvent> run2 = gw.runStream(ctx, List.of(userMsg), outbound("sess-S"));
        Flux.merge(run1, run2).blockLast();

        assertThat(model.maxActive()).isLessThanOrEqualTo(1);
    }

    private static MsgContext ctxFor(String agentId, String sessionId) {
        return new MsgContext(CHANNEL_ID, "T1", sessionId, null, null, Map.of("agentId", agentId), "user-1");
    }

    private static OutboundAddress outbound(String sessionId) {
        return OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + sessionId);
    }

    /** 构建最小 CHAT 型 agent（镜像生产 {@code AiAgentFactory.buildChat} 的关闭项）。 */
    private static HarnessAgent buildAgent(String agentId, StubModel model) {
        return HarnessAgent.builder()
                .agentId(agentId)
                .name("test-" + agentId)
                .sysPrompt("You are a test assistant.")
                .model(model)
                .maxIters(1)
                .stateStore(new InMemoryAgentStateStore())
                .disableFilesystemTools()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableCompaction()
                .disableToolResultEviction()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableSubagents()
                .disableToolsConfig()
                .disableSessionPersistence()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }

    private static String collectText(Flux<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        events.toStream().forEach(e -> {
            if (e instanceof TextBlockDeltaEvent delta) {
                sb.append(delta.getDelta());
            }
        });
        return sb.toString();
    }
}
