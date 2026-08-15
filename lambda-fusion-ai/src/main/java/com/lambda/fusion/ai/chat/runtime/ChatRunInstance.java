package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.agui.AgentEventInterpreter;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.model.ExecutionInterpretation;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.runtime.model.FinalizeResult;
import com.lambda.fusion.ai.chat.runtime.model.PreparedConfirmation;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.util.JsonUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;

/**
 * 单个对话运行的执行实例。
 *
 * <p>负责持有 Agent 事件流、更新执行快照、写入检查点并提交运行终态。生命周期状态由实例锁和原子变量协调。
 *
 * @author Jin
 */
@Slf4j
final class ChatRunInstance {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AiProperties properties;
    private final ScheduledExecutorService scheduler;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ConcurrentMap<String, ChatRunInstance> executions;

    final ChatRunEntity run;
    final ChatSessionEntity session;
    private final AgentExecutionAdapter agentExecutionAdapter;
    private final ChatRunSnapshotAccumulator accumulator;
    private boolean phaseFinished;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private boolean terminalCommitted;
    private int finalizeAttempts;
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    private final long turnStartMillis = System.currentTimeMillis();
    private long lastCheckpointNanos = System.nanoTime();
    private AgentEventInterpreter mapper;
    private ExecutionInterpretation pendingConfirmInterpretation;

    /**
     * 创建执行实例。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param properties AI 模块配置
     * @param scheduler 定时任务执行器
     * @param workspaceAuditRecorder 工作区审计记录器
     * @param executions 活动执行实例集合
     * @param run 运行实体
     * @param session 会话实体
     * @param agentExecutionAdapter Agent 执行适配器；仅执行终结流程时可为 {@code null}
     * @param accumulator 快照累加器
     */
    ChatRunInstance(
            ChatRunStateService runService,
            ChatRunEventStore eventStore,
            AiProperties properties,
            ScheduledExecutorService scheduler,
            WorkspaceAuditRecorder workspaceAuditRecorder,
            ConcurrentMap<String, ChatRunInstance> executions,
            ChatRunEntity run,
            ChatSessionEntity session,
            AgentExecutionAdapter agentExecutionAdapter,
            ChatRunSnapshotAccumulator accumulator) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.properties = properties;
        this.scheduler = scheduler;
        this.workspaceAuditRecorder = workspaceAuditRecorder;
        this.executions = executions;
        this.run = run;
        this.session = session;
        this.agentExecutionAdapter = agentExecutionAdapter;
        this.accumulator = accumulator;
        this.mapper = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
    }

    /**
     * 在运行所属租户上下文中执行任务。
     *
     * @param task 待执行任务
     */
    void runInTenant(Runnable task) {
        TenantUtils.withTenant(session.getTenantId(), task);
    }

    /**
     * 使用已确认的工具调用结果启动下一阶段。
     *
     * @param updated 更新后的运行实体
     * @param prepared 已校验的确认结果
     * @throws IllegalStateException 确认结果与当前运行或阶段不匹配
     */
    synchronized void startConfirmedPhase(ChatRunEntity updated, PreparedConfirmation prepared) {
        if (isRunning() || terminal.get()) {
            return;
        }
        if (agentExecutionAdapter == null) {
            finalizeFailed("START_FAILED", "Agent未能恢复");
            return;
        }
        if (!Objects.equals(run.getId(), prepared.runId())) {
            throw new IllegalStateException("PreparedConfirmation 与当前 Run 不匹配: " + prepared.runId());
        }
        if (prepared.sourcePhaseNo() + 1 != updated.getPhaseNo()) {
            throw new IllegalStateException("PreparedConfirmation 阶段号不匹配: " + prepared.sourcePhaseNo());
        }
        run.setStatus(updated.getStatus());
        run.setPhaseNo(updated.getPhaseNo());
        run.setAguiRunId(updated.getAguiRunId());
        accumulator.beginPhase(run.getAguiRunId(), run.getPhaseNo());
        mapper = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
        phaseFinished = false;
        Msg confirm = UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, prepared.results()))
                .build();
        startPhase(confirm);
    }

    /**
     * 读取 Agent 状态中等待确认的工具调用。
     *
     * @return 待确认工具调用
     * @throws AiBusinessException Agent 状态不可用或不存在待确认工具调用
     */
    synchronized List<ToolUseBlock> readAskingToolBlocks() {
        if (agentExecutionAdapter == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
        }
        return agentExecutionAdapter.readAskingToolBlocks();
    }

    /**
     * 启动一个 Agent 执行阶段。
     *
     * @param message 阶段输入消息
     */
    synchronized void startPhase(Msg message) {
        if (terminal.get() || isRunning()) {
            return;
        }
        if (agentExecutionAdapter == null) {
            finalizeFailed("START_FAILED", "Agent未能初始化");
            return;
        }
        phaseFinished = false;
        disposable.set(null);
        Disposable next = agentExecutionAdapter.stream(message)
                .timeout(Duration.ofSeconds(properties.getChat().getRun().getMaxRunDurationSeconds()))
                .subscribe(
                        event -> runInTenant(() -> onEvent(event)),
                        error -> runInTenant(() -> onError(error)),
                        () -> runInTenant(this::onComplete));
        if (terminal.get()) {
            next.dispose();
        } else {
            disposable.compareAndSet(null, next);
        }
    }

    private synchronized void onEvent(AgentEvent event) {
        if (terminal.get()) {
            return;
        }
        if (event.getType() == AgentEventType.AGENT_END) {
            if (phaseFinished || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                return;
            }
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped("USER_STOP");
            } else {
                finalizeCompleted();
            }
            return;
        }
        ExecutionInterpretation interpretation = mapper.interpret(event);
        if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM) {
            if (pendingConfirmInterpretation != null) {
                return;
            }
            pendingConfirmInterpretation = interpretation;
            accumulator.apply(interpretation.snapshotDelta());
            // AgentScope 在事件流结束时持久化 ASKING 状态。
            phaseFinished = true;
            return;
        }
        accumulator.apply(interpretation.snapshotDelta());
        appendAll(interpretation.events());
        maybeCheckpoint();
    }

    private synchronized void onError(Throwable error) {
        if (terminal.get()) {
            return;
        }
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            finalizeStopped("USER_STOP");
        } else {
            finalizeFailed("ERROR", ChatRunCoordinator.safeMessage(error));
        }
    }

    private synchronized void onComplete() {
        disposable.set(null);
        if (terminal.get()) {
            return;
        }
        if (pendingConfirmInterpretation != null) {
            completeAwaitConfirm();
            return;
        }
        if (!phaseFinished && !terminal.get()) {
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped("USER_STOP");
            } else {
                finalizeCompleted();
            }
        }
    }

    private void completeAwaitConfirm() {
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            pendingConfirmInterpretation = null;
            finalizeStopped("USER_STOP");
            return;
        }
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        LocalDateTime deadline =
                LocalDateTime.now().plusSeconds(properties.getChat().getRun().getAwaitConfirmTimeoutSeconds());
        ExecutionSnapshot snapshot = accumulator.snapshot();
        if (!runService.awaitConfirm(run, snapshot, seq, deadline)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            pendingConfirmInterpretation = null;
            if (ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                finalizeStopped("USER_STOP");
            } else if (!ChatRunStatus.isTerminal(current.getStatus())) {
                finalizeFailed("STATE_CONFLICT", "Run进入待确认状态失败");
            } else {
                executions.remove(run.getId(), this);
            }
            return;
        }
        run.setStatus(ChatRunStatus.AWAITING_CONFIRM.name());
        run.setAwaitConfirmDeadlineAt(deadline);
        run.setSnapshotSeq(seq);
        eventStore.compact(run.getId(), seq);
        List<AguiEvent> events = pendingConfirmInterpretation.events();
        pendingConfirmInterpretation = null;
        appendAll(events);
    }

    private void appendAll(List<AguiEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        boolean checkpointRequired = eventStore.appendAll(
                run.getId(),
                run.getAguiRunId(),
                events.stream().map(mapper::encodeToJson).toList());
        if (checkpointRequired) {
            checkpointNow();
        }
    }

    private void maybeCheckpoint() {
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        int every = properties.getChat().getRun().getSnapshotEveryEvents();
        if (every > 0 && seq - ChatRunCoordinator.sequenceFallback(run) >= every) {
            checkpointNow();
        }
    }

    /**
     * 查询最新持久化运行。
     *
     * @param identity 运行标识实体
     * @return 最新运行实体；记录不存在时返回传入实体
     */
    private ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    /** 将运行终结为完成状态。 */
    synchronized void finalizeCompleted() {
        finalizeTerminal(ChatRunStatus.COMPLETED, "SUCCESS", null, null);
    }

    /**
     * 将运行终结为停止状态。
     *
     * @param reason 停止原因
     */
    synchronized void finalizeStopped(String reason) {
        finalizeTerminal(ChatRunStatus.STOPPED, reason, null, null);
    }

    /**
     * 将运行终结为失败状态。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    synchronized void finalizeFailed(String errorCode, String errorMessage) {
        finalizeTerminal(ChatRunStatus.FAILED, "ERROR", errorCode, errorMessage);
    }

    private synchronized void finalizeTerminal(
            ChatRunStatus status, String reason, String errorCode, String errorMessage) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        phaseFinished = true;
        pendingConfirmInterpretation = null;
        accumulator.closeActiveMessages();
        if (status != ChatRunStatus.COMPLETED) {
            closePendingToolCalls();
        }
        try {
            ExecutionSnapshot snapshot = terminalCommitted
                    ? ExecutionSnapshotCodec.decode(loadCurrent(run).getSnapshotJson())
                    : accumulator.snapshot();
            if (!terminalCommitted) {
                ExecutionInterpretation closeInterpretation = mapper.closeOpenMessages();
                try {
                    appendAll(closeInterpretation.events());
                } catch (RuntimeException closeEventFailure) {
                    log.warn("Run终结前内容关闭事件写入失败，仍继续提交业务终态: runId={}", run.getId(), closeEventFailure);
                }
                accumulator.apply(closeInterpretation.snapshotDelta());
                snapshot = accumulator.snapshot();
                long beforeTerminal = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
                String toolJson = snapshot.tools().isEmpty()
                        ? null
                        : JsonUtils.getJsonCodec()
                                .toJson(snapshot.tools().stream()
                                        .map(ChatRunInstance::toPersistedToolCall)
                                        .toList());
                FinalizeResult result = runService.finalizeExecution(
                        run,
                        new FinalizeCommand(
                                status, reason, snapshot, toolJson, beforeTerminal, errorCode, errorMessage));
                run.setStatus(result.status());
                run.setFinishReason(result.finishReason());
                run.setErrorCode(result.errorCode());
                run.setErrorMessage(result.errorMessage());
                terminalCommitted = true;
                if (result.committed()) {
                    try {
                        workspaceAuditRecorder.recordChanges(session, turnStartMillis);
                    } catch (RuntimeException auditFailure) {
                        log.warn("Run已终结，但工作区审计记录失败: runId={}", run.getId(), auditFailure);
                    }
                } else {
                    ChatRunEntity persisted = loadCurrent(run);
                    run.setAguiRunId(persisted.getAguiRunId());
                    snapshot = ExecutionSnapshotCodec.decode(persisted.getSnapshotJson());
                }
            }
            ChatRunStatus actualStatus = ChatRunStatus.valueOf(run.getStatus());
            AguiEvent terminalEvent = actualStatus == ChatRunStatus.FAILED
                    ? new AguiEvent.RunError(
                            run.getSessionId(),
                            run.getAguiRunId(),
                            StringUtils.defaultIfBlank(run.getErrorMessage(), "对话运行失败"),
                            run.getErrorCode())
                    : new AguiEvent.RunFinished(
                            run.getSessionId(), run.getAguiRunId(), null, new AguiEvent.RunFinishedSuccessOutcome());
            String json = AguiEventJsonCodec.withTerminalMetadata(
                    mapper.encodeToJson(terminalEvent), actualStatus.name(), run.getFinishReason());
            ChatRunEvent appended =
                    eventStore.appendTerminalIfAbsent(run.getId(), run.getAguiRunId(), actualStatus.name(), json);
            runService.recordTerminalSeq(run, snapshot, appended.seq());
            run.setSnapshotSeq(appended.seq());
            eventStore.compact(run.getId(), appended.seq());
            eventStore.markTerminal(
                    run.getId(),
                    Duration.ofSeconds(properties.getChat().getRun().getTerminalTtlSeconds()));
            executions.remove(run.getId(), this);
        } catch (RuntimeException finalizeFailure) {
            terminal.set(false);
            int attempt = ++finalizeAttempts;
            if (!scheduler.isShutdown()) {
                if (attempt == 5 || attempt % 10 == 0) {
                    log.error("对话Run终结持续失败，将继续重试: runId={}, attempt={}", run.getId(), attempt, finalizeFailure);
                } else {
                    log.warn("对话Run终结失败，将重试: runId={}, attempt={}", run.getId(), attempt, finalizeFailure);
                }
                scheduler.schedule(
                        () -> runInTenant(() -> finalizeTerminal(status, reason, errorCode, errorMessage)),
                        Math.min(attempt, 30),
                        TimeUnit.SECONDS);
            } else {
                log.error("应用已停止，Run终结交由下次启动恢复: runId={}", run.getId(), finalizeFailure);
            }
        }
    }

    /**
     * 生成当前运行的 AG-UI 引导事件。
     *
     * @return 引导事件批次
     */
    synchronized AguiBootstrap bootstrap() {
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new AguiBootstrap(
                highWatermark,
                AguiBootstrapEncoder.encode(run, accumulator.snapshot(), highWatermark),
                ChatRunStatus.isTerminal(run.getStatus())
                        || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus()));
    }

    /**
     * 获取当前执行快照。
     *
     * @return 执行快照
     */
    synchronized ExecutionSnapshot snapshot() {
        return accumulator.snapshot();
    }

    /**
     * 立即写入执行检查点并收缩事件缓冲区。
     *
     * @throws IllegalStateException 非终态运行的检查点写入失败
     */
    synchronized void checkpointNow() {
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        if (!runService.checkpoint(run, accumulator.snapshot(), seq)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (!ChatRunStatus.isTerminal(current.getStatus())) {
                throw new IllegalStateException("Run快照检查点未写入: " + run.getId());
            }
        } else {
            run.setSnapshotSeq(seq);
            eventStore.compact(run.getId(), seq);
        }
        lastCheckpointNanos = System.nanoTime();
    }

    /**
     * 在检查点间隔到期时写入执行快照。
     *
     * @param nowNanos 当前单调时钟值
     */
    synchronized void checkpointIfDue(long nowNanos) {
        if (terminal.get() || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            return;
        }
        long interval = TimeUnit.SECONDS.toNanos(properties.getChat().getRun().getSnapshotIntervalSeconds());
        if (nowNanos - lastCheckpointNanos >= interval) {
            checkpointNow();
        }
    }

    /**
     * 判断 Agent 事件流是否正在运行。
     *
     * @return 事件流未结束时返回 {@code true}
     */
    boolean isRunning() {
        Disposable current = disposable.get();
        return current != null && !current.isDisposed();
    }

    /** 判断执行实例是否持有可用的 Agent。 */
    boolean hasAgent() {
        return agentExecutionAdapter != null;
    }

    /** 请求中断 Agent 状态会话。 */
    void interruptAgent() {
        if (agentExecutionAdapter != null) {
            agentExecutionAdapter.interrupt();
        }
    }

    /** 将内存运行状态标记为停止中。 */
    synchronized void markStopping() {
        run.setStatus(ChatRunStatus.STOPPING.name());
    }

    /** 取消活动事件流并终结运行。 */
    void forceStopIfRunning() {
        Disposable current = disposable.getAndSet(null);
        if (current != null && !current.isDisposed()) {
            current.dispose();
        }
        finalizeStopped("USER_STOP");
    }

    /** 保存当前检查点并中断 Agent。 */
    void interruptForShutdown() {
        try {
            runService.checkpoint(run, snapshot(), eventStore.latestSeq(run.getId(), run.getSnapshotSeq()));
            interruptAgent();
        } catch (RuntimeException error) {
            log.warn("停机中断Run失败: runId={}", run.getId(), error);
        }
    }

    /** 闭合 Agent 状态中的未决工具调用：补写拒绝结果，避免遗留调用阻塞该会话的后续对话。 */
    private void closePendingToolCalls() {
        if (agentExecutionAdapter == null) {
            return;
        }
        try {
            agentExecutionAdapter.denyPendingToolCalls();
        } catch (RuntimeException error) {
            log.warn("Run终结时补写未决工具调用失败: runId={}", run.getId(), error);
        }
    }

    private static Map<String, String> toPersistedToolCall(ExecutionSnapshot.Tool tool) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("toolCallId", tool.toolCallId());
        record.put("toolCallName", tool.toolCallName());
        record.put("args", tool.args());
        record.put("result", tool.result());
        return record;
    }
}
