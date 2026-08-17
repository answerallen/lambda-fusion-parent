package com.lambda.fusion.ai.chat.runtime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.runtime.gateway.FusionSubagentGateway;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.sandbox.AbstractSandboxFilesystem;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.SessionIdUtils;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import io.agentscope.harness.agent.sandbox.SandboxExecutionGuard;
import io.agentscope.harness.agent.sandbox.SandboxIsolationKey;
import io.agentscope.harness.agent.sandbox.SandboxLease;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/**
 * Agent 执行适配器测试。
 *
 * @author Jin
 */
class AgentExecutionAdapterTest {

    @Test
    void shouldUseConversationSessionInDirectMode() {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class))).thenReturn(Flux.empty());
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithAskingBlock());
        AgentExecutionAdapter adapter = new AgentExecutionAdapter(agent, null, "agent-1", run(), session(), "tenant-1");
        Msg message = userMessage();

        adapter.stream(message).blockLast();
        adapter.stream(message).blockLast();

        ArgumentCaptor<RuntimeContext> contextCaptor = ArgumentCaptor.forClass(RuntimeContext.class);
        verify(agent, times(2)).streamEvents(any(Msg.class), contextCaptor.capture());
        List<RuntimeContext> contexts = contextCaptor.getAllValues();
        assertThat(contexts).allSatisfy(context -> {
            assertThat(context.getSessionId()).isEqualTo("session-1");
            assertThat(context.getUserId()).isEqualTo("user-1");
            assertThat(context.<String>get(RuntimeProperty.KEY_TENANT_ID)).isEqualTo("tenant-1");
        });
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
        assertThat(adapter.readAskingToolBlocks())
                .extracting(ToolUseBlock::getId)
                .containsExactly("call-1");

        adapter.interrupt();

        verify(delegate).interrupt("user-1", "session-1");
    }

    @Test
    void shouldUseGatewayRoutingSessionInGatewayMode() {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(gateway.runStream(any(MsgContext.class), anyList(), any(OutboundAddress.class)))
                .thenReturn(Flux.<AgentEvent>empty());
        // 路由标识必须使用 AgentFactory 注册进网关的稳定键，getAgentId() 是随机实例 UUID（本测试不调用）。
        AgentExecutionAdapter adapter =
                new AgentExecutionAdapter(agent, gateway, "agent-1", run(), session(), "tenant-1");

        adapter.stream(userMessage()).blockLast();

        ArgumentCaptor<MsgContext> contextCaptor = ArgumentCaptor.forClass(MsgContext.class);
        verify(gateway).runStream(contextCaptor.capture(), anyList(), any(OutboundAddress.class));
        MsgContext context = contextCaptor.getValue();
        assertThat(context.channel()).isEqualTo("fusion-chat");
        assertThat(context.group()).isEqualTo("tenant-1");
        assertThat(context.room()).isEqualTo("session-1");
        assertThat(context.userId()).isEqualTo("user-1");
        assertThat(context.extra())
                .containsEntry(RuntimeProperty.KEY_AGENT_ID, "agent-1")
                .containsEntry(RuntimeProperty.KEY_APP_ID, "app-1")
                .containsEntry(RuntimeProperty.KEY_LF_SESSION_ID, "session-1")
                .containsEntry(RuntimeProperty.KEY_TENANT_ID, "tenant-1");

        String stateSessionId = "gw-" + SessionIdUtils.deterministicHash(context.canonicalKey());
        when(delegate.getAgentState("user-1", stateSessionId)).thenReturn(stateWithAskingBlock());
        assertThat(adapter.readAskingToolBlocks())
                .extracting(ToolUseBlock::getId)
                .containsExactly("call-1");

        adapter.interrupt();

        verify(delegate).interrupt("user-1", stateSessionId);
    }

    @Test
    void shouldCompleteSubagentExposureRecordFromTheBusinessConversation() {
        HarnessAgent agent = mock(HarnessAgent.class);
        HarnessGateway gateway = mock(HarnessGateway.class);
        FusionSubagentGateway subagentGateway = mock(FusionSubagentGateway.class);
        SubagentExposedEvent event = new SubagentExposedEvent("sub-1", "worker", "child-session", "Worker");
        when(gateway.runStream(any(MsgContext.class), anyList(), any(OutboundAddress.class)))
                .thenReturn(Flux.just(event));
        AgentExecutionAdapter adapter =
                new AgentExecutionAdapter(agent, gateway, "agent-1", run(), session(), "tenant-1", subagentGateway);

        adapter.stream(userMessage()).blockLast();

        ArgumentCaptor<MsgContext> contextCaptor = ArgumentCaptor.forClass(MsgContext.class);
        verify(gateway).runStream(contextCaptor.capture(), anyList(), any(OutboundAddress.class));
        String parentSessionId = "gw-"
                + SessionIdUtils.deterministicHash(contextCaptor.getValue().canonicalKey());
        verify(subagentGateway)
                .recordExposure(eq(event), eq("app-1"), eq("tenant-1"), eq("user-1"), eq(parentSessionId));
    }

    @Test
    void shouldHoldDistributedWorkspaceLockUntilDirectStreamCompletes() throws Exception {
        HarnessAgent agent = mock(HarnessAgent.class);
        DistributedStore distributedStore = mock(DistributedStore.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        AbstractFilesystem filesystem = mock(AbstractFilesystem.class);
        SandboxExecutionGuard executionGuard = mock(SandboxExecutionGuard.class);
        SandboxLease lease = mock(SandboxLease.class);
        when(agent.getDistributedStore()).thenReturn(distributedStore);
        when(agent.getWorkspaceManager()).thenReturn(workspaceManager);
        when(workspaceManager.getFilesystem()).thenReturn(filesystem);
        when(distributedStore.sandboxExecutionGuard()).thenReturn(executionGuard);
        when(executionGuard.tryEnter(any(SandboxIsolationKey.class))).thenReturn(lease);
        when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class))).thenReturn(Flux.empty());
        AgentExecutionAdapter adapter = new AgentExecutionAdapter(agent, null, "agent-1", run(), session(), "tenant-1");

        adapter.stream(userMessage()).blockLast();

        verify(executionGuard).tryEnter(any(SandboxIsolationKey.class));
        verify(lease).close();
    }

    @Test
    void shouldLetSandboxManagerOwnDistributedWorkspaceLock() {
        HarnessAgent agent = mock(HarnessAgent.class);
        DistributedStore distributedStore = mock(DistributedStore.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        AbstractSandboxFilesystem filesystem = mock(AbstractSandboxFilesystem.class);
        when(agent.getDistributedStore()).thenReturn(distributedStore);
        when(agent.getWorkspaceManager()).thenReturn(workspaceManager);
        when(workspaceManager.getFilesystem()).thenReturn(filesystem);
        when(agent.streamEvents(any(Msg.class), any(RuntimeContext.class))).thenReturn(Flux.empty());
        AgentExecutionAdapter adapter = new AgentExecutionAdapter(agent, null, "agent-1", run(), session(), "tenant-1");

        adapter.stream(userMessage()).blockLast();

        verify(distributedStore, never()).sandboxExecutionGuard();
    }

    @Test
    void shouldDenyPendingToolCallsAndSaveState() {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agent.getName()).thenReturn("demo-agent");
        AgentState state = stateWithAskingBlock();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);
        AgentExecutionAdapter adapter = new AgentExecutionAdapter(agent, null, "agent-1", run(), session(), "tenant-1");

        adapter.denyPendingToolCalls();

        List<Msg> context = state.getContext();
        assertThat(context).hasSize(2);
        Msg deniedMsg = context.get(1);
        assertThat(deniedMsg.getRole()).isEqualTo(MsgRole.TOOL);
        ToolResultBlock denied =
                deniedMsg.getContentBlocks(ToolResultBlock.class).getFirst();
        assertThat(denied.getId()).isEqualTo("call-1");
        assertThat(denied.getName()).isEqualTo("demo_tool");
        assertThat(denied.getState()).isEqualTo(ToolResultState.DENIED);
        verify(delegate).saveAgentState("user-1", "session-1");
    }

    @Test
    void shouldSkipDenyWhenNoPendingToolCalls() {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        ToolUseBlock resolved = ToolUseBlock.builder()
                .id("call-1")
                .name("demo_tool")
                .state(ToolCallState.ALLOWED)
                .build();
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(resolved)))
                .build();
        Msg result = Msg.builderForRole(MsgRole.TOOL)
                .content(ToolResultBlock.text("done").withIdAndName("call-1", "demo_tool"))
                .build();
        AgentState state =
                AgentState.builder().context(List.of(assistant, result)).build();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);
        AgentExecutionAdapter adapter = new AgentExecutionAdapter(agent, null, "agent-1", run(), session(), "tenant-1");

        adapter.denyPendingToolCalls();

        assertThat(state.getContext()).hasSize(2);
        verify(delegate, never()).saveAgentState("user-1", "session-1");
    }

    private static ChatRunEntity run() {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        return run;
    }

    private static ChatSessionEntity session() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setAppId("app-1");
        session.setUserId("user-1");
        session.setTenantId("tenant-1");
        return session;
    }

    private static Msg userMessage() {
        return Msg.builderForRole(MsgRole.USER).textContent("hello").build();
    }

    private static AgentState stateWithAskingBlock() {
        ToolUseBlock block = ToolUseBlock.builder()
                .id("call-1")
                .name("demo_tool")
                .state(ToolCallState.ASKING)
                .build();
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(block)))
                .build();
        return AgentState.builder().context(List.of(assistant)).build();
    }
}
