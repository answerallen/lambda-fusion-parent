package com.lambda.fusion.ai.chat.run;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.adapter.AguiEventMapper;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
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
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 单实例 Run 执行器。网络订阅者不持有 Agent Flux：SSE 断开仅 detach，Flux 由本管理器持有到业务终态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRunManager {

    private static final String CHANNEL_ID = "fusion-chat";
    private final ChatRunService runService;
    private final ChatRunEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;
    private final AiProperties properties;
    private final Map<String, Execution> executions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chat-run-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public void ensureStarted(ChatRunEntity run, ChatSessionEntity session) {
        withTenant(session, () -> {
            ensureStartedInContext(run, session);
            return null;
        });
    }

    private void ensureStarted(ChatRunEntity run) {
        ensureStarted(run, loadSession(run));
    }

    private synchronized void ensureStartedInContext(ChatRunEntity run, ChatSessionEntity session) {
        if (!ChatRunStatus.CREATED.name().equals(run.getStatus())) {
            return;
        }
        try {
            enforceCapacity(run, session);
        } catch (RuntimeException capacityFailure) {
            Execution rejected = restoreFinalizer(run, session);
            rejected.finalizeFailed("RUN_CAPACITY_EXCEEDED", safeMessage(capacityFailure));
            return;
        }
        eventStore.initialize(run.getId(), sequenceFallback(run));
        Execution candidate;
        try {
            candidate = restoreExecution(run, session);
        } catch (RuntimeException restoreFailure) {
            Execution rejected = restoreFinalizer(run, session);
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed("START_FAILED", safeMessage(restoreFailure));
            }
            return;
        }
        Execution execution = executions.putIfAbsent(run.getId(), candidate);
        if (execution == null && !startCreated(candidate)) {
            executions.remove(run.getId(), candidate);
        }
    }

    public void resumeConfirmed(ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        withTenant(session, () -> {
            Execution selected = executions.get(run.getId());
            try {
                if (selected == null) {
                    Execution candidate = restoreExecution(run, session);
                    Execution existing = executions.putIfAbsent(run.getId(), candidate);
                    selected = existing == null ? candidate : existing;
                }
                selected.startConfirmedPhase(run, command);
            } catch (RuntimeException startFailure) {
                Execution failed = selected == null ? restoreFinalizer(run, session) : selected;
                failed.finalizeFailed("START_FAILED", safeMessage(startFailure));
            }
            return null;
        });
    }

    public ChatRunEventStore.Subscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, afterSeq, consumer, failureConsumer);
    }

    /** 在 MVC 切换到异步 SSE 响应前完成窗口校验。 */
    public void validateCursor(ChatRunEntity run, long afterSeq, boolean bootstrap) {
        if (bootstrap) {
            return;
        }
        ChatRunEventStore.CursorWindow window = eventStore.cursorWindow(run.getId());
        if (afterSeq < window.minSeq() - 1 || afterSeq > window.latestSeq()) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
        }
    }

    public BootstrapBatch bootstrap(ChatRunEntity run) {
        Execution execution = executions.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new BootstrapBatch(
                highWatermark,
                RunBootstrapEncoder.encode(current, RunSnapshot.fromJson(current.getSnapshotJson()), highWatermark),
                ChatRunStatus.isTerminal(current.getStatus())
                        || ChatRunStatus.AWAITING_CONFIRM.name().equals(current.getStatus()));
    }

    public void stop(ChatRunEntity run, ChatSessionEntity session) {
        withTenant(session, () -> {
            stopInContext(run, session);
            return null;
        });
    }

    private void stopInContext(ChatRunEntity run, ChatSessionEntity session) {
        if (ChatRunStatus.isTerminal(run.getStatus())) {
            return;
        }
        if (!runService.requestStopping(run)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (!ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                return;
            }
        }
        run.setStatus(ChatRunStatus.STOPPING.name());
        Execution execution = executions.get(run.getId());
        if (execution != null) {
            execution.markStopping();
            try {
                execution.checkpointNow();
            } catch (RuntimeException checkpointFailure) {
                log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
            }
        }
        if (execution == null || !execution.isRunning()) {
            Execution waiting = execution == null ? restoreFinalizer(run, session) : execution;
            waiting.finalizeStopped("USER_STOP");
            return;
        }
        try {
            execution.agent.getDelegate().interrupt(session.getUserId(), run.getSessionId());
        } catch (RuntimeException interruptFailure) {
            log.warn("协作式停止Run失败，将等待宽限期后强制停止: runId={}", run.getId(), interruptFailure);
        } finally {
            scheduler.schedule(
                    () -> execution.runInTenant(execution::forceStopIfRunning),
                    properties.getChat().getRun().getStopGraceSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        // 内存执行器不能恢复原 Agent：运行/待确认收敛为失败，已提交的停止意图仍收敛为停止。
        for (ChatRunEntity run : runService.listInterruptedOnRestart()) {
            try {
                ChatSessionEntity session = loadSession(run);
                withTenant(session, () -> {
                    Execution lost = restoreFinalizer(run, session);
                    if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                        lost.finalizeStopped("USER_STOP");
                    } else {
                        lost.finalizeFailed("INSTANCE_LOST", "服务进程重启，对话运行已终止");
                    }
                    return null;
                });
            } catch (RuntimeException recoveryFailure) {
                log.error("恢复遗留对话Run失败: runId={}", run.getId(), recoveryFailure);
            }
        }
        for (ChatRunEntity run : runService.listCreated()) {
            try {
                ensureStarted(run);
            } catch (RuntimeException recoveryFailure) {
                log.error("启动待执行对话Run失败: runId={}", run.getId(), recoveryFailure);
            }
        }
        scheduler.scheduleAtFixedRate(this::maintenance, 5, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executions.values().forEach(execution -> execution.runInTenant(execution::interruptForShutdown));
        scheduler.shutdown();
    }

    private boolean startCreated(Execution execution) {
        ChatRunEntity run = execution.run;
        if (!runService.claimCreated(run)) {
            return false;
        }
        run.setStatus(ChatRunStatus.RUNNING.name());
        if (execution.agent == null) {
            execution.finalizeFailed("RUN_CAPACITY_EXCEEDED", "后台对话Run已达到实例容量上限");
            return true;
        }
        try {
            ChatMessageEntity userMessage = messageService
                    .findByIdAndSession(run.getUserMessageId(), run.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("Run用户消息不存在: " + run.getId()));
            List<ChatAttachmentEntity> attachments = attachmentService.listByMessageIds(List.of(userMessage.getId()));
            AppEntity app = appService.loadById(execution.session.getAppId());
            Msg msg = attachmentMessageBuilder.buildUserMsg(
                    execution.session, app, userMessage.getContent(), attachments);
            execution.startPhase(msg);
        } catch (RuntimeException startFailure) {
            execution.finalizeFailed("START_FAILED", safeMessage(startFailure));
        }
        return true;
    }

    private void enforceCapacity(ChatRunEntity run, ChatSessionEntity session) {
        int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
        if (!executions.containsKey(run.getId()) && executions.size() >= maxGlobal) {
            throw new IllegalStateException("后台对话Run已达到实例上限: " + maxGlobal);
        }
        long userRuns = executions.values().stream()
                .filter(execution -> Objects.equals(execution.session.getTenantId(), session.getTenantId()))
                .filter(execution -> Objects.equals(execution.session.getUserId(), session.getUserId()))
                .filter(execution -> !Objects.equals(execution.run.getId(), run.getId()))
                .count();
        int maxPerUser = properties.getChat().getRun().getMaxActiveRunsPerUser();
        if (userRuns >= maxPerUser) {
            throw new IllegalStateException("当前用户后台对话Run已达到上限: " + maxPerUser);
        }
    }

    private void maintenance() {
        try {
            eventStore.purgeExpired();
            long now = System.nanoTime();
            executions
                    .values()
                    .forEach(execution -> safelyMaintain(
                            execution.run.getId(), () -> execution.runInTenant(() -> execution.checkpointIfDue(now))));
            runService.listCreated().forEach(run -> safelyMaintain(run.getId(), () -> ensureStarted(run)));
            runService
                    .listExpiredConfirmations(LocalDateTime.now())
                    .forEach(run -> safelyMaintain(run.getId(), () -> expireConfirmation(run)));
        } catch (RuntimeException error) {
            log.error("对话Run维护任务失败", error);
        }
    }

    private void expireConfirmation(ChatRunEntity run) {
        ChatSessionEntity session = loadSession(run);
        withTenant(session, () -> {
            if (!runService.requestConfirmationTimeout(run, LocalDateTime.now())) {
                return null;
            }
            run.setStatus(ChatRunStatus.STOPPING.name());
            Execution execution = executions.computeIfAbsent(run.getId(), ignored -> restoreFinalizer(run, session));
            execution.markStopping();
            execution.finalizeStopped("CONFIRM_TIMEOUT");
            return null;
        });
    }

    private void safelyMaintain(String runId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            log.error("维护对话Run失败: runId={}", runId, error);
        }
    }

    private Execution restoreExecution(ChatRunEntity run, ChatSessionEntity session) {
        eventStore.initialize(run.getId(), sequenceFallback(run));
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), tenantId(session));
        return new Execution(
                run, session, agent, new RunSnapshot.Accumulator(RunSnapshot.fromJson(run.getSnapshotJson())));
    }

    /** 不构建 Agent 的终结器，用于容量拒绝、实例丢失、等待超时及启动构建失败。 */
    private Execution restoreFinalizer(ChatRunEntity run, ChatSessionEntity session) {
        eventStore.initialize(run.getId(), sequenceFallback(run));
        return new Execution(
                run, session, null, new RunSnapshot.Accumulator(RunSnapshot.fromJson(run.getSnapshotJson())));
    }

    private ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    private ChatSessionEntity loadSession(ChatRunEntity run) {
        return runService.loadSession(run);
    }

    private Flux<AgentEvent> routeStream(
            ChatRunEntity run, ChatSessionEntity session, HarnessAgent agent, Msg message) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        String tenantId = tenantId(session);
        if (gateway == null) {
            RuntimeContext context = RuntimeContext.builder()
                    .userId(session.getUserId())
                    .sessionId(run.getSessionId())
                    .put(RuntimeProperty.KEY_TENANT_ID, tenantId)
                    .build();
            return agent.streamEvents(message, context);
        }
        String stableAgentId = agentFactory.buildStableAgentId(session.getAppId(), tenantId);
        MsgContext context = new MsgContext(
                CHANNEL_ID,
                tenantId,
                run.getSessionId(),
                null,
                null,
                buildExtra(run, session, stableAgentId, tenantId),
                session.getUserId());
        OutboundAddress outbound = OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + run.getSessionId());
        return gateway.runStream(context, List.of(message), outbound);
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

    private static String tenantId(ChatSessionEntity session) {
        return StringUtils.defaultIfBlank(session.getTenantId(), "default");
    }

    private static long sequenceFallback(ChatRunEntity run) {
        return run.getSnapshotSeq() == null ? 0L : run.getSnapshotSeq();
    }

    private static String safeMessage(Throwable error) {
        String message =
                StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName());
        return StringUtils.left(RunSnapshot.Tool.redactText(message), 1000);
    }

    private static Map<String, String> toPersistedToolCall(RunSnapshot.Tool tool) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("toolCallId", tool.toolCallId());
        record.put("toolCallName", tool.toolCallName());
        record.put("args", tool.args());
        record.put("result", tool.result());
        return record;
    }

    private static <T> void withTenant(ChatSessionEntity session, Callable<T> task) {
        String tenantId = session.getTenantId();
        String previous = TenantContextHolder.getCurrentTenantId();
        try {
            if (StringUtils.isBlank(tenantId)) {
                TenantContextHolder.getInstance().close();
                task.call();
                return;
            }
            TenantContextHolder.getInstance().setTenantId(tenantId);
            task.call();
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new IllegalStateException("恢复对话租户上下文失败", exception);
        } finally {
            TenantContextHolder.getInstance().close();
            if (StringUtils.isNotBlank(previous)) {
                TenantContextHolder.getInstance().setTenantId(previous);
            }
        }
    }

    public record BootstrapBatch(long highWatermark, List<String> events, boolean phaseClosed) {}

    private final class Execution {
        private final ChatRunEntity run;
        private final ChatSessionEntity session;
        private final HarnessAgent agent;
        private final RunSnapshot.Accumulator accumulator;
        private final AtomicBoolean phaseFinished = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean terminalCommitted = new AtomicBoolean();
        private final AtomicInteger finalizeAttempts = new AtomicInteger();
        private final AtomicReference<Disposable> disposable = new AtomicReference<>();
        private final long turnStartMillis = System.currentTimeMillis();
        private long lastCheckpointNanos = System.nanoTime();
        private AguiEventMapper mapper;
        private List<AguiEvent> pendingConfirmEvents;

        private Execution(
                ChatRunEntity run, ChatSessionEntity session, HarnessAgent agent, RunSnapshot.Accumulator accumulator) {
            this.run = run;
            this.session = session;
            this.agent = agent;
            this.accumulator = accumulator;
            this.mapper = new AguiEventMapper(run.getSessionId(), run.getAguiRunId(), true);
        }

        void runInTenant(Runnable task) {
            ChatRunManager.withTenant(session, () -> {
                task.run();
                return null;
            });
        }

        synchronized void startConfirmedPhase(ChatRunEntity updated, ConfirmToolCall command) {
            if (isRunning() || terminal.get()) {
                return;
            }
            if (agent == null) {
                finalizeFailed("START_FAILED", "Agent未能恢复");
                return;
            }
            run.setStatus(updated.getStatus());
            run.setPhaseNo(updated.getPhaseNo());
            run.setAguiRunId(updated.getAguiRunId());
            accumulator.beginPhase(run.getAguiRunId(), run.getPhaseNo());
            mapper = new AguiEventMapper(run.getSessionId(), run.getAguiRunId(), true);
            phaseFinished.set(false);
            List<ToolUseBlock> pending = deserializePendingFromAgentState();
            List<ConfirmResult> results = new ArrayList<>();
            for (ConfirmToolCall.Decision decision : command.getDecisions()) {
                ToolUseBlock target = pending.stream()
                        .filter(block -> Objects.equals(block.getId(), decision.getToolCallId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("确认工具上下文不存在: " + decision.getToolCallId()));
                results.add(new ConfirmResult(decision.isConfirmed(), target));
            }
            Msg confirm = UserMessage.builder()
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                    .build();
            startPhase(confirm);
        }

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
                accumulator.updateTools(mapper.getToolCalls());
                accumulator.awaiting(confirm.getToolCalls());
                // AgentScope 会在本次 Flux 自然完成前保存 ASKING 状态。这里不能 dispose，
                // AWAITING_CONFIRM 与 interrupt 事件统一在 onComplete 中发布。
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
                finalizeFailed("ERROR", safeMessage(error));
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
            LocalDateTime deadline = LocalDateTime.now()
                    .plusSeconds(properties.getChat().getRun().getAwaitConfirmTimeoutSeconds());
            RunSnapshot snapshot = accumulator.snapshot();
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
            if (every > 0 && seq - sequenceFallback(run) >= every) {
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

        synchronized void finalizeCompleted() {
            finalizeTerminal(ChatRunStatus.COMPLETED, "SUCCESS", null, null);
        }

        synchronized void finalizeStopped(String reason) {
            finalizeTerminal(ChatRunStatus.STOPPED, reason, null, null);
        }

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
                RunSnapshot snapshot = terminalCommitted.get()
                        ? RunSnapshot.fromJson(loadCurrent(run).getSnapshotJson())
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
                    accumulator.updateTools(mapper.getToolCalls());
                    snapshot = accumulator.snapshot();
                    long beforeTerminal = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
                    String toolJson = snapshot.tools().isEmpty()
                            ? null
                            : JsonUtils.getJsonCodec()
                                    .toJson(snapshot.tools().stream()
                                            .map(ChatRunManager::toPersistedToolCall)
                                            .toList());
                    ChatRunService.FinalizeResult result = runService.finalizeRun(
                            run, status, reason, snapshot, toolJson, beforeTerminal, errorCode, errorMessage);
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
                        snapshot = RunSnapshot.fromJson(persisted.getSnapshotJson());
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
                                run.getSessionId(),
                                run.getAguiRunId(),
                                null,
                                new AguiEvent.RunFinishedSuccessOutcome());
                String json = AguiJson.withTerminalMetadata(
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

        synchronized BootstrapBatch bootstrap() {
            long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
            return new BootstrapBatch(
                    highWatermark,
                    RunBootstrapEncoder.encode(run, accumulator.snapshot(), highWatermark),
                    ChatRunStatus.isTerminal(run.getStatus())
                            || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus()));
        }

        synchronized RunSnapshot snapshot() {
            return accumulator.snapshot();
        }

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

        synchronized void checkpointIfDue(long nowNanos) {
            if (terminal.get() || ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                return;
            }
            long interval =
                    TimeUnit.SECONDS.toNanos(properties.getChat().getRun().getSnapshotIntervalSeconds());
            if (nowNanos - lastCheckpointNanos >= interval) {
                checkpointNow();
            }
        }

        boolean isRunning() {
            Disposable current = disposable.get();
            return current != null && !current.isDisposed();
        }

        synchronized void markStopping() {
            run.setStatus(ChatRunStatus.STOPPING.name());
        }

        void forceStopIfRunning() {
            Disposable current = disposable.getAndSet(null);
            if (current != null && !current.isDisposed()) {
                current.dispose();
            }
            finalizeStopped("USER_STOP");
        }

        void interruptForShutdown() {
            try {
                runService.checkpoint(run, snapshot(), eventStore.latestSeq(run.getId(), run.getSnapshotSeq()));
                if (agent != null) {
                    agent.getDelegate().interrupt(session.getUserId(), run.getSessionId());
                }
            } catch (RuntimeException error) {
                log.warn("停机中断Run失败: runId={}", run.getId(), error);
            }
        }

        private List<ToolUseBlock> deserializePendingFromAgentState() {
            // 快照只保留脱敏工具信息；完整 ToolUseBlock 由 Agent 状态保存，按 session 读取。
            if (agent == null) {
                return List.of();
            }
            try {
                var state = agent.getDelegate().getAgentState(session.getUserId(), run.getSessionId());
                if (state == null || state.getContext() == null) {
                    return List.of();
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
            } catch (RuntimeException error) {
                log.warn("读取HITL Agent状态失败: runId={}", run.getId(), error);
            }
            return List.of();
        }
    }
}
