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
 * 本地运行维护：清理过期事件、为活动实例写检查点、拉起尚未开始的业务 Run，并处理确认超时。
 * 不执行节点探活、远程停止或故障接管。
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

    public ChatRunMaintenanceScheduler(
            ScheduledExecutorService scheduler,
            ChatRunEventStore eventStore,
            ChatRunInstanceRegistry registry,
            ChatRunStateService runService,
            Consumer<ChatRunEntity> createdLauncher) {
        this.scheduler = scheduler;
        this.eventStore = eventStore;
        this.registry = registry;
        this.runService = runService;
        this.createdLauncher = createdLauncher;
    }

    /** 首轮延迟 5 秒，之后每 30 秒执行一次。 */
    public void schedule() {
        scheduler.scheduleAtFixedRate(this::maintenance, 5, 30, TimeUnit.SECONDS);
    }

    private void maintenance() {
        try {
            eventStore.purgeExpired();
            long now = System.nanoTime();
            registry.forEachActive(execution -> safelyMaintain(
                    execution.run().getId(), () -> execution.runInTenant(() -> execution.checkpointIfDue(now))));
            runService.listCreated().forEach(run -> safelyMaintain(run.getId(), () -> createdLauncher.accept(run)));
            runService
                    .listExpiredConfirmations(LocalDateTime.now())
                    .forEach(run -> safelyMaintain(run.getId(), () -> expireConfirmation(run)));
        } catch (RuntimeException error) {
            log.error("对话Run维护任务失败", error);
        }
    }

    private void expireConfirmation(ChatRunEntity run) {
        ChatSessionEntity session = runService.loadSession(run);
        TenantUtils.withTenant(session.getTenantId(), () -> {
            ChatRunInstance execution = registry.selectOrRestoreForConfirmationFinalization(run, session, scheduler);
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
