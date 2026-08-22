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
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstance;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstanceFactory;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEvent;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventSubscription;
import com.lambda.fusion.ai.chat.runtime.model.AguiBootstrap;
import com.lambda.fusion.ai.chat.runtime.registry.ChatRunInstanceRegistry;
import com.lambda.fusion.ai.chat.runtime.registry.ChatRunMaintenanceScheduler;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.core.utils.TenantUtils;
import io.agentscope.core.message.Msg;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对话运行协调门面：负责运行执行的启动、确认、停止与启动恢复编排。活动实例注册表与容量约束由
 * {@link ChatRunInstanceRegistry} 承载，定时维护与确认超时扫描由 {@link ChatRunMaintenanceScheduler} 承载；
 * Agent 事件流由执行实例持有，不依赖 SSE 连接生命周期。新运行注册后立即异步订阅 {@code streamEvents}；
 * 同一 {@code (userId, sessionId)} 的核心状态调用由 AgentScope 自身串行保护，上一轮记忆整理等后处理
 * 不阻塞下一轮交互。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ChatRunCoordinator {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final ChatMessageService messageService;
    private final ChatAttachmentService attachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final ChatRunInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final ChatRunOwner runOwner;
    private final ChatRunInstanceRegistry registry;
    private final ChatRunMaintenanceScheduler maintenanceScheduler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "chat-run-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 创建协调器。
     *
     * @param runService 运行状态服务
     * @param eventStore 运行事件存储
     * @param messageService 消息服务
     * @param attachmentService 附件服务
     * @param attachmentMessageBuilder 附件消息构造器
     * @param appService 应用服务
     * @param instanceFactory 执行实例工厂
     * @param properties AI 模块配置
     * @param runOwner 本节点执行标识
     */
    public ChatRunCoordinator(
            ChatRunStateService runService,
            ChatRunEventStore eventStore,
            ChatMessageService messageService,
            ChatAttachmentService attachmentService,
            ChatAttachmentMessageBuilder attachmentMessageBuilder,
            AppService appService,
            ChatRunInstanceFactory instanceFactory,
            AiProperties properties,
            ChatRunOwner runOwner) {
        this.runService = runService;
        this.eventStore = eventStore;
        this.messageService = messageService;
        this.attachmentService = attachmentService;
        this.attachmentMessageBuilder = attachmentMessageBuilder;
        this.appService = appService;
        this.instanceFactory = instanceFactory;
        this.properties = properties;
        this.runOwner = runOwner;
        this.registry = new ChatRunInstanceRegistry(instanceFactory, properties);
        this.maintenanceScheduler = new ChatRunMaintenanceScheduler(
                scheduler,
                eventStore,
                registry,
                runService,
                runOwner,
                properties,
                this::startIfCreated,
                this::takeoverIfExpired,
                this::expiredBefore);
    }

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
        // 优雅停机排空中：本节点不再认领新 Run，留待其他节点认领。
        if (runOwner.draining()) {
            return;
        }
        // 集群下本节点满载时跳过该 Run（不改状态），留待空闲节点认领；全节点长期满载由周期调度超时收敛。
        // 仅用户发起的确认仍按容量约束拒绝，CREATED 调度不再终结为 RUN_CAPACITY_EXCEEDED。
        if (!registry.hasCapacityFor(run, session)) {
            return;
        }
        ChatRunInstance candidate;
        try {
            candidate = instanceFactory.restoreExecution(run, session, scheduler);
        } catch (RuntimeException restoreFailure) {
            ChatRunInstance rejected = instanceFactory.restoreFinalizer(run, session, scheduler);
            if (runService.claimCreated(run, runOwner.instanceId(), newLeaseUntil())) {
                run.setStatus(ChatRunStatus.RUNNING.name());
                rejected.finalizeFailed(
                        ChatRunFailureCode.START_FAILED, ChatRunDataSanitizer.safeMessage(restoreFailure));
            }
            return;
        }
        if (registry.register(run.getId(), candidate) == null) {
            // 注册成功后排空信号由注册表监听，最终源流结束时按实例身份安全摘除。
            // 只把实际订阅移出请求线程；不等待上一轮的记忆整理、Workspace 审计等后处理。
            scheduler.execute(() -> candidate.runInTenant(() -> startCreated(candidate)));
        }
    }

    /**
     * 在规范实例锁内原子地确认并推进到下一阶段。只有上一阶段完整排空并进入待确认态后才允许确认；
     * 锁顺序始终为实例 monitor，再进入 {@code REQUIRES_NEW} 数据库事务。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param command 用户确认命令
     * @return 迁移结果；{@code resumed=false} 表示来源阶段已经被处理
     */
    public ConfirmTransition confirm(ChatRunEntity run, ChatSessionEntity session, ConfirmToolCall command) {
        return TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatRunInstance execution = registry.selectOrRestore(run, session, scheduler);
            return execution.confirm(command);
        });
    }

    /**
     * 订阅指定序号之后的运行事件。Redis 后端下任意节点可订阅，并附 DB 复核：空转时复核该 Run 的
     * {@code (status, phaseNo, leaseEpoch)}，变化即发送 RESYNC_REQUIRED 控制事件并断开。
     *
     * @param runId 运行标识
     * @param afterSeq 已消费的事件序号
     * @param consumer 事件消费者
     * @param failureConsumer 发送失败消费者
     * @return 事件订阅
     */
    public ChatRunEventSubscription subscribe(
            String runId, long afterSeq, Consumer<ChatRunEvent> consumer, Consumer<Throwable> failureConsumer) {
        return eventStore.subscribe(runId, afterSeq, consumer, failureConsumer, () -> subscribeRecheck(runId));
    }

    /**
     * 订阅侧的 DB 复核：运行不存在、进入终态、进入待确认（owner 已释放）或 owner/epoch 已易主时返回
     * {@code false}，触发 RESYNC_REQUIRED 让前端重新 bootstrap；仍在原 owner 下 RUNNING 时返回 {@code true}。
     */
    private boolean subscribeRecheck(String runId) {
        ChatRunEntity current = runService.loadCurrent(runId);
        if (current == null) {
            return false;
        }
        String status = current.getStatus();
        if (ChatRunStatus.isTerminal(status)
                || ChatRunStatus.AWAITING_CONFIRM.name().equals(status)) {
            return false;
        }
        if (!ChatRunStatus.RUNNING.name().equals(status)
                && !ChatRunStatus.STOPPING.name().equals(status)) {
            return false;
        }
        String owner = current.getOwnerInstanceId();
        if (owner == null) {
            return true;
        }
        ChatRunInstance local = registry.get(runId);
        return local != null && runOwner.instanceId().equals(owner);
    }

    /**
     * 生成运行的 AG-UI 引导事件。
     *
     * @param run 运行实体
     * @return 引导事件批次
     */
    public AguiBootstrap bootstrap(ChatRunEntity run) {
        ChatRunInstance execution = registry.get(run.getId());
        if (execution != null) {
            return execution.bootstrap();
        }
        ChatRunEntity current = loadCurrent(run);
        long highWatermark = eventStore.latestSeq(run.getId(), run.getSnapshotSeq());
        return new AguiBootstrap(
                highWatermark,
                AguiBootstrapEncoder.encode(
                        current, ChatRunSnapshotCodec.decode(current.getSnapshotJson()), highWatermark),
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
        ChatRunInstance execution = registry.selectOrRestoreForFinalize(run, session, scheduler);
        // 在实例锁内同时判断运行状态并迁移到 STOPPING，消除检查与启动新源流之间的竞态窗口。
        if (!execution.requestStop()) {
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

    /** 启动定时维护任务。供 {@link ChatRunRecoveryListener} 编排调用。 */
    void scheduleMaintenance() {
        maintenanceScheduler.schedule();
    }

    private void recoverInterruptedInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        if (shouldRetainAwaitingConfirmation(run, session)) {
            eventStore.initialize(run.getId(), run.getSnapshotSeq());
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
            lost.finalizeFailed(ChatRunFailureCode.CONFIRM_CONTEXT_UNAVAILABLE, "服务进程重启，用户确认上下文不可恢复");
        } else {
            lost.finalizeFailed(ChatRunFailureCode.INSTANCE_LOST, "服务进程重启，对话运行已终止");
        }
    }

    private boolean shouldRetainAwaitingConfirmation(ChatRunEntity run, ChatSessionEntity session) {
        if (!ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            return false;
        }
        StateStoreType type = StateStoreType.of(properties.getStateStore().getType());
        if (type == null || type == StateStoreType.MEMORY) {
            return false;
        }
        try {
            return instanceFactory.hasRecoverableConfirmation(run, session);
        } catch (RuntimeException recoveryFailure) {
            log.warn("服务重启后待确认上下文校验失败: runId={}", run.getId(), recoveryFailure);
            return false;
        }
    }

    /**
     * 优雅停机 drain（§8.4）：先标记排空使本节点停止认领/接管新 Run，再在停机上限内等待活动 Run 自然收敛
     * （期间续约与检查点照常）；到期仍未结束的 Run 协作式中断、由源流的 STOPPING 收敛或后续接管兜底。
     * 最后关闭定时维护线程池。
     */
    @PreDestroy
    public void shutdown() {
        runOwner.beginDrain();
        awaitDrain();
        if (!registry.isIdle()) {
            registry.forEachActive(execution -> execution.runInTenant(execution::interruptForShutdown));
        }
        scheduler.shutdown();
    }

    /** 在停机上限内轮询等待注册表排空；被中断或超时即返回，由调用方继续收尾。 */
    private void awaitDrain() {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(properties.getChat().getRun().getShutdownDrainTimeoutSeconds());
        while (!registry.isIdle() && System.nanoTime() < deadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 计算新租约截止时间（以当前时间为基准加租约时长）。 */
    private LocalDateTime newLeaseUntil() {
        return LocalDateTime.now().plusSeconds(properties.getChat().getRun().getLeaseTtlSeconds());
    }

    /** 租约过期阈值：当前时间减去接管宽限期；早于该时刻的租约视为可接管。 */
    private LocalDateTime expiredBefore() {
        return LocalDateTime.now().minusSeconds(properties.getChat().getRun().getTakeoverGraceSeconds());
    }

    /**
     * 周期接管租约已过期的中断态运行（仅 {@code RUNNING}/{@code STOPPING}）：先以闭环 CAS 抢占 owner/epoch，
     * 抢占成功才按遗失实例终结；抢占失败（他节点已接管或租约被续约）则跳过。供定时维护任务调用。
     *
     * @param run 候选中断态运行（来自周期扫描）
     */
    void takeoverIfExpired(ChatRunEntity run) {
        ChatSessionEntity session = loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            takeoverIfExpiredInTenantContext(run, session);
            return null;
        });
    }

    private void takeoverIfExpiredInTenantContext(ChatRunEntity run, ChatSessionEntity session) {
        boolean won = runService.takeover(
                run,
                run.getOwnerInstanceId(),
                run.getLeaseEpoch(),
                runOwner.instanceId(),
                newLeaseUntil(),
                expiredBefore());
        if (!won) {
            return;
        }
        // 抢占成功后才终结：本节点已成为新 owner，lost-instance 路径只用持久化快照落终态、不写事件流。
        ChatRunInstance lost = instanceFactory.restoreForFinalize(run, session, scheduler);
        if (ChatRunStatus.STOPPING.name().equals(run.getStatus())) {
            lost.finalizeStopped(ChatRunFinishReason.USER_STOP);
        } else {
            lost.finalizeFailed(ChatRunFailureCode.INSTANCE_LOST, "执行节点失效，对话运行已被接管终结");
        }
    }

    private void startCreated(ChatRunInstance execution) {
        ChatRunEntity run = execution.run();
        if (!runService.claimCreated(run, runOwner.instanceId(), newLeaseUntil())) {
            // 运行在调度或认领期间已被并发方终结，本实例未建立源流；完成排空信号让注册表摘除实例。
            execution.releaseNeverStarted();
            return;
        }
        run.setStatus(ChatRunStatus.RUNNING.name());
        try {
            ChatMessageEntity userMessage = messageService
                    .findByIdAndSession(run.getUserMessageId(), run.getSessionId())
                    .orElseThrow(() -> new IllegalStateException("Run用户消息不存在: " + run.getId()));
            List<ChatAttachmentEntity> attachments = attachmentService.listByMessageIds(List.of(userMessage.getId()));
            AppEntity app = appService.loadById(execution.session().getAppId());
            Msg msg = attachmentMessageBuilder.buildUserMsg(
                    execution.session(), app, userMessage.getContent(), attachments);
            execution.startPhase(msg);
        } catch (RuntimeException startFailure) {
            execution.finalizeFailed(ChatRunFailureCode.START_FAILED, ChatRunDataSanitizer.safeMessage(startFailure));
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

    private ChatSessionEntity loadSession(ChatRunEntity run) {
        return runService.loadSession(run);
    }
}
