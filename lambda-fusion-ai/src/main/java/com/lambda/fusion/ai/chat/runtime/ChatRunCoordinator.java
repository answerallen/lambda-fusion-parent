package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiConstants.ChatRunFinishReason;
import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.ConfirmTransition;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.agui.AguiBootstrapEncoder;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.message.Msg;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler, noopTerminal());
            rejected.finalizeFailed(
                    ChatRunFailureCode.RUN_CAPACITY_EXCEEDED, ChatRunSupport.safeMessage(capacityFailure));
            return;
        }
        ChatRunInstance candidate;
        try {
            candidate = instanceFactory.restoreExecution(run, session, scheduler, onTerminal(run.getId()));
        } catch (RuntimeException restoreFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler, noopTerminal());
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSupport.safeMessage(restoreFailure));
            }
            return;
        }
        ChatRunInstance execution = executions.putIfAbsent(run.getId(), candidate);
        if (execution == null && !startCreated(candidate)) {
            executions.remove(run.getId(), candidate);
        }
    }

    /** 查询活动实例；不存在时恢复一个并注册（并发竞争下取先注册者）。注册表由本协调器唯一持有。 */
    private ChatRunInstance selectOrRestore(ChatRunEntity run, ChatSessionEntity session) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        ChatRunInstance candidate = instanceFactory.restoreExecution(run, session, scheduler, onTerminal(run.getId()));
        ChatRunInstance existing = executions.putIfAbsent(run.getId(), candidate);
        return existing == null ? candidate : existing;
    }

    /**
     * 构造终结信号：实例终结时回调，从注册表摘除 {@code runId} 当前登记实例。
     *
     * <p>注册表只登记活跃实例，终结信号由该活跃实例自身触发，故按 runId 摘除即可；{@code remove} 幂等，
     * 终结重试时重复调用安全。竞争落败或从未注册的实例不会触发本信号（它们不会被登记，见各调用点）。
     */
    private Runnable onTerminal(String runId) {
        return () -> executions.remove(runId);
    }

    /** 未注册实例（启动失败路径）的空终结信号：这些实例从未登记，终结时无需摘除。 */
    private Runnable noopTerminal() {
        return () -> {};
    }

    /**
     * 在规范实例锁内原子地确认并推进到下一阶段。
     *
     * <p>先取得 {@code executions} 中唯一的 {@link ChatRunInstance}（不存在则恢复并注册），再在该实例锁内
     * 完成「读 Agent 状态 → 三方校验 → 数据库 CAS → 同步内存 → 启动阶段」，锁顺序恒为 实例 monitor →
     * {@code REQUIRES_NEW} 数据库事务。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理过（幂等重放）
     */
    public ConfirmTransition confirm(ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        return TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatRunInstance execution = selectOrRestore(run, session);
            return execution.confirm(command);
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
        TenantUtils.withTenant(session.getTenantId(), () -> stopInContext(run, session));
    }

    private void stopInContext(ChatRunEntity run, ChatSessionEntity session) {
        if (ChatRunStatus.isTerminal(run.getStatus())) {
            return;
        }
        // 取规范实例（computeIfAbsent 注册唯一实例），锁顺序恒为 实例 monitor → REQUIRES_NEW 数据库事务。
        ChatRunInstance execution = executions.computeIfAbsent(
                run.getId(),
                ignored -> instanceFactory.restoreForFinalize(run, session, scheduler, onTerminal(run.getId())));
        if (!execution.isRunning()) {
            // 非运行态（含待确认、已中断）：数据库迁移到 STOPPING 与终态收敛一并移入实例锁。
            boolean stopped = execution.stop(() -> runService.requestStopping(run), ChatRunFinishReason.USER_STOP);
            if (!stopped && ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
                // 并发方已把 Run 迁到 STOPPING 但尚未终结：由本实例接管终态收敛。
                execution.finalizeStopped(ChatRunFinishReason.USER_STOP);
            }
            return;
        }
        // 运行态：先迁 STOPPING（抢占完成路径），再协作式中断并留宽限期兜底。
        if (!runService.requestStopping(run)) {
            ChatRunEntity current = loadCurrent(run);
            run.setStatus(current.getStatus());
            if (!ChatRunStatus.STOPPING.name().equals(current.getStatus())) {
                return;
            }
        }
        execution.markStopping();
        try {
            execution.checkpointNow();
        } catch (RuntimeException checkpointFailure) {
            log.warn("停止Run前快照写入失败，继续中断执行: runId={}", run.getId(), checkpointFailure);
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
        TenantUtils.withTenant(session.getTenantId(), () -> recoverInterruptedInTenantContext(run, session));
    }

    /** 启动定时维护任务。供 {@link ChatRunStartupRecovery} 编排调用。 */
    void scheduleMaintenance() {
        scheduler.scheduleAtFixedRate(this::maintenance, 5, 5, TimeUnit.SECONDS);
    }

    private void recoverInterruptedInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (shouldRetainAwaitingConfirmation(run)) {
            eventStore.initialize(run.getId(), ChatRunSupport.sequenceFallback(run));
            log.info(
                    "服务重启后保留待确认Run: runId={}, stateStore={}",
                    run.getId(),
                    properties.getStateStore().getType());
            return;
        }
        ChatRunInstance lost = instanceFactory.restoreForFinalize(run, session, scheduler, onTerminal(run.getId()));
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            lost.finalizeStopped(ChatRunFinishReason.USER_STOP);
        } else if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            lost.finalizeFailed(ChatRunFailureCode.CONFIRM_CONTEXT_UNAVAILABLE, "服务进程重启，内存中的用户确认上下文已丢失");
        } else {
            lost.finalizeFailed(ChatRunFailureCode.INSTANCE_LOST, "服务进程重启，对话运行已终止");
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
            execution.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSupport.safeMessage(startFailure));
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
            // 取规范实例（computeIfAbsent 注册唯一实例），确认超时的数据库迁移与终态收敛一并移入实例锁。
            ChatRunInstance execution = executions.computeIfAbsent(
                    run.getId(),
                    ignored -> instanceFactory.restoreForFinalize(run, session, scheduler, onTerminal(run.getId())));
            execution.stop(
                    () -> runService.requestConfirmationTimeout(run, LocalDateTime.now()),
                    ChatRunFinishReason.CONFIRM_TIMEOUT);
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
}
