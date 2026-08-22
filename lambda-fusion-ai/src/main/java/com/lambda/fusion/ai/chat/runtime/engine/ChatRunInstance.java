package com.lambda.fusion.ai.chat.runtime.engine;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.ChatRunDataSanitizer;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.agui.AgentEventInterpretation;
import com.lambda.fusion.ai.chat.runtime.agui.AgentEventInterpreter;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.runtime.engine.hitl.ConfirmationValidator;
import com.lambda.fusion.ai.chat.runtime.engine.stream.AgentStreamLifecycle;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.Msg;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * 单个对话运行的执行实例：持有 Agent 事件流、维护执行快照并提交运行终态。
 *
 * <p>业务终态通过持久化状态和终态事件对外发布；{@code drainedSignal} 仅表示最终阶段的源流与后处理
 * （沙箱释放、工作区审计）均已结束，供协调器摘除实例和关机等待。普通业务终态不会取消底层订阅，
 * 记忆尾部仍可继续执行；HITL 阶段由执行适配器在根
 * {@code AGENT_END} 后结束交互源流，避免记忆尾部阻塞待确认状态。状态由实例锁和原子变量共同协调。
 *
 * @author Jin
 */
@Slf4j
public final class ChatRunInstance {

    /** 终态提交的最大重试次数；达到上限后释放实例，避免局部故障永久占用运行容量。 */
    private static final int MAX_FINALIZE_ATTEMPTS = 100;

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AiProperties properties;
    private final ScheduledExecutorService scheduler;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final CompletableFuture<Void> drainedSignal = new CompletableFuture<>();
    private final ChatRunFinalizer chatRunFinalizer;
    private final AgentStreamLifecycle agentStreamLifecycle;

    private final ChatRunEntity run;
    private final ChatSessionEntity session;
    private final AgentExecutionAdapter agentExecutionAdapter;
    private final ChatRunSnapshotAccumulator accumulator;
    private boolean rootAgentEnded;
    private boolean terminal;
    private boolean sourceActive;
    private int finalizeAttempts;
    private volatile long phaseStartedAtMillis = System.currentTimeMillis();
    private boolean checkpointDirty;
    private ScheduledFuture<?> checkpointTask;
    private AgentEventInterpreter agentEventInterpreter;
    private AgentEventInterpretation pendingConfirmInterpretation;

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
        this.chatRunFinalizer = new ChatRunFinalizer(runService, eventStore, properties);
        this.agentStreamLifecycle = new AgentStreamLifecycle(run.getId(), scheduler, properties);
        this.agentEventInterpreter = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
    }

    /** 运行实体。 */
    public ChatRunEntity run() {
        return run;
    }

    /** 会话实体。 */
    public ChatSessionEntity session() {
        return session;
    }

    /**
     * 在运行所属租户上下文中执行任务。
     *
     * @param task 待执行任务
     */
    public void runInTenant(Runnable task) {
        TenantUtils.withTenant(session.getTenantId(), task);
    }

    /**
     * 在实例锁内处理用户确认。只有旧阶段源流已经完整排空、快照存在待确认投影时才校验并推进；
     * AgentScope ASKING 是底层确认状态，ChatRun 始终保持 RUNNING。已处理的旧阶段按幂等重放返回。
     *
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 仅表示来源阶段已经被处理
     * @throws AiBusinessException 状态/阶段冲突、确认上下文不可用、决策非法或三方工具调用不一致
     */
    public synchronized ConfirmTransition confirm(ConfirmToolCall command) {
        Integer sourcePhaseNo = command == null ? null : command.getPhaseNo();
        if (sourcePhaseNo == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "phaseNo不能为空");
        }
        if (run.getPhaseNo() > sourcePhaseNo) {
            log.info(
                    "Run确认幂等重放，来源阶段已处理: runId={}, sourcePhaseNo={}, currentPhaseNo={}",
                    run.getId(),
                    sourcePhaseNo,
                    run.getPhaseNo());
            return new ConfirmTransition(run, session, false);
        }
        if (!Objects.equals(run.getPhaseNo(), sourcePhaseNo)
                || !ChatRunStatus.RUNNING.name().equals(run.getStatus())
                || accumulator.buildSnapshot().pendingTools().isEmpty()
                || sourceActive) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }

        Msg confirmMessage = validateAndBuildMessage(command);
        ConfirmTransition transition =
                runService.advanceConfirmation(run, session, sourcePhaseNo, accumulator.buildSnapshot());
        if (!transition.resumed()) {
            syncRun(transition.run());
            return transition;
        }
        syncRun(transition.run());
        try {
            beginConfirmedPhase();
            startPhase(confirmMessage);
        } catch (RuntimeException startFailure) {
            finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunDataSanitizer.safeMessage(startFailure));
        }
        return transition;
    }

    /**
     * 校验确认命令并构造确认消息：委托 {@link ConfirmationValidator} 读取 Agent 当前 ASKING 工具调用，
     * 要求快照、决策、Agent 三方 ID 一致。
     *
     * @param command 用户确认命令
     * @return 携带确认结果的下一阶段输入消息
     * @throws AiBusinessException 确认上下文不可用、决策非法或三方不一致
     */
    private Msg validateAndBuildMessage(ConfirmToolCall command) {
        if (agentExecutionAdapter == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
        }
        return ConfirmationValidator.validateAndBuildMessage(
                run,
                accumulator.buildSnapshot().pendingTools(),
                command.getDecisions(),
                () -> agentExecutionAdapter.readAskingToolBlocks());
    }

    /** 将数据库迁移后的运行状态同步到实例内存对象。 */
    private void syncRun(ChatRunEntity updated) {
        run.setStatus(updated.getStatus());
        run.setPhaseNo(updated.getPhaseNo());
        run.setAguiRunId(updated.getAguiRunId());
    }

    /** CAS 成功后切换累加器与事件解释器到新阶段。 */
    private void beginConfirmedPhase() {
        cancelCheckpointTask();
        accumulator.beginPhase(run.getAguiRunId(), run.getPhaseNo());
        checkpointDirty = false;
        agentEventInterpreter = new AgentEventInterpreter(run.getSessionId(), run.getAguiRunId(), true);
    }

    /**
     * 启动一个 Agent 执行阶段。交互超时仅覆盖从订阅开始到根 {@code AGENT_END} 到达的区间：
     * 订阅时注册 {@code max-run-duration} 截止任务，根事件到达后立即取消。普通阶段的底层订阅在业务终态后仍保留，
     * 直到不受交互超时约束的记忆尾部自然结束；HITL 阶段的适配流在根事件处结束。
     *
     * @param message 阶段输入消息
     */
    public synchronized void startPhase(Msg message) {
        if (terminal || isRunning()) {
            return;
        }
        if (agentExecutionAdapter == null) {
            finalizeFailed(ChatRunFailureCode.START_FAILED, "Agent未能初始化");
            return;
        }
        rootAgentEnded = false;
        sourceActive = true;
        agentStreamLifecycle.reset();
        phaseStartedAtMillis = System.currentTimeMillis();
        agentStreamLifecycle.scheduleInteractionTimeout(() -> runInTenant(this::onInteractionTimeout));
        Disposable next;
        try {
            next = agentExecutionAdapter.stream(message)
                    .doFinally(signal -> runInTenant(() -> onSourceTerminated()))
                    .subscribe(
                            event -> runInTenant(() -> onEvent(event)),
                            error -> runInTenant(() -> onError(error)),
                            () -> runInTenant(this::onComplete));
        } catch (RuntimeException subscribeFailure) {
            // 装配或订阅同步失败时源流尚未建立，doFinally 不会触发；立即复位 sourceActive，
            // 使终结流程直接完成 drainedSignal。
            sourceActive = false;
            agentStreamLifecycle.cancelInteractionTimeout();
            throw subscribeFailure;
        }
        if (terminal) {
            next.dispose();
        } else {
            agentStreamLifecycle.adopt(next);
        }
    }

    /** 仅在根 {@code AGENT_END} 尚未到达时处理中断，避免误伤后续记忆尾部。 */
    private void onInteractionTimeout() {
        synchronized (this) {
            if (terminal || rootAgentEnded) {
                return;
            }
            log.warn("Run交互超时，中断当前phase: runId={}", run.getId());
            finalizeFailed(
                    ChatRunFailureCode.ERROR,
                    "对话运行超过最大时长 " + properties.getChat().getRun().getMaxRunDurationSeconds() + " 秒");
        }
        // AgentScope 中断可能触发流回调，必须在实例锁外调用，避免回流等待同一 monitor。
        try {
            interruptAgent();
        } catch (RuntimeException interruptFailure) {
            log.warn("Run交互超时后协作式中断失败，将等待宽限期后强制取消: runId={}", run.getId(), interruptFailure);
        }
        // 超时中断宽限期结束后仍有活动源流时强制取消，只释放资源，不覆盖已经提交的失败终态。
        agentStreamLifecycle.scheduleForceDispose(() -> runInTenant(agentStreamLifecycle::forceDispose));
    }

    private synchronized void onEvent(AgentEvent event) {
        if (terminal) {
            return;
        }
        if (event.getType() == AgentEventType.AGENT_END) {
            if (event.getSource() != null) {
                // 子 Agent 结束不代表整个对话运行结束。
                return;
            }
            // 根 AGENT_END 是业务回答边界；普通阶段允许记忆尾部继续，HITL 阶段由适配器在此结束源流。
            rootAgentEnded = true;
            agentStreamLifecycle.cancelInteractionTimeout();
            if (pendingConfirmInterpretation != null || hasPendingConfirmation()) {
                return;
            }
            finalizeCompleted();
            return;
        }
        AgentEventInterpretation interpretation = agentEventInterpreter.interpret(event);
        if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM) {
            if (pendingConfirmInterpretation != null) {
                return;
            }
            pendingConfirmInterpretation = interpretation;
            accumulator.apply(interpretation.snapshotDelta());
            checkpointDirty = true;
            // AgentScope 在事件流结束时持久化 ASKING 状态。
            return;
        }
        accumulator.apply(interpretation.snapshotDelta());
        checkpointDirty = true;
        appendAll(interpretation.events());
        maybeCheckpoint();
    }

    private synchronized void onError(Throwable error) {
        if (terminal) {
            // 业务终态后的记忆或维护任务失败不再改变运行结果，仅记录后处理错误。
            log.warn("Run业务终态后，记忆/维护尾部失败: runId={}", run.getId(), error);
            return;
        }
        finalizeFailed(ChatRunFailureCode.ERROR, ChatRunDataSanitizer.safeMessage(error));
    }

    private synchronized void onComplete() {
        agentStreamLifecycle.clear();
        if (terminal) {
            return;
        }
        if (pendingConfirmInterpretation != null) {
            // 待确认状态必须等 doFinally 确认适配后的当前阶段源流已经结束后再提交。
            return;
        }
        finalizeCompleted();
    }

    /**
     * AgentScope 源流完成、失败或取消后的统一收尾。此时沙箱已释放，方法先执行当前阶段的工作区审计，
     * 再把正常完成的 HITL 阶段提交为待确认。只有最终业务终态才完成实例 {@code drainedSignal}；
     * 待确认阶段保留实例，确认请求随后可立即启动下一阶段。
     */
    private synchronized void onSourceTerminated() {
        sourceActive = false;
        agentStreamLifecycle.cancelInteractionTimeout();
        runWorkspaceAudit();
        if (!terminal && pendingConfirmInterpretation != null) {
            try {
                completeAwaitConfirm();
            } catch (RuntimeException awaitConfirmFailure) {
                // 源流已经排空，待确认事实仍无法提交时转为失败终态，避免运行永久停留在 RUNNING。
                log.error("Run进入待确认失败，收敛为失败终态: runId={}", run.getId(), awaitConfirmFailure);
                finalizeFailed(
                        ChatRunFailureCode.AWAIT_CONFIRM_FAILED, ChatRunDataSanitizer.safeMessage(awaitConfirmFailure));
            }
        }
        if (terminal) {
            // 最终阶段已提交业务终态，源流排空后即可完成实例级排空信号。
            drainedSignal.complete(null);
        }
    }

    /** 源流排空后审计当前阶段的工作区变更；审计失败不回滚业务终态。 */
    private void runWorkspaceAudit() {
        try {
            workspaceAuditRecorder.recordChanges(session, phaseStartedAtMillis);
        } catch (RuntimeException auditFailure) {
            log.warn("源流排空后工作区审计记录失败: runId={}", run.getId(), auditFailure);
        }
    }

    /** 判断是否仍有已订阅但尚未触发 {@code doFinally} 的 AgentScope 源流；普通阶段包括记忆尾部。 */
    private boolean isDraining() {
        return sourceActive;
    }

    private void completeAwaitConfirm() {
        ChatRunSnapshot snapshot = accumulator.buildSnapshot();
        // 待确认中断事件先暂存（不占可见窗口、不推送订阅者），数据库事实落库成功后才发布：
        // 序号在暂存时分配使快照覆盖中断事件，且事实先于信号外发；落库失败则丢弃，零副作用可重试。
        List<AguiEvent> events = pendingConfirmInterpretation.events();
        boolean awaiting;
        try {
            awaiting = eventStore.runExclusive(
                    run.getId(),
                    run.getAguiRunId(),
                    events,
                    () -> runService.checkpoint(
                            run, snapshot, eventStore.latestSeq(run.getId(), run.getSnapshotSeq())));
        } catch (RuntimeException awaitFailure) {
            // 独立事务回滚时暂存事件尚未发布，运行保持 RUNNING，可由调用方重试。
            pendingConfirmInterpretation = null;
            log.error("Run进入待确认失败，保持运行态可重试: runId={}", run.getId(), awaitFailure);
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_AWAIT_CONFIRM_FAILED, run.getId());
        }
        if (!awaiting) {
            // 终态会拒绝迟到的确认快照；不存在第二套待确认状态迁移。
            pendingConfirmInterpretation = null;
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (ChatRunStatus.isTerminal(current.getStatus())) {
                terminal = true;
                drainedSignal.complete(null);
            } else {
                throw new IllegalStateException("Run待确认快照未写入: " + run.getId());
            }
            return;
        }
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        run.setSnapshotSeq(seq);
        eventStore.compact(run.getId(), seq);
        cancelCheckpointTask();
        checkpointDirty = false;
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
        if (!checkpointDirty || terminal || hasPendingConfirmation()) {
            return;
        }
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        int every = properties.getChat().getRun().getSnapshotEveryEvents();
        if (every > 0 && seq - snapshotSeqOrZero(run) >= every) {
            checkpointNow();
            return;
        }
        scheduleCheckpoint();
    }

    /** 由实例自身安排一次延迟检查点，不依赖全局扫描。 */
    private void scheduleCheckpoint() {
        if (checkpointTask != null || scheduler.isShutdown()) {
            return;
        }
        checkpointTask = scheduler.schedule(
                () -> runInTenant(this::checkpointIfDirty),
                properties.getChat().getRun().getSnapshotIntervalSeconds(),
                TimeUnit.SECONDS);
    }

    private synchronized void checkpointIfDirty() {
        checkpointTask = null;
        if (!checkpointDirty || terminal || hasPendingConfirmation()) {
            return;
        }
        try {
            checkpointNow();
        } catch (RuntimeException checkpointFailure) {
            log.warn("Run延迟检查点写入失败，将由实例重试: runId={}", run.getId(), checkpointFailure);
            scheduleCheckpoint();
        }
    }

    private boolean hasPendingConfirmation() {
        return !accumulator.buildSnapshot().pendingTools().isEmpty();
    }

    /** 快照事件序号兜底：未设置时按 0 计。 */
    private static long snapshotSeqOrZero(ChatRunEntity run) {
        return run.getSnapshotSeq() == null ? 0L : run.getSnapshotSeq();
    }

    /** 查询最新持久化运行；不存在时返回传入实体。 */
    private ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    /** 将运行终结为完成状态。 */
    synchronized void finalizeCompleted() {
        finalizeTerminal(ChatRunStatus.COMPLETED, ChatRunFinishReason.SUCCESS, null, null);
    }

    /** 将运行终结为停止状态。 */
    public synchronized void finalizeStopped(ChatRunFinishReason reason) {
        finalizeTerminal(ChatRunStatus.STOPPED, reason, null, null);
    }

    /** 将运行终结为失败状态。 */
    public synchronized void finalizeFailed(ChatRunFailureCode errorCode, String errorMessage) {
        finalizeTerminal(ChatRunStatus.FAILED, ChatRunFinishReason.ERROR, errorCode, errorMessage);
    }

    private synchronized void finalizeTerminal(
            ChatRunStatus status, ChatRunFinishReason reason, ChatRunFailureCode errorCode, String errorMessage) {
        if (terminal) {
            return;
        }
        terminal = true;
        cancelCheckpointTask();
        pendingConfirmInterpretation = null;
        if (status != ChatRunStatus.COMPLETED) {
            closePendingToolCalls();
        }
        try {
            AgentEventInterpretation closeInterpretation = agentEventInterpreter.closeOpenMessages();
            // 先应用快照增量、后写关闭事件：appendAll 可能触发 checkpointNow，
            // 必须保证检查点读到的是已关闭快照，而非「事件已关闭、快照仍打开」。
            accumulator.apply(closeInterpretation.snapshotDelta());
            try {
                appendAll(closeInterpretation.events());
            } catch (RuntimeException closeEventFailure) {
                log.warn("Run终结前内容关闭事件写入失败，仍继续提交业务终态: runId={}", run.getId(), closeEventFailure);
            }
            chatRunFinalizer.commitTerminal(
                    run, accumulator.buildSnapshot(), agentEventInterpreter, status, reason, errorCode, errorMessage);
            // 业务终态后若无活动源流（如纯终结恢复、源流已先终止、启动同步失败），立即完成排空信号；
            // 否则等待 onSourceTerminated 在源流排空后完成它。
            if (!isDraining()) {
                drainedSignal.complete(null);
            }
        } catch (RuntimeException finalizeFailure) {
            terminal = false;
            int attempt = ++finalizeAttempts;
            // 数据行已删除等永久性失败或重试达到上限时停止提交，但仍释放实例，
            // 避免单个实例的终结故障永久占用内存运行容量。
            if (isPermanentFinalizeFailure(finalizeFailure)
                    || attempt >= MAX_FINALIZE_ATTEMPTS
                    || scheduler.isShutdown()) {
                log.error(
                        "对话Run终结放弃数据库提交，释放实例: runId={}, attempt={}, permanent={}",
                        run.getId(),
                        attempt,
                        isPermanentFinalizeFailure(finalizeFailure),
                        finalizeFailure);
                terminal = true;
                if (!isDraining()) {
                    drainedSignal.complete(null);
                }
                return;
            }
            if (attempt == 5 || attempt % 10 == 0) {
                log.error("对话Run终结持续失败，将继续重试: runId={}, attempt={}", run.getId(), attempt, finalizeFailure);
            } else {
                log.warn("对话Run终结失败，将重试: runId={}, attempt={}", run.getId(), attempt, finalizeFailure);
            }
            scheduler.schedule(
                    () -> runInTenant(() -> finalizeTerminal(status, reason, errorCode, errorMessage)),
                    Math.min(attempt, 30),
                    TimeUnit.SECONDS);
        }
    }

    /** 判断终结失败是否为永久性（重试无意义）：底层数据行已不存在等。 */
    private static boolean isPermanentFinalizeFailure(RuntimeException failure) {
        return failure instanceof IllegalStateException;
    }

    /**
     * 排空信号：最终阶段源流与后处理全部结束时完成，协调器订阅以摘除注册表项；未注册实例无人订阅。
     */
    public CompletionStage<Void> drainedSignal() {
        return drainedSignal;
    }

    /** 生成当前运行的 AG-UI 引导事件。 */
    public synchronized AguiBootstrap bootstrap() {
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new AguiBootstrap(
                highWatermark,
                AguiBootstrapEncoder.encode(run, accumulator.buildSnapshot(), highWatermark),
                ChatRunStatus.isTerminal(run.getStatus()) || hasPendingConfirmation());
    }

    /** 立即写入执行检查点并收缩事件缓冲区。抛 {@link IllegalStateException} 表示非终态运行的检查点失败。 */
    synchronized void checkpointNow() {
        cancelCheckpointTask();
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        if (!runService.checkpoint(run, accumulator.buildSnapshot(), seq)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (ChatRunStatus.isTerminal(current.getStatus())) {
                terminal = true;
                checkpointDirty = false;
                if (!isDraining()) {
                    drainedSignal.complete(null);
                }
                return;
            }
            throw new IllegalStateException("Run快照检查点未写入: " + run.getId());
        } else {
            run.setSnapshotSeq(seq);
            eventStore.compact(run.getId(), seq);
            checkpointDirty = false;
        }
    }

    private void cancelCheckpointTask() {
        if (checkpointTask != null) {
            checkpointTask.cancel(false);
            checkpointTask = null;
        }
    }

    /** 判断 Agent 事件流是否正在运行。 */
    boolean isRunning() {
        return agentStreamLifecycle.isRunning();
    }

    /** 请求中断 Agent 状态会话。 */
    public void interruptAgent() {
        if (agentExecutionAdapter != null) {
            agentExecutionAdapter.interrupt();
        }
    }

    /** 取消活动事件流并终结运行。dispose 出锁以避免取消回调回流死锁，DB 终态迁移在锁内完成。 */
    public void forceStopIfRunning() {
        agentStreamLifecycle.forceDispose();
        finalizeStopped(ChatRunFinishReason.USER_STOP);
    }

    /**
     * 在实例锁内直接提交 STOPPED；数据库终态阻止迟到完成结果覆盖。若本地源流仍活动，返回 true 供协调器
     * 在锁外做协作式中断。
     *
     * @return 是否需要协调器在锁外中断活动源流
     */
    public synchronized boolean requestStop() {
        if (terminal) {
            return false;
        }
        boolean interruptRequired = isRunning();
        if (interruptRequired) {
            try {
                checkpointNow();
            } catch (RuntimeException checkpointFailure) {
                log.warn("停止Run前快照写入失败，仍提交停止终态: runId={}", run.getId(), checkpointFailure);
            }
        }
        finalizeStopped(ChatRunFinishReason.USER_STOP);
        return interruptRequired;
    }

    /** 保存当前检查点并中断 Agent。检查点在实例锁内写入，中断请求保持在锁外以避免回流死锁。 */
    public void interruptForShutdown() {
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
}
