package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Sinks;

/**
 * 执行实例排空语义测试：固定「业务终态（terminalSignal）与源流排空（drainedSignal）分离」、
 * 「Workspace 审计移到源流终止后、用 phase 起始时刻」、「记忆尾部失败不改 COMPLETED」等目标语义。
 *
 * @author Jin
 */
class ChatRunInstanceDrainTest {

    private final ChatRunStateService runService = mock(ChatRunStateService.class);
    private final ChatRunEventStore eventStore = mock(ChatRunEventStore.class);
    private final AgentExecutionAdapter adapter = mock(AgentExecutionAdapter.class);
    private final WorkspaceAuditRecorder workspaceAuditRecorder = mock(WorkspaceAuditRecorder.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ChatRunInstance instance;

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void shouldKeepDrainedOpenUntilSourceTerminatesAfterBusinessTerminal() {
        instance = newInstance(run(ChatRunStatus.RUNNING));
        stubTerminalCommit();
        // 源流：先发根 AGENT_END，随后保持打开（模拟记忆尾部），由测试手动 complete。
        Sinks.Many<AgentEvent> source = Sinks.many().unicast().onBackpressureBuffer();
        when(adapter.stream(any(Msg.class))).thenReturn(source.asFlux());

        long beforeStart = System.currentTimeMillis();
        instance.startPhase(userMsg());
        source.tryEmitNext(agentEnd());

        // 根 AGENT_END：业务终态已提交、terminalSignal 完成，但源流未终止，drainedSignal 不应完成，且未审计。
        verify(runService).finalizeExecution(eq(instance.run), any(FinalizeCommand.class));
        assertThat(instance.terminalSignal().toCompletableFuture()).isDone();
        assertThat(instance.drainedSignal().toCompletableFuture()).isNotDone();
        verify(workspaceAuditRecorder, never()).recordChanges(any(), anyLong());

        // 源流终止（含记忆尾部结束）：完成 drainedSignal，并在此时执行审计，审计下界 ≥ startPhase 时刻。
        source.tryEmitComplete();
        assertThat(instance.drainedSignal().toCompletableFuture()).succeedsWithin(Duration.ofSeconds(2));
        ArgumentCaptor<Long> auditSince = ArgumentCaptor.forClass(Long.class);
        verify(workspaceAuditRecorder).recordChanges(eq(instance.session), auditSince.capture());
        assertThat(auditSince.getValue()).isGreaterThanOrEqualTo(beforeStart);
    }

    @Test
    void shouldKeepCompletedWhenPostProcessingTailFails() {
        instance = newInstance(run(ChatRunStatus.RUNNING));
        stubTerminalCommit();
        Sinks.Many<AgentEvent> source = Sinks.many().unicast().onBackpressureBuffer();
        when(adapter.stream(any(Msg.class))).thenReturn(source.asFlux());

        instance.startPhase(userMsg());
        source.tryEmitNext(agentEnd());
        assertThat(instance.terminalSignal().toCompletableFuture()).isDone();

        // 业务终态后的记忆/维护尾部失败：Run 保持 COMPLETED，不改为 FAILED；源流终止仍完成排空。
        source.tryEmitError(new RuntimeException("memory flush failed"));
        assertThat(instance.drainedSignal().toCompletableFuture()).succeedsWithin(Duration.ofSeconds(2));
        assertThat(instance.run.getStatus()).isEqualTo(ChatRunStatus.COMPLETED.name());
    }

    @Test
    void shouldCompleteDrainImmediatelyWhenFinalizingWithoutActiveSource() {
        // 纯终结恢复实例（无源流）：finalize 后 drainedSignal 应立即完成，供协调器摘除。
        instance = newInstance(run(ChatRunStatus.RUNNING));
        stubTerminalCommit();

        instance.finalizeCompleted();

        assertThat(instance.terminalSignal().toCompletableFuture()).isDone();
        assertThat(instance.drainedSignal().toCompletableFuture()).isDone();
    }

    @Test
    void shouldReleaseNeverStartedInstance() {
        instance = newInstance(run(ChatRunStatus.RUNNING));

        instance.releaseNeverStarted();

        assertThat(instance.terminalSignal().toCompletableFuture()).isDone();
        assertThat(instance.drainedSignal().toCompletableFuture()).isDone();
        assertThat(instance.phaseDrainedSignal().toCompletableFuture()).isDone();
        // 从未建立源流，不写数据库终结。
        verify(runService, never()).finalizeExecution(any(), any());
    }

    @Test
    void shouldDeferConfirmationWhenSourceStillDraining() {
        // 读法 B：实例仍有在排空的源流（sourceActive=true）时，confirm 只暂存、立即受理返回 resumed=false，
        // 不在排空中执行数据库 CAS。恢复与三方校验由真实 Agent 的特征测试端到端覆盖。
        instance = newInstance(runWithPendingTool(ChatRunStatus.AWAITING_CONFIRM, "call-1"));
        Sinks.Many<AgentEvent> source = Sinks.many().unicast().onBackpressureBuffer();
        when(adapter.stream(any(Msg.class))).thenReturn(source.asFlux());
        // 让实例进入一个仍在排空的源流（首 phase），随后处于待确认。
        instance.run.setStatus(ChatRunStatus.RUNNING.name());
        instance.startPhase(userMsg());
        instance.run.setStatus(ChatRunStatus.AWAITING_CONFIRM.name());

        ConfirmTransition transition = instance.confirm(command(1));

        // 排空中：暂存确认、立即受理（resumed=false），未做数据库 CAS。
        assertThat(transition.resumed()).isFalse();
        verify(runService, never()).advanceConfirmation(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static io.agentscope.core.message.ToolUseBlock askingBlock(String toolCallId) {
        return io.agentscope.core.message.ToolUseBlock.builder()
                .id(toolCallId)
                .name("demo_tool")
                .state(io.agentscope.core.message.ToolCallState.ASKING)
                .build();
    }

    private static ChatRunEntity runWithPendingTool(ChatRunStatus status, String toolCallId) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        run.setPhaseNo(1);
        run.setAguiRunId("agui-1");
        run.setSnapshotSeq(0L);
        ExecutionSnapshot.Tool tool = new ExecutionSnapshot.Tool(toolCallId, "demo_tool", "", "", "asking");
        ExecutionSnapshot snapshot =
                new ExecutionSnapshot("run-1", "agui-1", 1, "", "", null, null, false, false, List.of(), List.of(tool));
        run.setSnapshotJson(ExecutionSnapshotCodec.encode(snapshot));
        return run;
    }

    private static ConfirmToolCall command(int phaseNo) {
        ConfirmToolCall command = new ConfirmToolCall();
        command.setPhaseNo(phaseNo);
        ConfirmToolCall.Decision decision = new ConfirmToolCall.Decision();
        decision.setToolCallId("call-1");
        decision.setConfirmed(true);
        command.setDecisions(List.of(decision));
        return command;
    }

    private void stubTerminalCommit() {
        lenient()
                .when(runService.finalizeExecution(eq(instance.run), any(FinalizeCommand.class)))
                .thenAnswer(invocation -> {
                    FinalizeCommand command = invocation.getArgument(1);
                    return new FinalizeResult(
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
        lenient()
                .when(eventStore.appendTerminalIfAbsent(anyString(), anyString(), anyString()))
                .thenReturn(new ChatRunEvent(8L, "run-1:8", "RUN_FINISHED", "{}"));
        lenient().when(eventStore.latestSeq(anyString(), anyLong())).thenReturn(1L);
    }

    private ChatRunInstance newInstance(ChatRunEntity run) {
        return new ChatRunInstance(
                runService,
                eventStore,
                new AiProperties(),
                scheduler,
                workspaceAuditRecorder,
                run,
                session(),
                adapter,
                new ChatRunSnapshotAccumulator(ExecutionSnapshotCodec.decode(run.getSnapshotJson())));
    }

    private static AgentEvent agentEnd() {
        return new AgentEndEvent("reply-1");
    }

    private static Msg userMsg() {
        return Msg.builderForRole(MsgRole.USER).textContent("hi").build();
    }

    private static ChatRunEntity run(ChatRunStatus status) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        run.setPhaseNo(1);
        run.setAguiRunId("agui-1");
        run.setSnapshotSeq(0L);
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
