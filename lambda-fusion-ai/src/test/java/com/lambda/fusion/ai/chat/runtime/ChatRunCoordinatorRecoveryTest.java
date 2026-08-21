package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.ChatRunFinalizationCommand;
import com.lambda.fusion.ai.chat.model.ChatRunFinalizationResult;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstanceFactory;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ChatRunCoordinatorRecoveryTest {

    private final ChatRunStateService runService = mock(ChatRunStateService.class);
    private final ChatRunEventStore eventStore = mock(ChatRunEventStore.class);
    private final AgentFactory agentFactory = mock(AgentFactory.class);
    private final WorkspaceAuditRecorder workspaceAuditRecorder = mock(WorkspaceAuditRecorder.class);
    private ChatRunCoordinator coordinator;
    private ChatRunRecoveryListener startupRecovery;

    @AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void shouldRetainAwaitingConfirmationWithPersistentStateStore() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM, List.of("call-1"));
        prepareRecovery(run, "FILE");
        stubAgentWithAskingState();

        startupRecovery.recoverOnStartup();

        verify(eventStore).initialize("run-1", 7L);
        verify(runService, never()).finalizeExecution(any(), any());
        assertThat(run.getStatus()).isEqualTo(ChatRunStatus.AWAITING_CONFIRM.name());
    }

    @Test
    void shouldFailAwaitingConfirmationWhenPersistentStateDoesNotMatchSnapshot() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM, List.of("call-1"));
        prepareRecovery(run, "FILE");
        stubAgentWithState(AgentState.builder().context(List.of()).build());
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        ChatRunFinalizationCommand command = captureChatRunFinalizationCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo(ChatRunFailureCode.CONFIRM_CONTEXT_UNAVAILABLE);
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，用户确认上下文不可恢复");
    }

    @Test
    void shouldFailAwaitingConfirmationWithMemoryStateStore() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        prepareRecovery(run, "MEMORY");
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        ChatRunFinalizationCommand command = captureChatRunFinalizationCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo(ChatRunFailureCode.CONFIRM_CONTEXT_UNAVAILABLE);
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，用户确认上下文不可恢复");
    }

    @Test
    void shouldFailRunningEvenWithPersistentStateStore() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        prepareRecovery(run, "REDIS");
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        ChatRunFinalizationCommand command = captureChatRunFinalizationCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo(ChatRunFailureCode.INSTANCE_LOST);
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，对话运行已终止");
    }

    @Test
    void shouldClosePendingToolCallsWhenFailingInterruptedRun() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        prepareRecovery(run, "REDIS");
        ReActAgent delegate = stubAgentWithAskingState();
        stubTerminalCommit(run);

        startupRecovery.recoverOnStartup();

        ChatRunFinalizationCommand command = captureChatRunFinalizationCommand(run);
        assertThat(command.errorCode()).isEqualTo(ChatRunFailureCode.INSTANCE_LOST);
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
        verify(runService).finalizeExecution(eq(run), any(ChatRunFinalizationCommand.class));
    }

    private void prepareRecovery(ChatRunEntity run, String stateStoreType) {
        ChatSessionEntity session = session();
        when(runService.listInterruptedOnRestart()).thenReturn(List.of(run));
        when(runService.listCreated()).thenReturn(List.of());
        when(runService.loadSession(run)).thenReturn(session);

        AiProperties properties = new AiProperties();
        properties.getStateStore().setType(stateStoreType);
        ChatRunInstanceFactory instanceFactory = new ChatRunInstanceFactory(
                runService, eventStore, agentFactory, workspaceAuditRecorder, mock(ObjectProvider.class), properties);
        coordinator = new ChatRunCoordinator(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                instanceFactory,
                properties);
        startupRecovery = new ChatRunRecoveryListener(runService, coordinator);
    }

    private void stubTerminalCommit(ChatRunEntity run) {
        when(runService.finalizeExecution(eq(run), any(ChatRunFinalizationCommand.class)))
                .thenAnswer(invocation -> {
                    ChatRunFinalizationCommand command = invocation.getArgument(1);
                    return new ChatRunFinalizationResult(
                            true,
                            command.targetStatus().name(),
                            command.finishReason() == null
                                    ? null
                                    : command.finishReason().name(),
                            command.errorCode() == null
                                    ? null
                                    : command.errorCode().name(),
                            command.errorMessage());
                });
        when(eventStore.appendTerminalIfAbsent(anyString(), anyString(), anyString()))
                .thenReturn(new ChatRunEvent(8L, "terminal-8", "RUN_FINISHED", "{}"));
    }

    /** 注册带 ASKING 待确认工具调用的 Agent，返回 delegate 供校验状态闭合调用。 */
    private ReActAgent stubAgentWithAskingState() {
        ToolUseBlock block = ToolUseBlock.builder()
                .id("call-1")
                .name("demo_tool")
                .state(ToolCallState.ASKING)
                .build();
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(block)))
                .build();
        return stubAgentWithState(
                AgentState.builder().context(List.of(assistant)).build());
    }

    private ReActAgent stubAgentWithState(AgentState state) {
        HarnessAgent agent = mock(HarnessAgent.class);
        ReActAgent delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agent.getName()).thenReturn("demo-agent");
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);
        when(agentFactory.getOrBuild("app-1", "tenant-1")).thenReturn(agent);
        return delegate;
    }

    private ChatRunFinalizationCommand captureChatRunFinalizationCommand(ChatRunEntity run) {
        ArgumentCaptor<ChatRunFinalizationCommand> captor = ArgumentCaptor.forClass(ChatRunFinalizationCommand.class);
        verify(runService).finalizeExecution(eq(run), captor.capture());
        return captor.getValue();
    }

    private static ChatRunEntity run(ChatRunStatus status) {
        return run(status, List.of());
    }

    private static ChatRunEntity run(ChatRunStatus status, List<String> pendingIds) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        run.setPhaseNo(1);
        run.setAguiRunId("agui-1");
        run.setSnapshotSeq(7L);
        List<ChatRunSnapshot.ToolCall> pendingTools = pendingIds.stream()
                .map(id -> new ChatRunSnapshot.ToolCall(id, "demo_tool", "", "", "asking"))
                .toList();
        run.setSnapshotJson(ChatRunSnapshotCodec.encode(
                new ChatRunSnapshot("run-1", "agui-1", 1, "", "", null, null, false, false, List.of(), pendingTools)));
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
