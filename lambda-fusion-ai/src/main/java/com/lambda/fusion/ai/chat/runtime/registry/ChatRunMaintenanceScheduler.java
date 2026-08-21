package com.lambda.fusion.ai.chat.runtime.registry;

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
 * 对话运行定时维护调度：周期执行事件缓冲过期清理、活动实例到期检查点、待执行 CREATED Run 拉起
 * 与确认超时扫描。由协调器持有并按需启动，不独立注册为 Spring Bean。
 *
 * @author Jin
 */
@Slf4j
public final class ChatRunMaintenanceScheduler {

    private final ScheduledExecutorService scheduler;
    private final ChatRunEventStore eventStore;
    private final ChatRunInstanceRegistry registry;
    private final ChatRunStateService runService;
    private final Consumer<ChatRunEntity> createdLauncher;
    private final Consumer<ChatRunEntity> takeoverLauncher;
    private final java.util.function.Supplier<java.time.LocalDateTime> expiredBeforeSupplier;

    /**
     * 创建维护调度器。
     *
     * @param scheduler 定时任务执行器（与协调器共用）
     * @param eventStore 运行事件存储
     * @param registry 活动实例注册表
     * @param runService 运行状态服务
     * @param createdLauncher 待执行 CREATED Run 的拉起动作（协调器入口）
     * @param takeoverLauncher 过期中断态 Run 的接管动作（协调器入口）
     * @param expiredBeforeSupplier 租约过期阈值提供者
     */
    public ChatRunMaintenanceScheduler(
            ScheduledExecutorService scheduler,
            ChatRunEventStore eventStore,
            ChatRunInstanceRegistry registry,
            ChatRunStateService runService,
            Consumer<ChatRunEntity> createdLauncher,
            Consumer<ChatRunEntity> takeoverLauncher,
            java.util.function.Supplier<java.time.LocalDateTime> expiredBeforeSupplier) {
        this.scheduler = scheduler;
        this.eventStore = eventStore;
        this.registry = registry;
        this.runService = runService;
        this.createdLauncher = createdLauncher;
        this.takeoverLauncher = takeoverLauncher;
        this.expiredBeforeSupplier = expiredBeforeSupplier;
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
            // 周期接管失效租约的 RUNNING/STOPPING（不含 AWAITING_CONFIRM，后者走确认超时路径）。
            java.time.LocalDateTime expiredBefore = expiredBeforeSupplier.get();
            runService.listInterruptedOnRestart(expiredBefore).stream()
                    .filter(run -> !com.lambda.fusion.ai.AiConstants.ChatRunStatus.AWAITING_CONFIRM
                            .name()
                            .equals(run.getStatus()))
                    .forEach(run -> safelyMaintain(run.getId(), () -> takeoverLauncher.accept(run)));
            runService.listCreated().forEach(run -> safelyMaintain(run.getId(), () -> createdLauncher.accept(run)));
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

    private void safelyMaintain(String runId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            log.error("维护对话Run失败: runId={}", runId, error);
        }
    }
}
