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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;

/**
 * 单个对话运行的执行实例。
 *
 * <p>负责持有 Agent 事件流、更新执行快照、写入检查点并提交运行终态。实例维护两类完成信号：
 * {@code terminalSignal} 表示业务 Run 已进入最终状态（供 API 返回与 SSE 终态），{@code drainedSignal}
 * 表示最终阶段的 AgentScope 源流与后处理（Sandbox 释放、Workspace 审计）全部结束（供实例摘除与关机等待）。
 * 业务终态不 dispose 底层订阅，记忆尾部继续运行；生命周期状态由实例锁和原子变量协调。
 *
 * @author Jin
 */
@Slf4j
final class ChatRunInstance {

    /** 终结数据库提交的最大重试次数：超过后放弃提交但仍完成全部信号，释放实例与会话尾链。 */
    private static final int MAX_FINALIZE_ATTEMPTS = 100;

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AiProperties properties;
    private final ScheduledExecutorService scheduler;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final CompletableFuture<Void> terminalSignal = new CompletableFuture<>();
    private final CompletableFuture<Void> drainedSignal = new CompletableFuture<>();
    private volatile CompletableFuture<Void> phaseDrainedSignal = new CompletableFuture<>();

    final ChatRunEntity run;
    final ChatSessionEntity session;
    private final AgentExecutionAdapter agentExecutionAdapter;
    private final ChatRunSnapshotAccumulator accumulator;
    private boolean phaseFinished;
    private boolean rootAgentEnded;
    private boolean terminal;
    private boolean terminalCommitted;
    private boolean sourceActive;
    private int finalizeAttempts;
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> interactionTimeout = new AtomicReference<>();
    private volatile long phaseStartedAtMillis = System.currentTimeMillis();
    private long lastCheckpointNanos = System.nanoTime();
    private AgentEventInterpreter agentEventInterpreter;
    private ExecutionInterpretation pendingConfirmInterpretation;
    private ConfirmToolCall pendingConfirmCommand;

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
     * 在实例锁内处理用户确认。
     *
     * <p>读法 B：确认在旧 phase 排空前到达时只暂存决策、立即返回（{@code resumed=false}），不执行数据库 CAS，
     * Run 保持 {@code AWAITING_CONFIRM}；待当前 phase 源流排空（phase-drained）后才在锁内完成
     * 「校验 → CAS → 启动下一 phase」。若当前无待排空 phase（纯待确认恢复实例），则立即按原路径推进。
     *
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理或已受理待排空
     * @throws AiBusinessException 确认上下文不可用、决策非法或三方工具调用不一致（保持 AWAITING_CONFIRM，不终结）
     */
    synchronized ConfirmTransition confirm(ConfirmToolCall command) {
        if (isDraining()) {
            // 旧 phase 源流尚未排空：暂存确认，立即受理返回，待 phase-drained 后统一推进。
            pendingConfirmCommand = command;
            log.info("Run确认已受理，待当前phase源流排空后恢复: runId={}, phaseNo={}", run.getId(), command.getPhaseNo());
            return new ConfirmTransition(run, session, false);
        }
        return doConfirm(command);
    }

    /** 在实例锁内原子地完成确认：校验、数据库 CAS、同步内存、启动下一阶段。 */
    private ConfirmTransition doConfirm(ConfirmToolCall command) {
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
     * <p>交互超时只约束「订阅到根 {@code AGENT_END}」阶段：订阅时调度 {@code max-run-duration} 截止，
     * 根 {@code AGENT_END} 到达即取消；不再用单个 Reactor {@code timeout()} 包围整条 Flux（记忆尾部不受交互
     * 超时约束）。底层订阅在业务终态后保留，直到源流自然终止。
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
        rootAgentEnded = false;
        sourceActive = true;
        disposable.set(null);
        phaseStartedAtMillis = System.currentTimeMillis();
        scheduleInteractionTimeout();
        Disposable next;
        try {
            next = agentExecutionAdapter.stream(message)
                    .doFinally(signal -> runInTenant(() -> onSourceTerminated()))
                    .subscribe(
                            event -> runInTenant(() -> onEvent(event)),
                            error -> runInTenant(() -> onError(error)),
                            () -> runInTenant(this::onComplete));
        } catch (RuntimeException subscribeFailure) {
            // 装配/订阅同步失败：源流从未建立，doFinally 不会触发，必须立即复位 sourceActive，
            // 让调用方的 finalizeFailed 走「非排空」分支完成 drainedSignal/phaseDrainedSignal。
            sourceActive = false;
            cancelInteractionTimeout();
            throw subscribeFailure;
        }
        if (terminal) {
            next.dispose();
        } else {
            disposable.compareAndSet(null, next);
        }
    }

    /** 调度交互超时：到点若根 AGENT_END 未达则中断当前 phase；根 AGENT_END 到达即取消。 */
    private void scheduleInteractionTimeout() {
        cancelInteractionTimeout();
        if (scheduler.isShutdown()) {
            return;
        }
        long seconds = properties.getChat().getRun().getMaxRunDurationSeconds();
        ScheduledFuture<?> timeout =
                scheduler.schedule(() -> runInTenant(this::onInteractionTimeout), seconds, TimeUnit.SECONDS);
        interactionTimeout.set(timeout);
    }

    private void cancelInteractionTimeout() {
        ScheduledFuture<?> timeout = interactionTimeout.getAndSet(null);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    /** 交互超时：仅在根 AGENT_END 未达时中断当前 phase；根 AGENT_END 后记忆尾部不受其约束。 */
    private synchronized void onInteractionTimeout() {
        if (terminal || rootAgentEnded) {
            return;
        }
        log.warn("Run交互超时，中断当前phase: runId={}", run.getId());
        finalizeFailed(
                ChatRunFailureCode.ERROR,
                "对话运行超过最大时长 " + properties.getChat().getRun().getMaxRunDurationSeconds() + " 秒");
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
            // 根 AGENT_END：业务回答边界。取消交互超时，此后记忆尾部不受其约束。
            rootAgentEnded = true;
            cancelInteractionTimeout();
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
            // 业务终态后的记忆/维护尾部失败：Run 保持 COMPLETED，仅记录后处理错误。
            log.warn("Run业务终态后，记忆/维护尾部失败: runId={}", run.getId(), error);
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
            try {
                completeAwaitConfirm();
            } catch (RuntimeException awaitConfirmFailure) {
                // 待确认落库失败不能穿出 Reactor 回调无人兜底（会导致 Run 永久卡 RUNNING、实例与尾链悬挂）。
                // 收敛为失败终态，复用 finalizeTerminal 的重试/熔断循环，保证终态与排空最终发生。
                log.error("Run进入待确认失败，收敛为失败终态: runId={}", run.getId(), awaitConfirmFailure);
                finalizeFailed(
                        ChatRunFailureCode.AWAIT_CONFIRM_FAILED, ChatRunSupport.safeMessage(awaitConfirmFailure));
            }
            return;
        }
        if (!phaseFinished) {
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped(ChatRunFinishReason.USER_STOP);
            } else {
                finalizeCompleted();
            }
        }
    }

    /**
     * AgentScope 源流终止（complete/error/cancel）后的收尾：此时 Sandbox 已释放，执行当前 phase 的
     * Workspace 审计，随后推进 phase-drained。本方法在每条源流终止时恰好执行一次，与业务终态解耦。
     * 仅当业务终态已提交（最终 phase）时才汇入实例 {@code drainedSignal}；待确认的中间 phase 只推进当前
     * phase 的确认恢复，不摘除实例。
     */
    private synchronized void onSourceTerminated() {
        sourceActive = false;
        cancelInteractionTimeout();
        runWorkspaceAudit();
        // 源流排空：无论业务终态与否，都完成本 phase 的 phaseDrainedSignal 释放会话尾链后继。
        // 该信号与 finalize 重试解耦，保证前驱源流排空后后继即可启动，不被单实例终结故障阻塞。
        phaseDrainedSignal.complete(null);
        if (terminal) {
            // 最终 phase：业务终态已提交，源流排空后完成实例排空信号。
            drainedSignal.complete(null);
            return;
        }
        // 待确认的中间 phase：源流排空后恢复已受理的待启动确认（读法 B）。
        resumePendingConfirmationIfAny();
    }

    /** 执行当前 phase 的 Workspace 审计：源流排空后进行，使用本 phase 起始时刻，失败不回滚业务终态。 */
    private void runWorkspaceAudit() {
        try {
            workspaceAuditRecorder.recordChanges(session, phaseStartedAtMillis);
        } catch (RuntimeException auditFailure) {
            log.warn("源流排空后工作区审计记录失败: runId={}", run.getId(), auditFailure);
        }
    }

    /** phase 源流排空后，若存在已受理的待启动确认，则在锁内推进确认并启动下一 phase（读法 B）。 */
    private void resumePendingConfirmationIfAny() {
        if (pendingConfirmCommand == null || terminal) {
            return;
        }
        ConfirmToolCall command = pendingConfirmCommand;
        pendingConfirmCommand = null;
        // 排空期间 Run 已被停止/确认超时收敛：丢弃该确认（DB 终态由取胜方落好），不再启动新 phase，
        // 也不按 START_FAILED 误终结。
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            log.info("源流排空后确认恢复时Run已离开待确认态，丢弃该确认: runId={}, status={}", run.getId(), run.getStatus());
            return;
        }
        try {
            doConfirm(command);
        } catch (RuntimeException confirmFailure) {
            log.warn("排空后恢复确认失败: runId={}", run.getId(), confirmFailure);
            if (!terminal) {
                finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSupport.safeMessage(confirmFailure));
            }
        }
    }

    /** 是否存在仍在排空的 AgentScope 源流（已订阅但 doFinally 尚未触发，含记忆尾部窗口）。 */
    private boolean isDraining() {
        return sourceActive;
    }

    private void completeAwaitConfirm() {
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            pendingConfirmInterpretation = null;
            finalizeStopped(ChatRunFinishReason.USER_STOP);
            return;
        }
        ExecutionSnapshot snapshot = accumulator.snapshot();
        // 待确认中断事件先暂存（不占可见窗口、不推送订阅者），数据库事实落库成功后才发布：
        // 序号在暂存时分配使快照覆盖中断事件，且事实先于信号外发；落库失败则丢弃，零副作用可重试。
        List<AguiEvent> events = pendingConfirmInterpretation.events();
        LocalDateTime deadline =
                LocalDateTime.now().plusSeconds(properties.getChat().getRun().getAwaitConfirmTimeoutSeconds());
        boolean awaiting;
        try {
            awaiting = eventStore.runExclusive(
                    run.getId(),
                    run.getAguiRunId(),
                    events,
                    () -> runService.awaitConfirm(
                            run, snapshot, eventStore.latestSeq(run.getId(), run.getSnapshotSeq()), deadline));
        } catch (RuntimeException awaitFailure) {
            // 数据库迁移抛错（REQUIRES_NEW 回滚）：暂存事件未发布，Run 仍为 RUNNING 可重试，不落终态。
            pendingConfirmInterpretation = null;
            log.error("Run进入待确认失败，保持运行态可重试: runId={}", run.getId(), awaitFailure);
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_AWAIT_CONFIRM_FAILED, run.getId());
        }
        if (!awaiting) {
            // 并发落败：awaitConfirm 仅当状态前置 RUNNING 不满足时返回 false。按当前真实状态分流。
            pendingConfirmInterpretation = null;
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                finalizeStopped(ChatRunFinishReason.USER_STOP);
            } else if (ChatRunStatus.isTerminal(current.getStatus())) {
                terminalSignal.complete(null);
            } else if (ChatRunStatus.RUNNING.name().equals(current.getStatus())) {
                // 确认竞胜：Run 已被并发确认推进回 RUNNING，下一阶段仍在执行；本实例安全收尾，不误判为失败。
                log.info("Run进入待确认时被并发确认推进，本实例安全收尾: runId={}", run.getId());
            } else {
                finalizeFailed(ChatRunFailureCode.STATE_CONFLICT, "Run进入待确认状态失败: " + current.getStatus());
            }
            return;
        }
        long seq = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
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
        pendingConfirmCommand = null;
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
                if (!result.committed()) {
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
            // 业务终态后若无活动源流（如纯终结恢复、源流已先终止、启动同步失败），立即完成排空信号；
            // 否则等待 onSourceTerminated 在源流排空后完成它。phaseDrainedSignal 同步完成以释放会话尾链。
            if (!isDraining()) {
                phaseDrainedSignal.complete(null);
                drainedSignal.complete(null);
            }
        } catch (RuntimeException finalizeFailure) {
            terminal = false;
            int attempt = ++finalizeAttempts;
            // 永久性失败（如会话/Run 行已被删除）或重试超过上限：放弃数据库提交，但仍完成全部信号，
            // 释放实例与会话尾链，避免单实例局部故障放大成会话级永久阻塞（见设计 §6.5）。
            if (isPermanentFinalizeFailure(finalizeFailure)
                    || attempt >= MAX_FINALIZE_ATTEMPTS
                    || scheduler.isShutdown()) {
                log.error(
                        "对话Run终结放弃数据库提交，释放实例与尾链: runId={}, attempt={}, permanent={}",
                        run.getId(),
                        attempt,
                        isPermanentFinalizeFailure(finalizeFailure),
                        finalizeFailure);
                terminal = true;
                phaseDrainedSignal.complete(null);
                terminalSignal.complete(null);
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
     * 暴露只读终态信号：业务终态提交或确认被并发终结时完成。供 API 返回与 SSE 终态使用；
     * 不触发底层订阅的 dispose。
     *
     * @return 业务终态信号（只读）
     */
    CompletionStage<Void> terminalSignal() {
        return terminalSignal;
    }

    /**
     * 释放一个「已注册但未能启动」的实例：Run 在排队/认领期间已被并发取胜方终结（如停止），本实例从未
     * 建立源流，无需也不应再写数据库。直接完成全部信号，使协调器摘除实例、会话尾链释放后继。
     * 仅在 {@code !terminal && !sourceActive} 时生效，否则为空操作。
     */
    synchronized void releaseNeverStarted() {
        if (terminal || sourceActive) {
            return;
        }
        terminal = true;
        phaseDrainedSignal.complete(null);
        terminalSignal.complete(null);
        drainedSignal.complete(null);
    }

    /**
     * 暴露只读排空信号：最终阶段的 AgentScope 源流与后处理（Sandbox 释放、Workspace 审计）全部结束时完成。
     * 协调器在实例注册后订阅它以摘除注册表项；未注册实例无人订阅，信号不产生任何效果。
     *
     * @return 排空信号（只读）
     */
    CompletionStage<Void> drainedSignal() {
        return drainedSignal;
    }

    /**
     * 暴露当前 phase 的源流排空信号：该次 AgentScope 源流终止（complete/error/cancel）并完成 Workspace
     * 审计后即完成，与终结重试解耦。会话源流尾链以此信号释放同会话后继——前驱源流排空即可启动下一条，
     * 不被单实例的终结故障或延迟阻塞。
     *
     * @return 当前 phase 的源流排空信号（只读）
     */
    CompletionStage<Void> phaseDrainedSignal() {
        return phaseDrainedSignal;
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

    /**
     * 在实例锁内原子地判定并请求停止：与 {@code confirm}/{@code startPhase} 同一把实例锁，
     * 「是否运行」的判读与「是否可启动新流」共用临界区，消除检查与分流之间的 TOCTOU 间隙。
     *
     * <p>非运行态（含待确认、已中断）在锁内完成 STOPPING 迁移并直接终结；运行态只做 STOPPING
     * 迁移与检查点，返回 {@link StopOutcome#INTERRUPT} 由协调器在锁外中断并留宽限期兜底。
     *
     * @return 停止结果
     */
    synchronized StopOutcome requestStop() {
        if (terminal) {
            return StopOutcome.ALREADY_TERMINAL;
        }
        if (!runService.requestStopping(run)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (ChatRunStatus.isTerminal(current.getStatus())) {
                return StopOutcome.ALREADY_TERMINAL;
            }
            if (!ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                return StopOutcome.NOT_STOPPING;
            }
        }
        run.setStatus(ChatRunStatus.STOPPING.name());
        if (!isRunning()) {
            // 锁内判读：此刻 confirm/startPhase 无法并发改 disposable，可安全直接终结。
            finalizeStopped(ChatRunFinishReason.USER_STOP);
            return StopOutcome.STOPPED_NOW;
        }
        try {
            checkpointNow();
        } catch (RuntimeException checkpointFailure) {
            log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
        }
        return StopOutcome.INTERRUPT;
    }

    /** 停止请求结果。 */
    enum StopOutcome {
        /** 运行态：已迁 STOPPING，需协调器在锁外中断并留宽限期。 */
        INTERRUPT,
        /** 非运行态：已在实例锁内直接终结。 */
        STOPPED_NOW,
        /** Run 已处于终态，无需停止。 */
        ALREADY_TERMINAL,
        /** 并发方已推进到其他非 STOPPING 状态，本次停止未取胜。 */
        NOT_STOPPING
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
