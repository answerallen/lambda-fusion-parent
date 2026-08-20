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
 * 负责对话执行实例的创建、恢复、停止与定时维护。Agent 事件流由执行实例持有，不依赖 SSE 连接生命周期；
 * 业务进入终态且最终源流与后处理全部排空后，协调器才根据 {@code drainedSignal} 摘除对应实例。
 * 同一 {@code (tenantId, userId, sessionId)} 的相邻源流通过进程内非阻塞尾链排序：新运行可以先建立 SSE，
 * 但必须等待上一条源流排空后才能订阅下一次 {@code streamEvents}。
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
    private final SessionSourceTailChain sourceTailChain = new SessionSourceTailChain();
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
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler);
            rejected.finalizeFailed(
                    ChatRunFailureCode.RUN_CAPACITY_EXCEEDED, ChatRunSupport.safeMessage(capacityFailure));
            return;
        }
        ChatRunInstance candidate;
        try {
            candidate = instanceFactory.restoreExecution(run, session, scheduler);
        } catch (RuntimeException restoreFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler);
            if (runService.claimCreated(run)) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunSupport.safeMessage(restoreFailure));
            }
            return;
        }
        ChatRunInstance execution = executions.putIfAbsent(run.getId(), candidate);
        if (execution == null) {
            // 注册成功后监听排空信号，最终源流结束时按实例身份安全摘除。
            subscribeDrained(run.getId(), candidate);
            // 会话尾链等待上一阶段的 phaseDrainedSignal，而不是实例级 drainedSignal；前者在源流终止时即完成，
            // 与终态提交重试解耦，因此前驱排空后即可启动后继，不会被单个实例的终结故障阻塞。
            sourceTailChain.enqueue(
                    sessionKey(session),
                    scheduler,
                    () -> runInTenant(candidate, () -> startCreated(candidate)),
                    candidate.phaseDrainedSignal());
        }
    }

    /** 在候选实例所属租户上下文中执行启动动作（尾链回调可能运行在调度器线程，需重建租户上下文）。 */
    private void runInTenant(ChatRunInstance execution, Runnable task) {
        execution.runInTenant(task);
    }

    /** 查询活动实例；不存在时恢复并注册，并发竞争时使用最先注册的实例。 */
    private ChatRunInstance selectOrRestore(ChatRunEntity run, ChatSessionEntity session) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        // 确认恢复会创建执行实例，因此与 CREATED 启动使用相同的容量约束。
        enforceCapacity(run, session);
        ChatRunInstance candidate = instanceFactory.restoreExecution(run, session, scheduler);
        ChatRunInstance existing = executions.putIfAbsent(run.getId(), candidate);
        if (existing == null) {
            subscribeDrained(run.getId(), candidate);
            return candidate;
        }
        return existing;
    }

    /**
     * 查询活动实例；不存在时恢复一个仅用于终结的实例。终结和停止恢复不受容量上限约束，
     * 避免容量已满时已有运行无法收敛。
     */
    private ChatRunInstance selectOrRestoreForFinalize(ChatRunEntity run, ChatSessionEntity session) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        ChatRunInstance candidate = instanceFactory.restoreForFinalize(run, session, scheduler);
        ChatRunInstance existing = executions.putIfAbsent(run.getId(), candidate);
        if (existing == null) {
            subscribeDrained(run.getId(), candidate);
            return candidate;
        }
        return existing;
    }

    /**
     * 实例注册成功后订阅其排空信号，最终源流和后处理全部结束时按身份摘除。
     * 业务终态信号不会触发摘除，仍在排空的实例会保留到 {@code drainedSignal} 完成。
     */
    private void subscribeDrained(String runId, ChatRunInstance instance) {
        instance.drainedSignal().whenComplete((ignored, error) -> executions.remove(runId, instance));
    }

    /**
     * 在规范实例锁内原子地确认并推进到下一阶段。若上一阶段源流尚未排空，则先受理并暂存确认；
     * 排空后再完成校验、CAS 迁移和下一阶段启动。锁顺序始终为实例 monitor，再进入
     * {@code REQUIRES_NEW} 数据库事务。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示阶段已被处理或已受理待排空
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
        // 获取注册表中的规范实例，后续锁顺序始终为实例 monitor，再进入独立数据库事务。
        ChatRunInstance execution = selectOrRestoreForFinalize(run, session);
        // 在实例锁内同时判断运行状态并迁移到 STOPPING，消除检查与启动新源流之间的竞态窗口。
        ChatRunInstance.StopOutcome outcome = execution.requestStop();
        if (outcome != ChatRunInstance.StopOutcome.INTERRUPT) {
            return;
        }
        // 先在锁外发起协作式中断，再以宽限期后的强制停止作为兜底，避免取消回调造成锁重入。
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

    /** 恢复或终结重启前遗留的中断态运行；持久化存储中的待确认运行保留，其余按状态终结。 */
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
        // 仅用于启动恢复的遗失实例不注册也不订阅排空信号，因此不会修改活动实例注册表。
        ChatRunInstance lost = instanceFactory.restoreForFinalize(run, session, scheduler);
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
            // 运行在排队或认领期间已被并发方终结，本实例未建立源流；仍需完成全部信号，
            // 让协调器摘除实例并释放会话尾链的后继节点。
            execution.releaseNeverStarted();
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

    /** 会话源流尾链的会话键：{@code (tenantId, userId, sessionId)}，与 AgentScope 状态槽一致。 */
    private static SessionKey sessionKey(ChatSessionEntity session) {
        return new SessionKey(ChatRunInstanceFactory.tenantId(session), session.getUserId(), session.getId());
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
            // 取规范实例（注册唯一实例），确认超时的数据库迁移与终态收敛一并移入实例锁。
            ChatRunInstance execution = selectOrRestoreForFinalize(run, session);
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
