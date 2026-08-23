package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.SubmitToolInput;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.agui.InterruptFactory;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

/** 挂起输入（单选/多选/文本）链路集成测试：挂起进入待输入、提交、取消与进程重启恢复。 */
class ChatRunCoordinatorInputTest {

    private ChatRunStateService runService;
    private ChatRunEventStore eventStore;
    private AgentFactory agentFactory;
    private ChatExecutionService coordinator;
    private ChatExecutionInstanceFactory instanceFactory;
    private HarnessAgent agent;
    private ReActAgent delegate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @BeforeEach
    void setUp() {
        runService = mock(ChatRunStateService.class);
        eventStore = mock(ChatRunEventStore.class);
        agentFactory = mock(AgentFactory.class);
        agent = mock(HarnessAgent.class);
        delegate = mock(ReActAgent.class);
        when(agent.getDelegate()).thenReturn(delegate);
        when(agent.getName()).thenReturn("assistant");
        when(agentFactory.getOrBuild(anyString(), anyString())).thenReturn(agent);

        AiProperties properties = new AiProperties();
        instanceFactory = new ChatExecutionInstanceFactory(
                runService,
                eventStore,
                agentFactory,
                mock(WorkspaceAuditRecorder.class),
                mock(ObjectProvider.class),
                properties);
        coordinator = new ChatExecutionService(
                runService,
                eventStore,
                mock(ChatMessageService.class),
                mock(ChatAttachmentService.class),
                null,
                mock(AppService.class),
                instanceFactory,
                properties);
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
        scheduler.shutdownNow();
    }

    /**
     * 挂起链路（AgentScope 2.0.1 真实序列）：外部工具挂起不发 REQUIRE_EXTERNAL_EXECUTION，
     * 而是发出 TOOL_RESULT_END(RUNNING) + 根 AGENT_RESULT(TOOL_SUSPENDED)，随后根 AGENT_END
     * 结束源流，快照落 pendingInputs 并发布 interrupt。
     */
    @Test
    void shouldCheckpointPendingInputsWhenExternalExecutionSuspends() {
        ChatRunEntity run = runningRun(1);
        ChatSessionEntity session = session();
        when(runService.checkpoint(eq(run), any(ChatRunSnapshot.class))).thenReturn(true);
        ToolUseBlock toolUse = suspendedBlock("call_1");
        Msg suspendedResult = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(List.of(toolUse, ToolResultBlock.suspended(toolUse)))
                .generateReason(GenerateReason.TOOL_SUSPENDED)
                .build();
        when(agent.streamEvents(any(Msg.class), any()))
                .thenReturn(Flux.just(
                        new ToolResultEndEvent("reply-1", "call_1", "ask_single_choice", ToolResultState.RUNNING),
                        new AgentResultEvent(suspendedResult),
                        new AgentEndEvent("reply-1")));

        ChatExecutionInstance execution = instanceFactory.createAgentBacked(run, session, scheduler);
        execution.startPhase(userMessage());

        ArgumentCaptor<ChatRunSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ChatRunSnapshot.class);
        verify(runService, timeout(2000)).checkpoint(eq(run), snapshotCaptor.capture());
        ChatRunSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.pendingInputs()).hasSize(1);
        ChatRunSnapshot.PendingInput pending = snapshot.pendingInputs().get(0);
        assertThat(pending.toolCallId()).isEqualTo("call_1");
        assertThat(pending.inputKind()).isEqualTo(InterruptFactory.KIND_SINGLE_CHOICE);
        assertThat(pending.question()).isEqualTo("选择部署环境");
        assertThat(snapshot.tools()).anySatisfy(tool -> {
            assertThat(tool.toolCallId()).isEqualTo("call_1");
            assertThat(tool.status()).isEqualTo("awaiting_input");
        });
        // interrupt 事件在快照提交后发布（事实先于信号）；事件按批次追加，断言跨批次进行。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AguiEvent>> eventsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(eventStore, timeout(2000).atLeastOnce()).appendAll(eq("run-1"), eq("agui-1"), eventsCaptor.capture());
        assertThat(eventsCaptor.getAllValues().stream().flatMap(List::stream).toList())
                .anySatisfy(event -> {
                    assertThat(event).isInstanceOf(AguiEvent.RunFinished.class);
                    AguiEvent.RunFinished finished = (AguiEvent.RunFinished) event;
                    assertThat(finished.outcome()).isInstanceOf(AguiEvent.RunFinishedInterruptOutcome.class);
                    AguiEvent.RunFinishedInterruptOutcome outcome =
                            (AguiEvent.RunFinishedInterruptOutcome) finished.outcome();
                    assertThat(outcome.interrupts()).anySatisfy(interrupt -> {
                        assertThat(interrupt.reason()).isEqualTo(InterruptFactory.REASON_INPUT_REQUIRED);
                        assertThat(interrupt.toolCallId()).isEqualTo("call_1");
                        assertThat(interrupt.responseSchema()).containsKey("properties");
                    });
                });
    }

    /** 提交链路：合法单选值经三方校验后构造 TOOL 恢复消息并推进下一阶段。 */
    @Test
    void shouldResumeWithToolResultWhenInputSubmitted() {
        ChatRunEntity run = awaitingInputRun(2, "call_1");
        ChatSessionEntity session = session();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithSuspendedBlock("call_1"));
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ConfirmTransition transition =
                execution(run, session).submitInput(command(2, List.of(input("call_1", new TextNode("prod")))));

        assertThat(transition.resumed()).isTrue();
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent).streamEvents(captor.capture(), any());
        Msg resumeMessage = captor.getValue();
        assertThat(resumeMessage.getRole()).isEqualTo(MsgRole.TOOL);
        ToolResultBlock result = resultBlockOf(resumeMessage, "call_1");
        assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
        assertThat(textOf(result)).isEqualTo("prod");
    }

    /** 取消链路：value=null 补写 INTERRUPTED 结果，Agent 看到用户取消并自行决定后续。 */
    @Test
    void shouldResumeWithInterruptedResultWhenInputCancelled() {
        ChatRunEntity run = awaitingInputRun(2, "call_1");
        ChatSessionEntity session = session();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithSuspendedBlock("call_1"));
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ConfirmTransition transition = execution(run, session).submitInput(command(2, List.of(input("call_1", null))));

        assertThat(transition.resumed()).isTrue();
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent).streamEvents(captor.capture(), any());
        ToolResultBlock result = resultBlockOf(captor.getValue(), "call_1");
        assertThat(result.getState()).isEqualTo(ToolResultState.INTERRUPTED);
        assertThat(textOf(result)).contains("cancelled");
    }

    /** 断线恢复链路：进程重启后注册表无实例，从持久化快照重建并恢复下一阶段。 */
    @Test
    void shouldRehydratePausedInputAfterRestart() {
        ChatRunEntity run = awaitingInputRun(2, "call_1");
        ChatSessionEntity session = session();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithSuspendedBlock("call_1"));
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        ConfirmTransition transition =
                coordinator.submitInput(run, session, command(2, List.of(input("call_1", new TextNode("prod")))));

        assertThat(transition.resumed()).isTrue();
        verify(delegate).getAgentState("user-1", "session-1");
        verify(agent).streamEvents(any(Msg.class), any());
    }

    /** 回归守护：枚举外的提交值在触碰 Agent 状态推进前被拒绝。 */
    @Test
    void shouldRejectValueOutsideResponseSchema() {
        ChatRunEntity run = awaitingInputRun(2, "call_1");
        ChatSessionEntity session = session();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithSuspendedBlock("call_1"));

        assertThatThrownBy(() -> execution(run, session)
                        .submitInput(command(2, List.of(input("call_1", new TextNode("staging"))))))
                .isInstanceOf(AiBusinessException.class)
                .satisfies(error -> assertThat(((AiBusinessException) error).getCode())
                        .isEqualTo(AiErrorCode.INVALID_PARAMETER.getCode()));
        verify(runService, never()).advanceConfirmation(any(), any(), anyInt(), any());
    }

    /** 多选提交：数组值满足枚举与数量约束后按原样回传。 */
    @Test
    void shouldResumeWithArrayResultWhenMultiChoiceSubmitted() {
        ChatRunEntity run = awaitingInputRun(2, "call_2", KIND_MULTI);
        ChatSessionEntity session = session();
        when(delegate.getAgentState("user-1", "session-1")).thenReturn(stateWithSuspendedBlock("call_2"));
        stubResumed(run, 2);
        when(agent.streamEvents(any(Msg.class), any())).thenReturn(Flux.never());

        JsonNode choices = JsonNodeFactory.instance.arrayNode().add("a").add("b");
        ConfirmTransition transition =
                execution(run, session).submitInput(command(2, List.of(input("call_2", choices))));

        assertThat(transition.resumed()).isTrue();
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        verify(agent).streamEvents(captor.capture(), any());
        ToolResultBlock result = resultBlockOf(captor.getValue(), "call_2");
        assertThat(result.getState()).isEqualTo(ToolResultState.SUCCESS);
        assertThat(textOf(result)).isEqualTo("[\"a\",\"b\"]");
    }

    private void stubResumed(ChatRunEntity run, int sourcePhaseNo) {
        when(runService.advanceConfirmation(eq(run), any(ChatSessionEntity.class), eq(sourcePhaseNo), any()))
                .thenAnswer(invocation -> {
                    run.setStatus(ChatRunStatus.RUNNING.name());
                    run.setPhaseNo(sourcePhaseNo + 1);
                    run.setAguiRunId("agui-next");
                    return new ConfirmTransition(run, session(), true);
                });
    }

    private ChatExecutionInstance execution(ChatRunEntity run, ChatSessionEntity session) {
        return instanceFactory.createAgentBacked(run, session, scheduler);
    }

    /** 从 TOOL 恢复消息中按 ID 取结果块。 */
    private static ToolResultBlock resultBlockOf(Msg message, String toolCallId) {
        return message.getContentBlocks(ToolResultBlock.class).stream()
                .filter(block -> toolCallId.equals(block.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少结果块: " + toolCallId));
    }

    private static String textOf(ToolResultBlock block) {
        return block.getOutput().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .findFirst()
                .orElse("");
    }

    private static Msg userMessage() {
        return Msg.builderForRole(MsgRole.USER)
                .content(TextBlock.builder().text("deploy").build())
                .build();
    }

    private static ChatRunEntity runningRun(int phaseNo) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setPhaseNo(phaseNo);
        run.setAguiRunId("agui-1");
        ChatRunSnapshot snapshot = ChatRunSnapshot.empty("run-1", "agui-1", phaseNo);
        run.setSnapshotJson(JsonUtils.getJsonCodec().toJson(snapshot));
        return run;
    }

    private static final String KIND_SINGLE = InterruptFactory.KIND_SINGLE_CHOICE;
    private static final String KIND_MULTI = InterruptFactory.KIND_MULTI_CHOICE;

    /** 单选待输入快照：responseSchema 含 dev/prod 枚举。 */
    private static ChatRunEntity awaitingInputRun(int phaseNo, String toolCallId) {
        return awaitingInputRun(phaseNo, toolCallId, KIND_SINGLE);
    }

    private static ChatRunEntity awaitingInputRun(int phaseNo, String toolCallId, String inputKind) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(ChatRunStatus.RUNNING.name());
        run.setPhaseNo(phaseNo);
        run.setAguiRunId("agui-1");
        String schema = KIND_MULTI.equals(inputKind)
                ? "{\"type\":\"object\",\"properties\":{\"choices\":{\"type\":\"array\","
                        + "\"items\":{\"type\":\"string\",\"enum\":[\"a\",\"b\",\"c\"]}}},\"required\":[\"choices\"]}"
                : "{\"type\":\"object\",\"properties\":{\"choice\":{\"type\":\"string\","
                        + "\"enum\":[\"dev\",\"prod\"]}},\"required\":[\"choice\"]}";
        ChatRunSnapshot.PendingInput pending =
                new ChatRunSnapshot.PendingInput(toolCallId, "ask_single_choice", "选择部署环境", inputKind, schema);
        ChatRunSnapshot snapshot = new ChatRunSnapshot(
                "run-1", "agui-1", phaseNo, "", "", null, null, false, false, List.of(), List.of(), List.of(pending));
        run.setSnapshotJson(JsonUtils.getJsonCodec().toJson(snapshot));
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

    private static SubmitToolInput command(int phaseNo, List<SubmitToolInput.Input> inputs) {
        SubmitToolInput command = new SubmitToolInput();
        command.setPhaseNo(phaseNo);
        command.setInputs(inputs);
        return command;
    }

    private static SubmitToolInput.Input input(String toolCallId, JsonNode value) {
        SubmitToolInput.Input input = new SubmitToolInput.Input();
        input.setToolCallId(toolCallId);
        input.setValue(value);
        return input;
    }

    /** 挂起调用块：非 ASKING 且上下文无对应结果，与 readSuspendedToolBlocks 判定一致。 */
    private static ToolUseBlock suspendedBlock(String toolCallId) {
        return ToolUseBlock.builder()
                .id(toolCallId)
                .name("ask_single_choice")
                .input(java.util.Map.of("question", "选择部署环境", "options", List.of("dev", "prod")))
                .state(ToolCallState.SUBMITTED)
                .build();
    }

    private static AgentState stateWithSuspendedBlock(String toolCallId) {
        Msg assistant = Msg.builderForRole(MsgRole.ASSISTANT)
                .content(new ArrayList<>(List.of(suspendedBlock(toolCallId))))
                .build();
        return AgentState.builder().context(List.of(assistant)).build();
    }
}
