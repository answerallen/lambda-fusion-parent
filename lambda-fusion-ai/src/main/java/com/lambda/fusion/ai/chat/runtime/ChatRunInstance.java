package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
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
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.util.JsonUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
    private final CompletableFuture<Void> terminalSignal = new CompletableFuture<>();

    final ChatRunEntity run;
    final ChatSessionEntity session;
    private final AgentExecutionAdapter agentExecutionAdapter;
    private final ChatRunSnapshotAccumulator accumulator;
    private boolean phaseFinished;
    private boolean terminal;
    private boolean terminalCommitted;
    private int finalizeAttempts;
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    private final long turnStartMillis = System.currentTimeMillis();
    private long lastCheckpointNanos = System.nanoTime();
    private AgentEventInterpreter agentEventInterpreter;
    private ExecutionInterpretation pendingConfirmInterpretation;

    /**
     * 创建执行实例。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param properties AI 模块配置
     * @param scheduler 定时任务执行器
     * @param workspaceAuditRecorder 工作区审计记录器
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
            ChatRunEntity run,
            ChatSessionEntity session,
            AgentExecutionAdapter agentExecutionAdapter,
            ChatRunSnapshotAccumulator accumulator) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.properties = properties;
        this.scheduler = scheduler;
        this.workspaceAuditRecorder = workspaceAuditRecorder;
        this.run = run;
        this.session = session;
        this.agentExecutionAdapter = agentExecutionAdapter;
        this.accumulator = accumulator;
        this.agentEventInterpreter = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
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
     * 在实例锁内原子地完成确认：读取 Agent ASKING 状态、三方校验、数据库 CAS、同步内存、启动下一阶段。
     *
     * <p>临界区顺序为「读 Agent 状态 → 三方 ID 校验并构造确认消息 → 数据库 CAS 并等待提交 → 同步实例内
     * Run 状态 → 启动下一阶段」。{@code validateAndBuildMessage} 在 CAS 之前完成，因此 CAS 冲突（含
     * 幂等重放）不产生任何副作用；仅 CAS 成功后的启动失败按 {@code START_FAILED} 收敛。
     *
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理过（幂等重放）
     * @throws AiBusinessException 确认上下文不可用、决策非法或三方工具调用不一致（保持 AWAITING_CONFIRM，不终结）
     */
    synchronized ConfirmTransition confirm(ConfirmToolCall command) {
        Msg confirmMessage = validateAndBuildMessage(command);
        ConfirmTransition transition = runService.advanceConfirmation(run, session, command.getPhaseNo());
        if (!transition.resumed()) {
            syncRun(transition.run());
            return transition;
        }
        syncRun(transition.run());
        try {
            beginConfirmedPhase();
            startPhase(confirmMessage);
        } catch (RuntimeException startFailure) {
            finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSupport.safeMessage(startFailure));
        }
        return transition;
    }

    /**
     * 校验确认命令并构造确认消息。读取 Agent 当前 ASKING 工具调用，要求快照、决策、Agent 三方 ID 一致。
     *
     * @param command 用户确认命令
     * @return 携带确认结果的下一阶段输入消息
     * @throws AiBusinessException 确认上下文不可用、决策非法或三方不一致
     */
    private Msg validateAndBuildMessage(ConfirmToolCall command) {
        if (agentExecutionAdapter == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
        }
        if (command.getDecisions() == null || command.getDecisions().isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "确认决策不能为空");
        }
        Set<String> decidedIds = new HashSet<>();
        for (ConfirmToolCall.Decision decision : command.getDecisions()) {
            if (StringUtils.isBlank(decision.getToolCallId()) || !decidedIds.add(decision.getToolCallId())) {
                throw new AiBusinessException(
                        AiErrorCode.INVALID_PARAMETER, "确认决策必须完整且不能重复: " + decision.getToolCallId());
            }
        }

        Set<String> snapshotIds = new HashSet<>();
        for (ExecutionSnapshot.Tool tool : accumulator.snapshot().pendingTools()) {
            if (!snapshotIds.add(tool.toolCallId())) {
                throw contextMismatch("快照待确认工具ID重复: " + tool.toolCallId());
            }
        }
        List<ToolUseBlock> askingBlocks = agentExecutionAdapter.readAskingToolBlocks();
        Map<String, ToolUseBlock> blockById = new LinkedHashMap<>();
        for (ToolUseBlock block : askingBlocks) {
            if (blockById.put(block.getId(), block) != null) {
                throw contextMismatch("Agent待确认工具ID重复: " + block.getId());
            }
        }
        Set<String> agentAskingIds = blockById.keySet();
        if (!snapshotIds.equals(decidedIds) || !snapshotIds.equals(agentAskingIds)) {
            log.warn(
                    "确认工具上下文不一致: runId={}, phaseNo={}, snapshotCount={}, decisionCount={}, agentAskingCount={}",
                    run.getId(),
                    command.getPhaseNo(),
                    snapshotIds.size(),
                    decidedIds.size(),
                    agentAskingIds.size());
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
        }

        List<ConfirmResult> results = command.getDecisions().stream()
                .map(decision -> new ConfirmResult(decision.isConfirmed(), blockById.get(decision.getToolCallId())))
                .toList();
        return UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
    }

    private AiBusinessException contextMismatch(String detail) {
        log.warn("确认工具上下文不一致: runId={}, phaseNo={}, detail={}", run.getId(), run.getPhaseNo(), detail);
        return new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_MISMATCH, run.getId());
    }

    /** 把数据库迁移后的 Run 状态同步进实例内存对象。 */
    private void syncRun(ChatRunEntity updated) {
        run.setStatus(updated.getStatus());
        run.setPhaseNo(updated.getPhaseNo());
        run.setAguiRunId(updated.getAguiRunId());
    }

    /** CAS 成功后切换累加器与事件解释器到新阶段。 */
    private void beginConfirmedPhase() {
        accumulator.beginPhase(run.getAguiRunId(), run.getPhaseNo());
        agentEventInterpreter = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
        phaseFinished = false;
    }

    /**
     * 启动一个 Agent 执行阶段。
     *
     * @param message 阶段输入消息
     */
    synchronized void startPhase(Msg message) {
        if (terminal || isRunning()) {
            return;
        }
        if (agentExecutionAdapter == null) {
            finalizeFailed(ChatRunFailureCode.START_FAILED, "Agent未能初始化");
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
        if (terminal) {
            next.dispose();
        } else {
            disposable.compareAndSet(null, next);
        }
    }

    private synchronized void onEvent(AgentEvent event) {
        if (terminal) {
            return;
        }
        if (event.getType() == AgentEventType.AGENT_END) {
            if (event.getSource() != null) {
                // 子 Agent 结束不代表逻辑 Run 结束，忽略。
                return;
            }
            if (phaseFinished || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                return;
            }
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped(ChatRunFinishReason.USER_STOP);
            } else {
                finalizeCompleted();
            }
            return;
        }
        ExecutionInterpretation interpretation = agentEventInterpreter.interpret(event);
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
        if (terminal) {
            return;
        }
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            finalizeStopped(ChatRunFinishReason.USER_STOP);
        } else {
            finalizeFailed(ChatRunFailureCode.ERROR, ChatRunSupport.safeMessage(error));
        }
    }

    private synchronized void onComplete() {
        disposable.set(null);
        if (terminal) {
            return;
        }
        if (pendingConfirmInterpretation != null) {
            completeAwaitConfirm();
            return;
        }
        if (!phaseFinished && !terminal) {
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped(ChatRunFinishReason.USER_STOP);
            } else {
                finalizeCompleted();
            }
        }
    }

    private void completeAwaitConfirm() {
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            pendingConfirmInterpretation = null;
            finalizeStopped(ChatRunFinishReason.USER_STOP);
            return;
        }
        ExecutionSnapshot snapshot = accumulator.snapshot();
        // 先发确认中断事件、再按其后的最新序号持久化待确认：使快照序号覆盖中断事件，
        // 避免「快照已存、中断事件未到」的错位（不再依赖先存序号再发事件的隐式顺序）。
        List<AguiEvent> events = pendingConfirmInterpretation.events();
        appendAll(events);
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        LocalDateTime deadline =
                LocalDateTime.now().plusSeconds(properties.getChat().getRun().getAwaitConfirmTimeoutSeconds());
        if (!runService.awaitConfirm(run, snapshot, seq, deadline)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            pendingConfirmInterpretation = null;
            if (ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                finalizeStopped(ChatRunFinishReason.USER_STOP);
            } else if (!ChatRunStatus.isTerminal(current.getStatus())) {
                finalizeFailed(ChatRunFailureCode.STATE_CONFLICT, "Run进入待确认状态失败");
            } else {
                terminalSignal.complete(null);
            }
            return;
        }
        run.setStatus(ChatRunStatus.AWAITING_CONFIRM.name());
        run.setAwaitConfirmDeadlineAt(deadline);
        run.setSnapshotSeq(seq);
        eventStore.compact(run.getId(), seq);
        pendingConfirmInterpretation = null;
    }

    private void appendAll(List<AguiEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        boolean checkpointRequired = eventStore.appendAll(run.getId(), run.getAguiRunId(), events);
        if (checkpointRequired) {
            checkpointNow();
        }
    }

    private void maybeCheckpoint() {
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        int every = properties.getChat().getRun().getSnapshotEveryEvents();
        if (every > 0 && seq - ChatRunSupport.sequenceFallback(run) >= every) {
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
        finalizeTerminal(ChatRunStatus.COMPLETED, ChatRunFinishReason.SUCCESS, null, null);
    }

    /**
     * 将运行终结为停止状态。
     *
     * @param reason 停止原因
     */
    synchronized void finalizeStopped(ChatRunFinishReason reason) {
        finalizeTerminal(ChatRunStatus.STOPPED, reason, null, null);
    }

    /**
     * 将运行终结为失败状态。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     */
    synchronized void finalizeFailed(ChatRunFailureCode errorCode, String errorMessage) {
        finalizeTerminal(ChatRunStatus.FAILED, ChatRunFinishReason.ERROR, errorCode, errorMessage);
    }

    private synchronized void finalizeTerminal(
            ChatRunStatus status, ChatRunFinishReason reason, ChatRunFailureCode errorCode, String errorMessage) {
        if (terminal) {
            return;
        }
        terminal = true;
        phaseFinished = true;
        pendingConfirmInterpretation = null;
        if (status != ChatRunStatus.COMPLETED) {
            closePendingToolCalls();
        }
        try {
            ExecutionSnapshot snapshot = terminalCommitted
                    ? ExecutionSnapshotCodec.decode(loadCurrent(run).getSnapshotJson())
                    : accumulator.snapshot();
            if (!terminalCommitted) {
                ExecutionInterpretation closeInterpretation = agentEventInterpreter.closeOpenMessages();
                // 先应用快照增量、后写关闭事件：appendAll 可能触发 checkpointNow，
                // 必须保证检查点读到的是已关闭快照，而非「事件已关闭、快照仍打开」。
                accumulator.apply(closeInterpretation.snapshotDelta());
                try {
                    appendAll(closeInterpretation.events());
                } catch (RuntimeException closeEventFailure) {
                    log.warn("Run终结前内容关闭事件写入失败，仍继续提交业务终态: runId={}", run.getId(), closeEventFailure);
                }
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
                    agentEventInterpreter.encodeToJson(terminalEvent), actualStatus.name(), run.getFinishReason());
            ChatRunEvent appended = eventStore.appendTerminalIfAbsent(run.getId(), run.getAguiRunId(), json);
            runService.recordTerminalSeq(run, snapshot, appended.seq());
            run.setSnapshotSeq(appended.seq());
            eventStore.compact(run.getId(), appended.seq());
            eventStore.markTerminal(
                    run.getId(),
                    Duration.ofSeconds(properties.getChat().getRun().getTerminalTtlSeconds()));
            terminalSignal.complete(null);
        } catch (RuntimeException finalizeFailure) {
            terminal = false;
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
     * 暴露只读终态信号：终态提交或确认被并发终结时完成。协调器在实例注册后订阅以摘除注册表项；
     * 未注册实例无人订阅，信号不产生任何效果。实例自身不持有、也不操作注册表。
     *
     * @return 终态信号（只读）
     */
    java.util.concurrent.CompletionStage<Void> terminalSignal() {
        return terminalSignal;
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
        if (terminal || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
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

    /** 取消活动事件流并终结运行。dispose 出锁以避免取消回调回流死锁，DB 终态迁移在锁内完成。 */
    void forceStopIfRunning() {
        Disposable current = disposable.getAndSet(null);
        if (current != null && !current.isDisposed()) {
            current.dispose();
        }
        finalizeStopped(ChatRunFinishReason.USER_STOP);
    }

    /**
     * 在实例锁内原子地停止 Run：先写入检查点，再执行数据库迁移并等待提交，最后收敛为停止终态。
     *
     * <p>数据库迁移（CAS 到 STOPPING / 确认超时）与内存状态修改在同一实例锁内完成，锁顺序恒为
     * 实例 monitor → {@code REQUIRES_NEW} 数据库事务。
     *
     * @param transition 数据库迁移，成功返回 {@code true}；竞争落败返回 {@code false}
     * @param reason 停止原因
     * @return 迁移成功返回 {@code true}；非本实例取胜（竞争落败或已终态）返回 {@code false}
     */
    synchronized boolean stop(Supplier<Boolean> transition, ChatRunFinishReason reason) {
        if (terminal) {
            return false;
        }
        if (isRunning()) {
            try {
                checkpointNow();
            } catch (RuntimeException checkpointFailure) {
                log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
            }
        }
        if (!transition.get()) {
            run.setStatus(loadCurrent(run).getStatus());
            return false;
        }
        run.setStatus(ChatRunStatus.STOPPING.name());
        finalizeStopped(reason);
        return true;
    }

    /** 保存当前检查点并中断 Agent。检查点在实例锁内写入，中断请求保持在锁外以避免回流死锁。 */
    void interruptForShutdown() {
        try {
            checkpointNow();
        } catch (RuntimeException checkpointFailure) {
            log.warn("停机前Run检查点写入失败，仍继续中断: runId={}", run.getId(), checkpointFailure);
        }
        try {
            interruptAgent();
        } catch (RuntimeException interruptFailure) {
            log.warn("停机中断Run失败: runId={}", run.getId(), interruptFailure);
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
