package com.lambda.fusion.ai.chat.runtime.registry;

import com.lambda.fusion.ai.AiConstants.ChatRunFailureCode;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstance;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.core.utils.TenantUtils;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 对话运行定时维护调度：周期执行事件缓冲过期清理、活动实例到期检查点（顺带心跳）、待执行 CREATED Run 拉起、
 * 执行节点心跳超时 Run 的失效收敛与确认超时扫描。由协调器持有并按需启动，不独立注册为 Spring Bean。
 *
 * <p>降级模型下不含接管/租约逻辑：心跳仅用于检测执行节点失效并收敛为可重试终态，不接管运行中的调用。
 *
 * @author Jin
 */
@Slf4j
public final class ChatRunMaintenanceScheduler {

    private final ScheduledExecutorService scheduler;
    private final ChatRunEventStore eventStore;
    private final ChatRunInstanceRegistry registry;
    private final ChatRunStateService runService;
    private final AiProperties properties;
    private final Consumer<ChatRunEntity> createdLauncher;
    private final Consumer<ChatRunEntity> instanceLostConverger;
    private final java.util.function.Supplier<java.time.LocalDateTime> heartbeatTimedOutBeforeSupplier;

    /**
     * 创建维护调度器。
     *
     * @param scheduler 定时任务执行器（与协调器共用）
     * @param eventStore 运行事件存储
     * @param registry 活动实例注册表
     * @param runService 运行状态服务
     * @param properties AI 模块配置
     * @param createdLauncher 待执行 CREATED Run 的拉起动作（协调器入口）
     * @param instanceLostConverger 心跳超时 Run 的失效收敛动作（协调器入口）
     * @param heartbeatTimedOutBeforeSupplier 心跳超时阈值提供者
     */
    public ChatRunMaintenanceScheduler(
            ScheduledExecutorService scheduler,
            ChatRunEventStore eventStore,
            ChatRunInstanceRegistry registry,
            ChatRunStateService runService,
            AiProperties properties,
            Consumer<ChatRunEntity> createdLauncher,
            Consumer<ChatRunEntity> instanceLostConverger,
            java.util.function.Supplier<java.time.LocalDateTime> heartbeatTimedOutBeforeSupplier) {
        this.scheduler = scheduler;
        this.eventStore = eventStore;
        this.registry = registry;
        this.runService = runService;
        this.properties = properties;
        this.createdLauncher = createdLauncher;
        this.instanceLostConverger = instanceLostConverger;
        this.heartbeatTimedOutBeforeSupplier = heartbeatTimedOutBeforeSupplier;
    }

    /** 启动周期维护任务：首轮延迟 5 秒，之后每 30 秒执行一次。 */
    public void schedule() {
        scheduler.scheduleAtFixedRate(this::maintenance, 5, 30, TimeUnit.SECONDS);
    }

    private void maintenance() {
        try {
            eventStore.purgeExpired();
            long now = System.nanoTime();
            registry.forEachActive(execution -> safelyMaintain(
                    execution.run().getId(), () -> execution.runInTenant(() -> execution.checkpointIfDue(now))));
            // 心跳超时扫描：收敛执行节点可能已失效的 RUNNING/STOPPING（不含 AWAITING_CONFIRM，后者走确认超时路径）。
            java.time.LocalDateTime timedOutBefore = heartbeatTimedOutBeforeSupplier.get();
            runService
                    .listExpiredHeartbeatRuns(timedOutBefore)
                    .forEach(run -> safelyMaintain(run.getId(), () -> instanceLostConverger.accept(run)));
            runService.listCreated().forEach(run -> safelyMaintain(run.getId(), () -> createdLauncher.accept(run)));
            // 滞留 CREATED 超过调度超时的运行收敛为失败（任意节点均可终结，DB 终态 CAS 幂等）。
            LocalDateTime staleBefore = LocalDateTime.now()
                    .minusSeconds(properties.getChat().getRun().getScheduleTimeoutSeconds());
            runService
                    .listStaleCreated(staleBefore)
                    .forEach(run -> safelyMaintain(run.getId(), () -> convergeScheduleTimeout(run)));
            runService
                    .listExpiredConfirmations(java.time.LocalDateTime.now())
                    .forEach(run -> safelyMaintain(run.getId(), () -> expireConfirmation(run)));
        } catch (RuntimeException error) {
            log.error("对话Run维护任务失败", error);
        }
    }

    private void expireConfirmation(ChatRunEntity run) {
        ChatSessionEntity session = runService.loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            // 取规范实例（注册唯一实例），确认超时的数据库迁移与终态收敛一并移入实例锁。
            ChatRunInstance execution = registry.selectOrRestoreForFinalize(run, session, scheduler);
            execution.expireConfirmation(LocalDateTime.now());
        });
    }

    /** 终结「调度超时」的滞留 CREATED 运行：按确认路径同款规范实例落终态（DB 幂等，多节点并发仅一方生效）。 */
    private void convergeScheduleTimeout(ChatRunEntity run) {
        ChatSessionEntity session = runService.loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatRunInstance execution = registry.selectOrRestoreForFinalize(run, session, scheduler);
            execution.finalizeFailed(
                    ChatRunFailureCode.SCHEDULE_TIMEOUT,
                    "对话Run等待调度超过 " + properties.getChat().getRun().getScheduleTimeoutSeconds() + " 秒未被认领");
        });
    }

    private void safelyMaintain(String runId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            log.error("维护对话Run失败: runId={}", runId, error);
        }
    }
}
