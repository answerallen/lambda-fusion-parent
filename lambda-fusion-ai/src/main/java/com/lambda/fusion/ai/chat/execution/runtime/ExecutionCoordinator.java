package com.lambda.fusion.ai.chat.execution.runtime;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.execution.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEvent;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventCursorWindow;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventStore;
import com.lambda.fusion.ai.chat.execution.event.ExecutionEventSubscription;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshotSanitizer;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 单实例 Run 执行器。网络订阅者不持有 Agent Flux：SSE 断开仅 detach，Flux 由
 * {@link ExecutionInstance} 持有到业务终态。单次执行的事件消费、检查点与终态提交见 {@link ExecutionInstance}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionCoordinator {

    private final ChatRunStateService runService;
    private final ExecutionEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;
    private final AiProperties properties;
    private final ConcurrentMap<String, ExecutionInstance> executions = new ConcurrentHashMap<>();
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
            ExecutionInstance rejected = restoreFinalizer(run, session);
            rejected.finalizeFailed("RUN_CAPACITY_EXCEEDED", safeMessage(capacityFailure));
            return;
        }
        eventStore.initialize(run.getId(), sequenceFallback(run));
        ExecutionInstance candidate;
        try {
            candidate = restoreExecution(run, session);
        } catch (RuntimeException restoreFailure) {
            ExecutionInstance rejected = restoreFinalizer(run, session);
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed("START_FAILED", safeMessage(restoreFailure));
            }
            return;
        }
        ExecutionInstance execution = executions.putIfAbsent(run.getId(), candidate);
        if (execution == null && !startCreated(candidate)) {
            executions.remove(run.getId(), candidate);
        }
    }

    public PreparedConfirmation prepareConfirmation(
            ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        return withTenant(session, () -> {
            try {
                return prepareConfirmationInContext(run, session, command);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public void resumePrepared(ChatRunEntity run, ChatSessionEntity session, PreparedConfirmation prepared) {
        withTenant(session, () -> {
            ExecutionInstance selected = executions.get(run.getId());
            try {
                if (selected == null) {
                    ExecutionInstance candidate = restoreExecution(run, session);
                    ExecutionInstance existing = executions.putIfAbsent(run.getId(), candidate);
                    selected = existing == null ? candidate : existing;
                }
                selected.startConfirmedPhase(run, prepared);
            } catch (RuntimeException startFailure) {
                ExecutionInstance failed = selected == null ? restoreFinalizer(run, session) : selected;
                failed.finalizeFailed("START_FAILED", safeMessage(startFailure));
            }
            return null;
        });
    }

    public ExecutionEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ExecutionEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, afterSeq, consumer, failureConsumer);
    }

    /** 在 MVC 切换到异步 SSE 响应前完成窗口校验。 */
    public void validateCursor(ChatRunEntity run, long afterSeq, boolean bootstrap) {
        if (bootstrap) {
            return;
        }
        ExecutionEventCursorWindow window = eventStore.cursorWindow(run.getId());
        if (afterSeq < window.minSeq() - 1 || afterSeq > window.latestSeq()) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
        }
    }

    public BootstrapBatch bootstrap(ChatRunEntity run) {
        ExecutionInstance execution = executions.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new BootstrapBatch(
                highWatermark,
                AguiBootstrapEncoder.encode(
                        current, ExecutionSnapshotCodec.decode(current.getSnapshotJson()), highWatermark),
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
        ExecutionInstance execution = executions.get(run.getId());
        if (execution != null) {
            execution.markStopping();
            try {
                execution.checkpointNow();
            } catch (RuntimeException checkpointFailure) {
                log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
            }
        }
        if (execution == null || !execution.isRunning()) {
            ExecutionInstance waiting = execution == null ? restoreFinalizer(run, session) : execution;
            waiting.finalizeStopped("USER_STOP");
            return;
        }
        try {
            execution.agent.getDelegate().interrupt(session.getUserId(), execution.agentSessionId);
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
        for (ChatRunEntity run : runService.listInterruptedOnRestart()) {
            try {
                ChatSessionEntity session = loadSession(run);
                withTenant(session, () -> {
                    if (shouldRetainAwaitingConfirmation(run)) {
                        // ASKING 是工具执行前的持久化暂停点；确认时按需重建 Agent 并校验上下文。
                        eventStore.initialize(run.getId(), sequenceFallback(run));
                        log.info(
                                "服务重启后保留待确认Run: runId={}, stateStore={}",
                                run.getId(),
                                properties.getStateStore().getType());
                        return null;
                    }
                    ExecutionInstance lost = restoreFinalizer(run, session);
                    if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                        lost.finalizeStopped("USER_STOP");
                    } else if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
                        lost.finalizeFailed("CONFIRM_CONTEXT_UNAVAILABLE", "服务进程重启，内存中的用户确认上下文已丢失");
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

    private boolean shouldRetainAwaitingConfirmation(ChatRunEntity run) {
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            return false;
        }
        StateStoreType type = StateStoreType.of(properties.getStateStore().getType());
        return type != null && type != StateStoreType.MEMORY;
    }

    @PreDestroy
    public void shutdown() {
        executions.values().forEach(execution -> execution.runInTenant(execution::interruptForShutdown));
        scheduler.shutdown();
    }

    private boolean startCreated(ExecutionInstance execution) {
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
            ExecutionInstance execution =
                    executions.computeIfAbsent(run.getId(), ignored -> restoreFinalizer(run, session));
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

    /** 汇总执行实例所需的基础设施依赖，供各构造点共享一份装配清单。 */
    private ExecutionInstance.Support support() {
        return new ExecutionInstance.Support(
                runService,
                eventStore,
                properties,
                scheduler,
                workspaceAuditRecorder,
                gatewayProvider,
                agentFactory,
                executions);
    }

    private ExecutionInstance restoreExecution(ChatRunEntity run, ChatSessionEntity session) {
        eventStore.initialize(run.getId(), sequenceFallback(run));
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), tenantId(session));
        return new ExecutionInstance(
                support(),
                run,
                session,
                agent,
                new ExecutionSnapshotAccumulator(ExecutionSnapshotCodec.decode(run.getSnapshotJson())));
    }

    /** 不构建 Agent 的终结器，用于容量拒绝、实例丢失、等待超时及启动构建失败。 */
    private ExecutionInstance restoreFinalizer(ChatRunEntity run, ChatSessionEntity session) {
        eventStore.initialize(run.getId(), sequenceFallback(run));
        return new ExecutionInstance(
                support(),
                run,
                session,
                null,
                new ExecutionSnapshotAccumulator(ExecutionSnapshotCodec.decode(run.getSnapshotJson())));
    }

    ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    private ChatSessionEntity loadSession(ChatRunEntity run) {
        return runService.loadSession(run);
    }

    static String tenantId(ChatSessionEntity session) {
        return StringUtils.defaultIfBlank(session.getTenantId(), "default");
    }

    static long sequenceFallback(ChatRunEntity run) {
        return run.getSnapshotSeq() == null ? 0L : run.getSnapshotSeq();
    }

    static String safeMessage(Throwable error) {
        String message =
                StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName());
        return StringUtils.left(ExecutionSnapshotSanitizer.redactText(message), 1000);
    }

    static <T> T withTenant(ChatSessionEntity session, Callable<T> task) {
        String tenantId = session.getTenantId();
        String previous = TenantContextHolder.getCurrentTenantId();
        try {
            if (StringUtils.isBlank(tenantId)) {
                TenantContextHolder.getInstance().close();
            } else {
                TenantContextHolder.getInstance().setTenantId(tenantId);
            }
            return task.call();
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

    private PreparedConfirmation prepareConfirmationInContext(
            ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_STATE_CONFLICT, run.getStatus());
        }
        if (!Objects.equals(run.getPhaseNo(), command.getPhaseNo())) {
            throw new AiBusinessException(
                    AiErrorCode.CHAT_RUN_STATE_CONFLICT,
                    "phaseNo=" + command.getPhaseNo() + ", current=" + run.getPhaseNo());
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

        Set<String> snapshotIds = ExecutionSnapshotCodec.decode(run.getSnapshotJson()).pendingTools().stream()
                .map(ExecutionSnapshot.Tool::toolCallId)
                .collect(Collectors.toSet());

        ExecutionInstance selected = executions.get(run.getId());
        if (selected == null) {
            ExecutionInstance candidate = restoreExecution(run, session);
            ExecutionInstance existing = executions.putIfAbsent(run.getId(), candidate);
            selected = existing == null ? candidate : existing;
        }
        List<ToolUseBlock> askingBlocks = selected.readAskingToolBlocks();
        Set<String> agentAskingIds =
                askingBlocks.stream().map(ToolUseBlock::getId).collect(Collectors.toSet());

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

        Map<String, ToolUseBlock> blockById = askingBlocks.stream()
                .collect(Collectors.toMap(ToolUseBlock::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ConfirmResult> results = command.getDecisions().stream()
                .map(d -> new ConfirmResult(d.isConfirmed(), blockById.get(d.getToolCallId())))
                .toList();
        return new PreparedConfirmation(run.getId(), command.getPhaseNo(), results);
    }

    public record BootstrapBatch(long highWatermark, List<String> events, boolean phaseClosed) {}

    public record PreparedConfirmation(String runId, int sourcePhaseNo, List<ConfirmResult> results) {
        public PreparedConfirmation {
            results = List.copyOf(results == null ? List.of() : results);
        }
    }
}
