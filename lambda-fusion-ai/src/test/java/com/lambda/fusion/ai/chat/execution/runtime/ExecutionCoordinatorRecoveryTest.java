package com.lambda.fusion.ai.chat.execution.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEvent;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventStore;
import com.lambda.fusion.ai.chat.execution.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.execution.model.FinalizeResult;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ExecutionCoordinatorRecoveryTest {

    private final ChatRunStateService runService = mock(ChatRunStateService.class);
    private final ExecutionEventStore eventStore = mock(ExecutionEventStore.class);
    private final AgentFactory agentFactory = mock(AgentFactory.class);
    private final WorkspaceAuditRecorder workspaceAuditRecorder = mock(WorkspaceAuditRecorder.class);
    private ExecutionCoordinator coordinator;

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

        coordinator.recoverOnStartup();

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

        coordinator.recoverOnStartup();

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

        coordinator.recoverOnStartup();

        FinalizeCommand command = captureFinalizeCommand(run);
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.FAILED);
        assertThat(command.errorCode()).isEqualTo("INSTANCE_LOST");
        assertThat(command.errorMessage()).isEqualTo("服务进程重启，对话运行已终止");
    }

    private void prepareRecovery(ChatRunEntity run, String stateStoreType) {
        ChatSessionEntity session = session();
        when(runService.listInterruptedOnRestart()).thenReturn(List.of(run));
        when(runService.listCreated()).thenReturn(List.of());
        when(runService.loadSession(run)).thenReturn(session);

        AiProperties properties = new AiProperties();
        properties.getStateStore().setType(stateStoreType);
        coordinator = new ExecutionCoordinator(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                agentFactory,
                workspaceAuditRecorder,
                gatewayProvider(),
                properties);
    }

    private void stubTerminalCommit(ChatRunEntity run) {
        when(runService.finalizeExecution(eq(run), any(FinalizeCommand.class))).thenAnswer(invocation -> {
            FinalizeCommand command = invocation.getArgument(1);
            return new FinalizeResult(
                    true,
                    null,
                    command.targetStatus().name(),
                    command.finishReason(),
                    command.errorCode(),
                    command.errorMessage());
        });
        when(eventStore.appendTerminalIfAbsent(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ExecutionEvent(8L, "terminal-8", "{}"));
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
