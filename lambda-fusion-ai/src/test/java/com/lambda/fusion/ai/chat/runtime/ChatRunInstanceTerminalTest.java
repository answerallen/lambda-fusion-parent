package com.lambda.fusion.ai.chat.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/** 终态解释与关闭路径的锁定测试：终态事实由 {@link ChatRunInstance} 唯一解释。 */
class ChatRunInstanceTerminalTest {

    private final ChatRunStateService runService = mock(ChatRunStateService.class);
    private final ChatRunEventStore eventStore = mock(ChatRunEventStore.class);
    private final AgentExecutionAdapter adapter = mock(AgentExecutionAdapter.class);
    private final WorkspaceAuditRecorder workspaceAuditRecorder = mock(WorkspaceAuditRecorder.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<String, ChatRunInstance> executions = new ConcurrentHashMap<>();
    private ChatRunInstance instance;

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void shouldFinalizeCompletedOnceOnRootAgentEnd() {
        instance = newInstance(run(ChatRunStatus.RUNNING, snapshot("", false, false)));
        stubTerminalCommit();
        when(adapter.stream(any(Msg.class))).thenReturn(Flux.just(agentEnd(null)));

        instance.startPhase(
                Msg.builderForRole(io.agentscope.core.message.MsgRole.USER).build());

        FinalizeCommand command = captureFinalize();
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.COMPLETED);
        assertThat(command.finishReason()).isEqualTo(ChatRunFinishReason.SUCCESS);
        verify(runService, times(1)).finalizeExecution(eq(instance.run), any(FinalizeCommand.class));
    }

    @Test
    void shouldFinalizeStoppedOnStoppingRootAgentEnd() {
        ChatRunEntity run = run(ChatRunStatus.RUNNING, snapshot("", false, false));
        instance = newInstance(run);
        stubTerminalCommit();
        // 停止请求抢先：Agent 流发出根 AGENT_END 时 Run 已处于 STOPPING。
        when(adapter.stream(any(Msg.class))).thenReturn(Flux.<AgentEvent>create(sink -> {
            run.setStatus(ChatRunStatus.STOPPING.name());
            sink.next(agentEnd(null));
            sink.complete();
        }));

        instance.startPhase(
                Msg.builderForRole(io.agentscope.core.message.MsgRole.USER).build());

        FinalizeCommand command = captureFinalize();
        assertThat(command.targetStatus()).isEqualTo(ChatRunStatus.STOPPED);
        assertThat(command.finishReason()).isEqualTo(ChatRunFinishReason.USER_STOP);
    }

    @Test
    void shouldNotFinalizeOnChildAgentEnd() {
        instance = newInstance(run(ChatRunStatus.RUNNING, snapshot("", false, false)));
        // 子 Agent 的 AGENT_END 带 source，不得终结父 Run；流随后保持运行。
        when(adapter.stream(any(Msg.class))).thenReturn(Flux.concat(Flux.just(agentEnd("sub-agent")), Flux.never()));

        instance.startPhase(
                Msg.builderForRole(io.agentscope.core.message.MsgRole.USER).build());

        verify(runService, never()).finalizeExecution(any(), any());
        assertThat(instance.isRunning()).isTrue();
    }

    @Test
    void shouldEmitCloseEventsOnceAndClosedSnapshotWhenMessagesOpen() {
        // 起始即带打开的文本/推理消息（模拟执行中产生过内容）。
        instance = newInstance(run(ChatRunStatus.RUNNING, snapshot("partial", true, true)));
        stubTerminalCommit();

        instance.finalizeCompleted();

        FinalizeCommand command = captureFinalize();
        assertThat(command.snapshot().textOpen()).isFalse();
        assertThat(command.snapshot().reasoningOpen()).isFalse();
        // 关闭事件经快照增量一次性驱动，终态快照已闭合。
        verify(runService, times(1)).finalizeExecution(eq(instance.run), any(FinalizeCommand.class));
    }

    @Test
    void shouldClosePersistedSnapshotWhenInterpreterHasNoInMemoryMessages() {
        // 恢复实例：解释器为空（无内存消息 ID），但持久化快照处于打开状态。
        ExecutionSnapshot openSnapshot = new ExecutionSnapshot(
                "run-1", "agui-1", 1, "partial", "", "m-text", null, true, false, List.of(), List.of());
        ChatRunEntity run = run(ChatRunStatus.RUNNING, openSnapshot);
        instance = newInstance(run);
        stubTerminalCommit();

        instance.finalizeCompleted();

        FinalizeCommand command = captureFinalize();
        // closeOpenMessages 无条件置 closeActiveMessages，闭合持久化快照。
        assertThat(command.snapshot().textOpen()).isFalse();
    }

    private void stubTerminalCommit() {
        when(runService.finalizeExecution(eq(instance.run), any(FinalizeCommand.class)))
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
        when(eventStore.appendTerminalIfAbsent(anyString(), anyString(), anyString()))
                .thenReturn(new ChatRunEvent(8L, "run-1:8", "RUN_FINISHED", "{}"));
        when(eventStore.latestSeq(anyString(), anyLong())).thenReturn(1L);
    }

    private FinalizeCommand captureFinalize() {
        ArgumentCaptor<FinalizeCommand> captor = ArgumentCaptor.forClass(FinalizeCommand.class);
        verify(runService).finalizeExecution(eq(instance.run), captor.capture());
        return captor.getValue();
    }

    private ChatRunInstance newInstance(ChatRunEntity run) {
        ChatRunInstance created = new ChatRunInstance(
                runService,
                eventStore,
                new AiProperties(),
                scheduler,
                workspaceAuditRecorder,
                () -> executions.remove(run.getId()),
                run,
                session(),
                adapter,
                new ChatRunSnapshotAccumulator(ExecutionSnapshotCodec.decode(run.getSnapshotJson())));
        executions.put(run.getId(), created);
        return created;
    }

    private static AgentEvent agentEnd(String source) {
        // 单参构造为根 Agent（source=null）；withSource 标记子 Agent 来源。
        AgentEvent event = new AgentEndEvent("reply-1");
        return source == null ? event : event.withSource(source);
    }

    private static ExecutionSnapshot snapshot(String text, boolean textOpen, boolean reasoningOpen) {
        return new ExecutionSnapshot(
                "run-1",
                "agui-1",
                1,
                text,
                "",
                textOpen ? "m-text" : null,
                reasoningOpen ? "m-reason" : null,
                textOpen,
                reasoningOpen,
                List.of(),
                List.of());
    }

    private static ChatRunEntity run(ChatRunStatus status, ExecutionSnapshot snapshot) {
        ChatRunEntity run = new ChatRunEntity();
        run.setId("run-1");
        run.setSessionId("session-1");
        run.setStatus(status.name());
        run.setPhaseNo(1);
        run.setAguiRunId("agui-1");
        run.setSnapshotSeq(0L);
        run.setSnapshotJson(ExecutionSnapshotCodec.encode(snapshot));
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
