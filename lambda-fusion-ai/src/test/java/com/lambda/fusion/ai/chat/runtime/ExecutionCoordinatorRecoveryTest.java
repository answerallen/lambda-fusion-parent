package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ExecutionCoordinatorRecoveryTest {

    private final ChatRunStateService runService = mock(ChatRunStateService.class);
    private final ChatRunEventStore eventStore = mock(ChatRunEventStore.class);
    private final AgentFactory agentFactory = mock(AgentFactory.class);
    private final WorkspaceAuditRecorder workspaceAuditRecorder = mock(WorkspaceAuditRecorder.class);
    private ChatRunCoordinator coordinator;
    private ChatRunStartupRecovery startupRecovery;

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void shouldRetainAwaitingConfirmationWithPersistentStateStore() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        prepareRecovery(run, "FILE");

        startupRecovery.recoverOnStartup();

        verify(eventStore).initialize("run-1", 7L);
        verify(runService, never()).finalizeExecution(any(), any());
        verifyNoInteractions(agentFactory);
        assertThat(run.getStatus()).isEqualTo(ChatRunStatus.AWAITING_CONFIRM.name());
    }

    @Test
    void shouldFailAwaitingConfirmationWithMemoryStateStore() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        prepareRecovery(run, "MEMORY");
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        FinalizeCommand command = captureFinalizeCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo("CONFIRM_CONTEXT_UNAVAILABLE");
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，内存中的用户确认上下文已丢失");
    }

    @Test
    void shouldFailRunningEvenWithPersistentStateStore() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        prepareRecovery(run, "REDIS");
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        FinalizeCommand command = captureFinalizeCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo("INSTANCE_LOST");
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，对话运行已终止");
    }

    @Test
    void shouldClosePendingToolCallsWhenFailingInterruptedRun() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        prepareRecovery(run, "REDIS");
        ReActAgent delegate = stubAgentWithAskingState();
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        FinalizeCommand command = captureFinalizeCommand(run);
        assertThat(command.errorCode()).isEqualTo("INSTANCE_LOST");
        verify(delegate).saveAgentState("user-1", "session-1");
    }

    @Test
    void shouldClosePendingToolCallsWhenStoppingAwaitingConfirmRun() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        prepareRecovery(run, "MEMORY");
        ReActAgent delegate = stubAgentWithAskingState();
        when(runService.requestStopping(run)).thenReturn(true);
        stubTerminalCommit(run);

        coordinator.stop(run, session());

        verify(delegate).saveAgentState("user-1", "session-1");
        verify(runService).finalizeExecution(eq(run), any(FinalizeCommand.class));
    }

    private void prepareRecovery(ChatRunEntity run, String stateStoreType) {
        ChatSessionEntity session = session();
        when(runService.listInterruptedOnRestart()).thenReturn(List.of(run));
        when(runService.listCreated()).thenReturn(List.of());
        when(runService.loadSession(run)).thenReturn(session);

        AiProperties properties = new AiProperties();
        properties.getStateStore().setType(stateStoreType);
        ChatRunInstanceFactory instanceFactory = new ChatRunInstanceFactory(
                runService, eventStore, agentFactory, workspaceAuditRecorder, gatewayProvider(), properties);
        coordinator = new ChatRunCoordinator(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                instanceFactory,
                properties);
        startupRecovery = new ChatRunStartupRecovery(runService, coordinator);
    }

    private void stubTerminalCommit(ChatRunEntity run) {
        when(runService.finalizeExecution(eq(run), any(FinalizeCommand.class))).thenAnswer(invocation -> {
            FinalizeCommand command = invocation.getArgument(1);
            return new FinalizeResult(
                    true,
                    command.targetStatus().name(),
                    command.finishReason(),
                    command.errorCode(),
                    command.errorMessage());
        });
        when(eventStore.appendTerminalIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ChatRunEvent(8L, "terminal-8", "RUN_FINISHED", "{}"));
    }

    /** 注册带 ASKING 待确认工具调用的 Agent，返回 delegate 供校验状态闭合调用。 */
    private ReActAgent stubAgentWithAskingState() {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agent.getName()).thenReturn("demo-agent");
        ToolUseBlock block = ToolUseBlock.builder()
                .id("call-1")
                .name("demo_tool")
                .state(ToolCallState.ASKING)
                .build();
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(block)))
                .build();
        when(delegate.getAgentState("user-1", "session-1"))
                .thenReturn(AgentState.builder().context(List.of(assistant)).build());
        when(agentFactory.getOrBuild("app-1", "tenant-1")).thenReturn(agent);
        return delegate;
    }

    private FinalizeCommand captureFinalizeCommand(ChatRunEntity run) {
        ArgumentCaptor<FinalizeCommand> captor = ArgumentCaptor.forClass(FinalizeCommand.class);
        verify(runService).finalizeExecution(eq(run), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<HarnessGateway> gatewayProvider() {
        return mock(ObjectProvider.class);
    }

    private static ChatRunEntity run(ChatRunStatus status) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        run.setPhaseNo(1);
        run.setAguiRunId("agui-1");
        run.setSnapshotSeq(7L);
        run.setSnapshotJson(ExecutionSnapshotCodec.encode(ExecutionSnapshot.empty("run-1", "agui-1", 1)));
        return run;
    }

    private static ChatSessionEntity session() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        session.setAppId("app-1");
        session.setTenantId("tenant-1");
        return session;
    }
}
