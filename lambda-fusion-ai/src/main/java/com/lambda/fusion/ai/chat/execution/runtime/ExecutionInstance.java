package com.lambda.fusion.ai.chat.execution.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.execution.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.execution.agui.AguiEventJsonCodec;
import com.lambda.fusion.ai.chat.execution.agui.AguiEventMapper;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEvent;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventStore;
import com.lambda.fusion.ai.chat.execution.model.FinalizeCommand;
import com.lambda.fusion.ai.chat.execution.model.FinalizeResult;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.SessionIdUtils;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 单个对话运行的执行实例。
 *
 * <p>负责持有 Agent 事件流、更新执行快照、写入检查点并提交运行终态。生命周期状态由实例锁和原子变量协调。
 *
 * @author Jin
 */
@Slf4j
final class ExecutionInstance {

    private static final String CHANNEL_ID = "fusion-chat";

    /**
     * 执行实例依赖。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param properties AI 模块配置
     * @param scheduler 定时任务执行器
     * @param workspaceAuditRecorder 工作区审计记录器
     * @param gatewayProvider Agent 网关提供器
     * @param agentFactory Agent 工厂
     * @param executions 活动执行实例集合
     */
    record Support(
            ChatRunStateService runService,
            ExecutionEventStore eventStore,
            AiProperties properties,
            ScheduledExecutorService scheduler,
            WorkspaceAuditRecorder workspaceAuditRecorder,
            ObjectProvider<HarnessGateway> gatewayProvider,
            AgentFactory agentFactory,
            ConcurrentMap<String, ExecutionInstance> executions) {}

    private final ChatRunStateService runService;
    private final ExecutionEventStore eventStore;
    private final AiProperties properties;
    private final ScheduledExecutorService scheduler;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;
    private final AgentFactory agentFactory;
    private final ConcurrentMap<String, ExecutionInstance> executions;

    final ChatRunEntity run;
    final ChatSessionEntity session;
    final HarnessAgent agent;
    private final ExecutionSnapshotAccumulator accumulator;
    private final AtomicBoolean phaseFinished = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean terminalCommitted = new AtomicBoolean();
    private final AtomicInteger finalizeAttempts = new AtomicInteger();
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    private final long turnStartMillis = System.currentTimeMillis();
    private long lastCheckpointNanos = System.nanoTime();
    private AguiEventMapper mapper;
    private List<AguiEvent> pendingConfirmEvents;

    final String agentSessionId;

    /**
     * 创建执行实例。
     *
     * @param support 执行实例依赖
     * @param run 运行实体
     * @param session 会话实体
     * @param agent Agent 实例；仅执行终结流程时可为 {@code null}
     * @param accumulator 快照累加器
     */
    ExecutionInstance(
            Support support,
            ChatRunEntity run,
            ChatSessionEntity session,
            HarnessAgent agent,
            ExecutionSnapshotAccumulator accumulator) {
        this.runService = support.runService();
        this.eventStore = support.eventStore();
        this.properties = support.properties();
        this.scheduler = support.scheduler();
        this.workspaceAuditRecorder = support.workspaceAuditRecorder();
        this.gatewayProvider = support.gatewayProvider();
        this.agentFactory = support.agentFactory();
        this.executions = support.executions();
        this.run = run;
        this.session = session;
        this.agent = agent;
        this.accumulator = accumulator;
        this.agentSessionId = resolveAgentSessionId(run, session);
        this.mapper = new AguiEventMapper(run.getSessionId(), run.getAguiRunId(), true);
    }

    /**
     * 在运行所属租户上下文中执行任务。
     *
     * @param task 待执行任务
     */
    void runInTenant(Runnable task) {
        ExecutionCoordinator.withTenant(session, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 使用已确认的工具调用结果启动下一阶段。
     *
     * @param updated 更新后的运行实体
     * @param prepared 已校验的确认结果
     * @throws IllegalStateException 确认结果与当前运行或阶段不匹配
     */
    synchronized void startConfirmedPhase(ChatRunEntity updated, ExecutionCoordinator.PreparedConfirmation prepared) {
        if (isRunning() || terminal.get()) {
            return;
        }
        if (agent == null) {
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
        mapper = new AguiEventMapper(run.getSessionId(), run.getAguiRunId(), true);
        phaseFinished.set(false);
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
        if (agent == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
        }
        try {
            var state = agent.getDelegate().getAgentState(session.getUserId(), agentSessionId);
            if (state == null || state.getContext() == null) {
                throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
            }
            for (int i = state.getContext().size() - 1; i >= 0; i--) {
                Msg message = state.getContext().get(i);
                if (message.getRole() != MsgRole.ASSISTANT) {
                    continue;
                }
                List<ToolUseBlock> asking = message.getContent().stream()
                        .filter(block -> block instanceof ToolUseBlock tool
                                && tool.getState() == io.agentscope.core.message.ToolCallState.ASKING)
                        .map(ToolUseBlock.class::cast)
                        .toList();
                if (!asking.isEmpty()) {
                    return asking;
                }
            }
        } catch (AiBusinessException e) {
            throw e;
        } catch (RuntimeException error) {
            log.warn("读取HITL Agent状态失败: runId={}", run.getId(), error);
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
        }
        throw new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, run.getId());
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
        if (agent == null) {
            finalizeFailed("START_FAILED", "Agent未能初始化");
            return;
        }
        phaseFinished.set(false);
        disposable.set(null);
        Disposable next = routeStream(run, session, agent, message)
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
        if (event instanceof TextBlockDeltaEvent delta) {
            accumulator.appendText(resolveMessageId(delta.getReplyId()), delta.getDelta());
        } else if (event instanceof ThinkingBlockDeltaEvent delta) {
            accumulator.appendReasoning(resolveMessageId(delta.getReplyId()), delta.getDelta());
        }
        accumulateToolEvent(event);
        if (event.getType() == AgentEventType.AGENT_END) {
            if (phaseFinished.get() || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                return;
            }
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped("USER_STOP");
            } else {
                finalizeCompleted();
            }
            return;
        }
        if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                && event instanceof RequireUserConfirmEvent confirm) {
            if (pendingConfirmEvents != null) {
                return;
            }
            accumulator.closeActiveMessages();
            pendingConfirmEvents = mapper.map(event);
            accumulator.awaiting(confirm.getToolCalls().stream()
                    .map(tool -> new ExecutionSnapshot.Tool(tool.getId(), tool.getName(), "", "", "asking"))
                    .toList());
            // AgentScope 在事件流结束时持久化 ASKING 状态。
            phaseFinished.set(true);
            return;
        }
        appendAll(mapper.map(event));
        maybeCheckpoint();
    }

    private synchronized void onError(Throwable error) {
        if (terminal.get()) {
            return;
        }
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            finalizeStopped("USER_STOP");
        } else {
            finalizeFailed("ERROR", ExecutionCoordinator.safeMessage(error));
        }
    }

    private synchronized void onComplete() {
        disposable.set(null);
        if (terminal.get()) {
            return;
        }
        if (pendingConfirmEvents != null) {
            completeAwaitConfirm();
            return;
        }
        if (!phaseFinished.get() && !terminal.get()) {
            if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                finalizeStopped("USER_STOP");
            } else {
                finalizeCompleted();
            }
        }
    }

    private void completeAwaitConfirm() {
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            pendingConfirmEvents = null;
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
            pendingConfirmEvents = null;
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
        List<AguiEvent> events = pendingConfirmEvents;
        pendingConfirmEvents = null;
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
        if (every > 0 && seq - ExecutionCoordinator.sequenceFallback(run) >= every) {
            checkpointNow();
        }
    }

    private void accumulateToolEvent(AgentEvent event) {
        if (event instanceof ToolCallStartEvent tool) {
            accumulator.startTool(tool.getToolCallId(), tool.getToolCallName());
        } else if (event instanceof ToolCallDeltaEvent tool) {
            accumulator.appendToolArgs(tool.getToolCallId(), tool.getToolCallName(), tool.getDelta());
        } else if (event instanceof ToolCallEndEvent tool) {
            accumulator.finishToolArgs(tool.getToolCallId(), tool.getToolCallName());
        } else if (event instanceof ToolResultStartEvent tool) {
            accumulator.startTool(tool.getToolCallId(), tool.getToolCallName());
        } else if (event instanceof ToolResultTextDeltaEvent tool) {
            accumulator.appendToolResult(tool.getToolCallId(), tool.getToolCallName(), tool.getDelta());
        } else if (event instanceof ToolResultEndEvent tool) {
            accumulator.finishTool(tool.getToolCallId(), tool.getToolCallName());
        }
    }

    private String resolveMessageId(String replyId) {
        return StringUtils.defaultIfBlank(replyId, run.getAguiRunId());
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
        phaseFinished.set(true);
        pendingConfirmEvents = null;
        accumulator.closeActiveMessages();
        try {
            ExecutionSnapshot snapshot = terminalCommitted.get()
                    ? ExecutionSnapshotCodec.decode(loadCurrent(run).getSnapshotJson())
                    : accumulator.snapshot();
            if (!terminalCommitted.get()) {
                List<AguiEvent> mapped = status == ChatRunStatus.FAILED
                        ? mapper.mapError(new IllegalStateException(errorMessage))
                        : mapper.mapCompletion();
                try {
                    appendAll(mapped.subList(0, Math.max(0, mapped.size() - 1)));
                } catch (RuntimeException closeEventFailure) {
                    log.warn("Run终结前内容关闭事件写入失败，仍继续提交业务终态: runId={}", run.getId(), closeEventFailure);
                }
                snapshot = accumulator.snapshot();
                long beforeTerminal = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
                String toolJson = snapshot.tools().isEmpty()
                        ? null
                        : JsonUtils.getJsonCodec()
                                .toJson(snapshot.tools().stream()
                                        .map(ExecutionInstance::toPersistedToolCall)
                                        .toList());
                FinalizeResult result = runService.finalizeExecution(
                        run,
                        new FinalizeCommand(
                                status, reason, snapshot, toolJson, beforeTerminal, errorCode, errorMessage));
                run.setStatus(result.status());
                run.setFinishReason(result.finishReason());
                run.setErrorCode(result.errorCode());
                run.setErrorMessage(result.errorMessage());
                terminalCommitted.set(true);
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
            ExecutionEvent appended =
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
            int attempt = finalizeAttempts.incrementAndGet();
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
    synchronized ExecutionCoordinator.BootstrapBatch bootstrap() {
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new ExecutionCoordinator.BootstrapBatch(
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
            if (agent != null) {
                agent.getDelegate().interrupt(session.getUserId(), agentSessionId);
            }
        } catch (RuntimeException error) {
            log.warn("停机中断Run失败: runId={}", run.getId(), error);
        }
    }

    private Flux<AgentEvent> routeStream(
            ChatRunEntity run, ChatSessionEntity session, HarnessAgent agent, Msg message) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        String tenantId = ExecutionCoordinator.tenantId(session);
        if (gateway == null) {
            RuntimeContext context = RuntimeContext.builder()
                    .userId(session.getUserId())
                    .sessionId(run.getSessionId())
                    .put(RuntimeProperty.KEY_TENANT_ID, tenantId)
                    .build();
            return agent.streamEvents(message, context);
        }
        MsgContext context = buildMsgContext(run, session);
        OutboundAddress outbound = OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + run.getSessionId());
        return gateway.runStream(context, List.of(message), outbound);
    }

    private MsgContext buildMsgContext(ChatRunEntity run, ChatSessionEntity session) {
        String tenantId = ExecutionCoordinator.tenantId(session);
        String stableAgentId = agentFactory.buildStableAgentId(session.getAppId(), tenantId);
        return new MsgContext(
                CHANNEL_ID,
                tenantId,
                run.getSessionId(),
                null,
                null,
                buildExtra(run, session, stableAgentId, tenantId),
                session.getUserId());
    }

    private String resolveAgentSessionId(ChatRunEntity run, ChatSessionEntity session) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return run.getSessionId();
        }
        MsgContext context = buildMsgContext(run, session);
        return "gw-" + SessionIdUtils.deterministicHash(context.canonicalKey());
    }

    private static Map<String, String> buildExtra(
            ChatRunEntity run, ChatSessionEntity session, String agentId, String tenantId) {
        Map<String, String> extra = new HashMap<>();
        extra.put(RuntimeProperty.KEY_AGENT_ID, agentId);
        extra.put(RuntimeProperty.KEY_APP_ID, session.getAppId());
        extra.put(RuntimeProperty.KEY_LF_SESSION_ID, run.getSessionId());
        extra.put(RuntimeProperty.KEY_TENANT_ID, tenantId);
        return extra;
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
