package com.lambda.fusion.ai.chat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.mapper.ChatRunMapper;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.run.RunSnapshot;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class ChatRunServiceImplTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        initializeTable(ChatRunMapper.class.getName(), ChatRunEntity.class);
        initializeTable(ChatSessionMapper.class.getName(), ChatSessionEntity.class);
    }

    private static void initializeTable(String namespace, Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private final ChatRunMapper runMapper = mock(ChatRunMapper.class);
    private final ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
    private final ChatSessionService sessionService = mock(ChatSessionService.class);
    private final ChatMessageService messageService = mock(ChatMessageService.class);
    private final ChatRunServiceImpl service = new ChatRunServiceImpl(
            runMapper,
            sessionMapper,
            sessionService,
            messageService,
            mock(ChatAttachmentService.class),
            mock(AppService.class));

    @Test
    void shouldLetStoppingStateWinAgainstConcurrentCompletion() {
        ChatRunEntity identity = run(ChatRunStatus.RUNNING);
        ChatRunEntity persisted = run(ChatRunStatus.STOPPING);
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        ChatMessageEntity assistant = new ChatMessageEntity();
        assistant.setId(11L);

        when(sessionMapper.selectForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(persisted);
        when(messageService.saveAssistantMessage(session, "partial", null)).thenReturn(assistant);
        when(runMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        var result = service.finalizeRun(
                identity, ChatRunStatus.COMPLETED, "SUCCESS", snapshot("partial"), null, 5, null, null);

        assertThat(result.status()).isEqualTo(ChatRunStatus.STOPPED.name());
        assertThat(result.finishReason()).isEqualTo("USER_STOP");
        assertThat(result.assistantMessageId()).isEqualTo(11L);
        assertThat(result.errorCode()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void shouldAcceptMissingRunWhenTerminalCursorRacesWithSessionDeletion() {
        ChatRunEntity run = run(ChatRunStatus.COMPLETED);
        when(runMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        when(runMapper.selectById("run-1")).thenReturn(null);

        assertThatCode(() -> service.recordTerminalSeq(run, snapshot("answer"), 8))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldExpireOnlyRunThatIsStillAwaitingConfirmation() {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 14, 12, 0);
        when(runMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertThat(service.requestConfirmationTimeout(run, deadline)).isTrue();

        ArgumentCaptor<LambdaUpdateWrapper<ChatRunEntity>> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(runMapper).update(isNull(), updateCaptor.capture());
        LambdaUpdateWrapper<ChatRunEntity> update = updateCaptor.getValue();
        assertThat(update.getSqlSegment()).contains("status").contains("await_confirm_deadline_at");
        assertThat(update.getParamNameValuePairs().values()).contains(ChatRunStatus.AWAITING_CONFIRM.name(), deadline);
    }

    @Test
    void shouldAdvanceWhenPhaseMatchesAndDecisionsAreComplete() {
        ChatRunEntity run = awaitingConfirmRun(2, snapshotWithPendingTool("call_1"));
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);
        when(runMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ConfirmToolCall command = confirmCommand(2, List.of(decision("call_1", true)));
        ChatRunService.ConfirmTransition transition = service.confirm("session-1", "run-1", command);

        assertThat(transition.resumed()).isTrue();
        assertThat(transition.run().getStatus()).isEqualTo(ChatRunStatus.RUNNING.name());
        assertThat(transition.run().getPhaseNo()).isEqualTo(3);
    }

    @Test
    void shouldReturnIdempotentWhenPhaseAlreadyAdvanced() {
        ChatRunEntity run = awaitingConfirmRun(3, snapshotWithPendingTool("call_1"));
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);

        ConfirmToolCall command = confirmCommand(2, List.of(decision("call_1", true)));
        ChatRunService.ConfirmTransition transition = service.confirm("session-1", "run-1", command);

        assertThat(transition.resumed()).isFalse();
        assertThat(transition.run().getPhaseNo()).isEqualTo(3);
    }

    @Test
    void shouldRejectConfirmWhenPhaseBehind() {
        ChatRunEntity run = awaitingConfirmRun(1, snapshotWithPendingTool("call_1"));
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);

        ConfirmToolCall command = confirmCommand(2, List.of(decision("call_1", true)));
        assertThatThrownBy(() -> service.confirm("session-1", "run-1", command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_STATE_CONFLICT.getCode()));
    }

    @Test
    void shouldRejectConfirmWhenStatusIsNotAwaiting() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING);
        run.setPhaseNo(2);
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);

        ConfirmToolCall command = confirmCommand(2, List.of(decision("call_1", true)));
        assertThatThrownBy(() -> service.confirm("session-1", "run-1", command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_STATE_CONFLICT.getCode()));
    }

    @Test
    void shouldRejectConfirmWhenDecisionsAreIncomplete() {
        ChatRunEntity run = awaitingConfirmRun(2, snapshotWithPendingTool("call_1"));
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);

        ConfirmToolCall command = confirmCommand(2, List.of());
        assertThatThrownBy(() -> service.confirm("session-1", "run-1", command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.INVALID_PARAMETER.getCode()));
    }

    @Test
    void shouldRejectConfirmWhenCasLosesRace() {
        ChatRunEntity run = awaitingConfirmRun(2, snapshotWithPendingTool("call_1"));
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId("session-1");
        session.setUserId("user-1");
        when(sessionService.loadOwnedForUpdate("session-1")).thenReturn(session);
        when(runMapper.selectOne(any())).thenReturn(run);
        when(runMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        ConfirmToolCall command = confirmCommand(2, List.of(decision("call_1", true)));
        assertThatThrownBy(() -> service.confirm("session-1", "run-1", command))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(e -> assertThat(((AiBusinessException) e).getCode())
                        .isEqualTo(AiErrorCode.CHAT_RUN_STATE_CONFLICT.getCode()));
    }

    private static ChatRunEntity run(ChatRunStatus status) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        return run;
    }

    private static ChatRunEntity awaitingConfirmRun(int phaseNo, RunSnapshot snapshot) {
        ChatRunEntity run = run(ChatRunStatus.AWAITING_CONFIRM);
        run.setPhaseNo(phaseNo);
        run.setSnapshotJson(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(snapshot));
        return run;
    }

    private static RunSnapshot snapshotWithPendingTool(String toolCallId) {
        return new RunSnapshot(
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
                List.of(new RunSnapshot.Tool(toolCallId, "demo_tool", "", "", "asking")));
    }

    private static ConfirmToolCall confirmCommand(int phaseNo, List<ConfirmToolCall.Decision> decisions) {
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

    private static RunSnapshot snapshot(String text) {
        return new RunSnapshot("run-1", "phase-1", 1, text, "", "message-1", null, false, false, null, null);
    }
}
