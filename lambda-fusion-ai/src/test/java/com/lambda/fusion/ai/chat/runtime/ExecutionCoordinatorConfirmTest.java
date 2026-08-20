package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

class ExecutionCoordinatorConfirmTest {

    private ChatRunStateService runService;
    private ChatRunEventStore eventStore;
    private AgentFactory agentFactory;
    private ChatRunCoordinator coordinator;
    private HarnessAgent agent;
    private ReActAgent delegate;

    @BeforeEach
    void setUp() {
        runService = mock(ChatRunStateService.class);
        eventStore = mock(ChatRunEventStore.class);
        agentFactory = mock(AgentFactory.class);
        agent = mock(HarnessAgent.class);
        delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agentFactory.getOrBuild(anyString(), anyString())).thenReturn(agent);

        AiProperties properties = new AiProperties();
        ChatRunInstanceFactory instanceFactory = new ChatRunInstanceFactory(
                runService,
                eventStore,
                agentFactory,
                mock(WorkspaceAuditRecorder.class),
                mock(ObjectProvider.class),
                properties);
        coordinator = new ChatRunCoordinator(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                instanceFactory,
                properties);
    }

    @Test
    void shouldThrowUnavailableWhenStateStoreThrows() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        when(delegate.getAgentState("user-1", "session-1")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldThrowUnavailableWhenStateOrContextIsNull() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        when(delegate.getAgentState("user-1", "session-1")).thenReturn(null);

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldThrowUnavailableWhenNoAskingBlockExists() {
        ChatRunEntity run = awaitingRun(2);
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithToolBlocks(List.of());
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldThrowMismatchWhenSnapshotAndAgentIdsDiffer() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithAskingBlock("call_2");
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldThrowInvalidParameterWhenDecisionsAreDuplicated() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true), decision("call_1", false)));

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.INVALID_PARAMETER.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldThrowMismatchWhenDecisionsAreIncomplete() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1", "call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        AgentState state = stateWithAskingBlocks(List.of("call_1", "call_2"));
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);

        assertThatThrownBy(() -> coordinator.confirm(run, session, command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
    }

    @Test
    void shouldStartNextPhaseWithResultsInDecisionOrder() {
        ChatRunEntity run = awaitingRun(2, List.of("call_1", "call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_2", false), decision("call_1", true)));

        AgentState state = stateWithAskingBlocks(List.of("call_1", "call_2"));
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(state);
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ConfirmTransition transition = coordinator.confirm(run, session, command);

        assertThat(transition.resumed()).isTrue();
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent).streamEvents(captor.capture(), any());
        @SuppressWarnings("unchecked")
        List<ConfirmResult> results =
                (List<ConfirmResult>) captor.getValue().getMetadata().get(Msg.METADATA_CONFIRM_RESULTS);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getToolCall().getId()).isEqualTo("call_2");
        assertThat(results.get(0).isConfirmed()).isFalse();
        assertThat(results.get(1).getToolCall().getId()).isEqualTo("call_1");
        assertThat(results.get(1).isConfirmed()).isTrue();
    }

    @Test
    void shouldNotStartPhaseWhenConfirmationIsReplayed() {
        ChatRunEntity run = awaitingRun(3, List.of("call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_1", true)));

        ConfirmTransition transition = coordinator.confirm(run, session, command);

        assertThat(transition.resumed()).isFalse();
        verify(delegate, never()).getAgentState(any(), any());
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt());
        verify(agent, never()).streamEvents(any(Msg.class), any());
    }

    /** 回归：旧消息残留的 ASKING 不计入当前确认批次，三方校验以最后一条助手消息为准。 */
    @Test
    void shouldConfirmWhenEarlierMessageHasStaleAskingBlock() {
        ChatRunEntity run = awaitingRun(2, List.of("call_2"));
        ChatSessionEntity session = session();
        ConfirmToolCall command = command(2, List.of(decision("call_2", true)));

        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithStaleThenCurrentAsking());
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ConfirmTransition transition = coordinator.confirm(run, session, command);

        assertThat(transition.resumed()).isTrue();
        verify(agent).streamEvents(any(Msg.class), any());
    }

    /** CAS 推进成功：刷新内存实体为新阶段并返回 resumed=true。 */
    private void stubResumed(ChatRunEntity run, int sourcePhaseNo) {
        when(runService.advanceConfirmation(run, session(), sourcePhaseNo)).thenAnswer(invocation -> {
            run.setStatus(ChatRunStatus.RUNNING.name());
            run.setPhaseNo(sourcePhaseNo + 1);
            run.setAguiRunId("agui-next");
            return new ConfirmTransition(run, session(), true);
        });
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

    /** 上下文含多条助手消息：旧消息残留 call_1 的 ASKING，最后一条为当前批次 call_2。 */
    private static AgentState stateWithStaleThenCurrentAsking() {
        Msg stale = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(askingBlock("call_1"))))
                .build();
        Msg current = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(askingBlock("call_2"))))
                .build();
        return AgentState.builder().context(List.of(stale, current)).build();
    }

    private static ToolUseBlock askingBlock(String toolCallId) {
        return ToolUseBlock.builder()
                .id(toolCallId)
                .name("demo_tool")
                .state(ToolCallState.ASKING)
                .build();
    }
}
