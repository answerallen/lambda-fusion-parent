package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventCursor;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.model.PreparedConfirmation;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.springframework.stereotype.Component;

/**
 * 对话执行协调器。
 *
 * <p>负责执行实例的创建、恢复、停止和定时维护。Agent 事件流由执行实例持有，不依赖 SSE 订阅的生命周期。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRunCoordinator {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final ChatRunInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final ConcurrentMap<String, ChatRunInstance> executions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chat-run-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 启动处于创建状态的运行。
     *
     * @param run 运行实体
     * @param session 会话实体
     */
    public void startIfCreated(ChatRunEntity run, ChatSessionEntity session) {
        TenantUtils.withTenant(session.getTenantId(), () -> {
            startIfCreatedInTenantContext(run, session);
            return null;
        });
    }

    void startIfCreated(ChatRunEntity run) {
        startIfCreated(run, loadSession(run));
    }

    private synchronized void startIfCreatedInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (!ChatRunStatus.CREATED.name().equals(run.getStatus())) {
            return;
        }
        try {
            enforceCapacity(run, session);
        } catch (RuntimeException capacityFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler, executions);
            rejected.finalizeFailed("RUN_CAPACITY_EXCEEDED", safeMessage(capacityFailure));
            return;
        }
        eventStore.initialize(run.getId(), sequenceFallback(run));
        ChatRunInstance candidate;
        try {
            candidate = instanceFactory.restoreExecution(run, session, scheduler, executions);
        } catch (RuntimeException restoreFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler, executions);
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed("START_FAILED", safeMessage(restoreFailure));
            }
            return;
        }
        ChatRunInstance execution = executions.putIfAbsent(run.getId(), candidate);
        if (execution == null && !startCreated(candidate)) {
            executions.remove(run.getId(), candidate);
        }
    }

    /**
     * 校验用户确认命令并准备 Agent 确认结果。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param command 用户确认命令
     * @return 已校验的确认结果
     * @throws AiBusinessException 运行状态、阶段或工具调用上下文不一致
     */
    public PreparedConfirmation prepareConfirmation(
            ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        return TenantUtils.withTenant(session.getTenantId(), () -> {
            try {
                return prepareConfirmationInContext(run, session, command);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * 根据已提交的确认状态启动下一执行阶段。
     *
     * @param run 更新后的运行实体
     * @param session 会话实体
     * @param prepared 已校验的确认结果
     */
    public void resumePrepared(ChatRunEntity run, ChatSessionEntity session, PreparedConfirmation prepared) {
        TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatRunInstance selected = null;
            try {
                selected = instanceFactory.selectOrRestore(run, session, scheduler, executions);
                selected.startConfirmedPhase(run, prepared);
            } catch (RuntimeException startFailure) {
                ChatRunInstance failed = selected == null
                        ? instanceFactory.restoreFinalizer(run, session, scheduler, executions)
                        : selected;
                failed.finalizeFailed("START_FAILED", safeMessage(startFailure));
            }
            return null;
        });
    }

    /**
     * 订阅指定序号之后的运行事件。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 事件订阅
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, afterSeq, consumer, failureConsumer);
    }

    /**
     * 校验 SSE 恢复游标。
     *
     * @param run 运行实体
     * @param afterSeq 已消费的事件序号
     * @param bootstrap 是否返回引导事件
     * @throws AiBusinessException 非引导模式下游标不在当前事件窗口内
     */
    public void validateCursor(ChatRunEntity run, long afterSeq, boolean bootstrap) {
        if (bootstrap) {
            return;
        }
        ChatRunEventCursor window = eventStore.cursorWindow(run.getId());
        if (afterSeq < window.minSeq() - 1 || afterSeq > window.latestSeq()) {
            throw new AiBusinessException(AiErrorCode.CHAT_RUN_CURSOR_EXPIRED, afterSeq);
        }
    }

    /**
     * 生成运行的 AG-UI 引导事件。
     *
     * @param run 运行实体
     * @return 引导事件批次
     */
    public AguiBootstrap bootstrap(ChatRunEntity run) {
        ChatRunInstance execution = executions.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new AguiBootstrap(
                highWatermark,
                AguiBootstrapEncoder.encode(
                        current, ExecutionSnapshotCodec.decode(current.getSnapshotJson()), highWatermark),
                ChatRunStatus.isTerminal(current.getStatus())
                        || ChatRunStatus.AWAITING_CONFIRM.name().equals(current.getStatus()));
    }

    /**
     * 请求停止运行。
     *
     * @param run 运行实体
     * @param session 会话实体
     */
    public void stop(ChatRunEntity run, ChatSessionEntity session) {
        TenantUtils.withTenant(session.getTenantId(), () -> {
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
        ChatRunInstance execution = executions.get(run.getId());
        if (execution != null) {
            execution.markStopping();
            try {
                execution.checkpointNow();
            } catch (RuntimeException checkpointFailure) {
                log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
            }
        }
        if (execution == null || !execution.isRunning()) {
            ChatRunInstance waiting = execution == null
                    ? instanceFactory.restoreForFinalize(run, session, scheduler, executions)
                    : execution;
            waiting.finalizeStopped("USER_STOP");
            return;
        }
        try {
            execution.interruptAgent();
        } catch (RuntimeException interruptFailure) {
            log.warn("协作式停止Run失败，将等待宽限期后强制停止: runId={}", run.getId(), interruptFailure);
        } finally {
            scheduler.schedule(
                    () -> execution.runInTenant(execution::forceStopIfRunning),
                    properties.getChat().getRun().getStopGraceSeconds(),
                    TimeUnit.SECONDS);
        }
    }

    /**
     * 恢复或终结单个重启前遗留的中断态 Run：持久化存储的待确认 Run 保留，其余按状态终结。
     *
     * <p>供 {@link ChatRunStartupRecovery} 编排调用。
     */
    void recoverInterrupted(ChatRunEntity run) {
        ChatSessionEntity session = loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            recoverInterruptedInTenantContext(run, session);
            return null;
        });
    }

    /** 启动定时维护任务。供 {@link ChatRunStartupRecovery} 编排调用。 */
    void scheduleMaintenance() {
        scheduler.scheduleAtFixedRate(this::maintenance, 5, 5, TimeUnit.SECONDS);
    }

    private void recoverInterruptedInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (shouldRetainAwaitingConfirmation(run)) {
            eventStore.initialize(run.getId(), sequenceFallback(run));
            log.info(
                    "服务重启后保留待确认Run: runId={}, stateStore={}",
                    run.getId(),
                    properties.getStateStore().getType());
            return;
        }
        ChatRunInstance lost = instanceFactory.restoreForFinalize(run, session, scheduler, executions);
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            lost.finalizeStopped("USER_STOP");
        } else if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            lost.finalizeFailed("CONFIRM_CONTEXT_UNAVAILABLE", "服务进程重启，内存中的用户确认上下文已丢失");
        } else {
            lost.finalizeFailed("INSTANCE_LOST", "服务进程重启，对话运行已终止");
        }
    }

    private boolean shouldRetainAwaitingConfirmation(ChatRunEntity run) {
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            return false;
        }
        StateStoreType type = StateStoreType.of(properties.getStateStore().getType());
        return type != null && type != StateStoreType.MEMORY;
    }

    /** 中断活动运行并关闭定时维护线程池。 */
    @PreDestroy
    public void shutdown() {
        executions.values().forEach(execution -> execution.runInTenant(execution::interruptForShutdown));
        scheduler.shutdown();
    }

    private boolean startCreated(ChatRunInstance execution) {
        ChatRunEntity run = execution.run;
        if (!runService.claimCreated(run)) {
            return false;
        }
        run.setStatus(ChatRunStatus.RUNNING.name());
        if (!execution.hasAgent()) {
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
            runService.listCreated().forEach(run -> safelyMaintain(run.getId(), () -> startIfCreated(run)));
            runService
                    .listExpiredConfirmations(LocalDateTime.now())
                    .forEach(run -> safelyMaintain(run.getId(), () -> expireConfirmation(run)));
        } catch (RuntimeException error) {
            log.error("对话Run维护任务失败", error);
        }
    }

    private void expireConfirmation(ChatRunEntity run) {
        ChatSessionEntity session = loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            if (!runService.requestConfirmationTimeout(run, LocalDateTime.now())) {
                return null;
            }
            run.setStatus(ChatRunStatus.STOPPING.name());
            ChatRunInstance execution = executions.computeIfAbsent(
                    run.getId(), ignored -> instanceFactory.restoreForFinalize(run, session, scheduler, executions));
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

    /**
     * 查询最新持久化运行。
     *
     * @param identity 运行标识实体
     * @return 最新运行实体；记录不存在时返回传入实体
     */
    ChatRunEntity loadCurrent(ChatRunEntity identity) {
        ChatRunEntity current = runService.loadCurrent(identity.getId());
        return current == null ? identity : current;
    }

    private ChatSessionEntity loadSession(ChatRunEntity run) {
        return runService.loadSession(run);
    }

    /**
     * 获取运行的快照事件序号。
     *
     * @param run 运行实体
     * @return 快照事件序号；未设置时返回 {@code 0}
     */
    static long sequenceFallback(ChatRunEntity run) {
        return ChatRunInstanceFactory.sequenceFallback(run);
    }

    /**
     * 生成可持久化的错误信息。
     *
     * @param error 异常
     * @return 已清理并限制长度的错误信息
     */
    static String safeMessage(Throwable error) {
        return ChatRunInstanceFactory.safeMessage(error);
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

        ChatRunInstance selected = instanceFactory.selectOrRestore(run, session, scheduler, executions);
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
}
