package com.lambda.fusion.ai.chat.execution.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventStore;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

class ExecutionCoordinatorConfirmTest {

    private ChatRunStateService runService;
    private ExecutionEventStore eventStore;
    private AgentFactory agentFactory;
    private ExecutionCoordinator coordinator;
    private HarnessAgent agent;
    private ReActAgent delegate;

    @BeforeEach
    void setUp() {
        runService = mock(ChatRunStateService.class);
        eventStore = mock(ExecutionEventStore.class);
        agentFactory = mock(AgentFactory.class);
        agent = mock(HarnessAgent.class);
        delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agentFactory.getOrBuild(anyString(), anyString())).thenReturn(agent);

        coordinator = new ExecutionCoordinator(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                agentFactory,
                mock(WorkspaceAuditRecorder.class),
                mock(ObjectProvider.class),
                new AiProperties());
    }

    @Test
    void shouldThrowUnavailableWhenStateStoreThrows() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        when(delegate.getAgentState("user-1", "session-1")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
    }

    @Test
    void shouldThrowUnavailableWhenStateOrContextIsNull() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        when(delegate.getAgentState("user-1", "session-1")).thenReturn(null);

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
    }

    @Test
    void shouldThrowUnavailableWhenNoAskingBlockExists() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithToolBlocks(List.of());
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
    }

    @Test
    void shouldThrowMismatchWhenSnapshotAndAgentIdsDiffer() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithAskingBlock("call_2");
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH.getCode()));
    }

    @Test
    void shouldThrowInvalidParameterWhenDecisionsAreDuplicated() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true), decision("call_1", false)));

        AgentState state = stateWithAskingBlock("call_1");
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.INVALID_PARAMETER.getCode()));
    }

    @Test
    void shouldThrowMismatchWhenDecisionsAreIncomplete() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1", "call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithAskingBlocks(List.of("call_1", "call_2"));
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.prepareConfirmation(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH.getCode()));
    }

    @Test
    void shouldReturnPreparedResultsInDecisionOrder() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1", "call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_2", false), decision("call_1", true)));

        AgentState state = stateWithAskingBlocks(List.of("call_1", "call_2"));
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        ExecutionCoordinator.PreparedConfirmation prepared = coordinator.prepareConfirmation(run, session, command);

        assertThat(prepared.runId()).isEqualTo("run-1");
        assertThat(prepared.sourcePhaseNo()).isEqualTo(2);
        List<ConfirmResult> results = prepared.results();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getToolCall().getId()).isEqualTo("call_2");
        assertThat(results.get(0).isConfirmed()).isFalse();
        assertThat(results.get(1).getToolCall().getId()).isEqualTo("call_1");
        assertThat(results.get(1).isConfirmed()).isTrue();
    }

    @Test
    void shouldNotReadAgentStateWhenResumePrepared() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithAskingBlock("call_1");
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ExecutionCoordinator.PreparedConfirmation prepared = coordinator.prepareConfirmation(run, session, command);
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setPhaseNo(3);
        coordinator.resumePrepared(run, session, prepared);

        verify(delegate).getAgentState("user-1", "session-1");
        verifyNoMoreInteractions(delegate);
    }

    private static ChatRunEntity awaitingRun(int phaseNo) {
        return awaitingRun(phaseNo, List.of("call_1"));
    }

    private static ChatRunEntity awaitingRun(int phaseNo, List<String> pendingIds) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(ChatRunStatus.AWAITING_CONFIRM.name());
        run.setPhaseNo(phaseNo);
        List<ExecutionSnapshot.Tool> pendingTools = pendingIds.stream()
                .map(id -> new ExecutionSnapshot.Tool(id, "demo", "", "", "asking"))
                .toList();
        ExecutionSnapshot snapshot = new ExecutionSnapshot(
                "run-1", "phase-1", phaseNo, "", "", null, null, false, false, List.of(), pendingTools);
        run.setSnapshotJson(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(snapshot));
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

    private static ConfirmToolCall command(int phaseNo, List<ConfirmToolCall.Decision> decisions) {
        ConfirmToolCall command = new ConfirmToolCall();
        command.setPhaseNo(phaseNo);
        command.setDecisions(decisions);
        return command;
    }

    private static ConfirmToolCall.Decision decision(String toolCallId, boolean confirmed) {
        ConfirmToolCall.Decision decision = new ConfirmToolCall.Decision();
        decision.setToolCallId(toolCallId);
        decision.setConfirmed(confirmed);
        return decision;
    }

    private static AgentState stateWithAskingBlock(String toolCallId) {
        return stateWithAskingBlocks(List.of(toolCallId));
    }

    private static AgentState stateWithAskingBlocks(List<String> toolCallIds) {
        return stateWithToolBlocks(toolCallIds.stream()
                .map(ExecutionCoordinatorConfirmTest::askingBlock)
                .toList());
    }

    private static AgentState stateWithToolBlocks(List<ToolUseBlock> blocks) {
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(blocks))
                .build();
        return AgentState.builder().context(List.of(assistant)).build();
    }

    private static ToolUseBlock askingBlock(String toolCallId) {
        return ToolUseBlock.builder()
                .id(toolCallId)
                .name("demo_tool")
                .state(ToolCallState.ASKING)
                .build();
    }
}
