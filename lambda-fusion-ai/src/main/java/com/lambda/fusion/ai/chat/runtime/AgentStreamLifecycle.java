package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiProperties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * 单阶段 Agent 源流生命周期：持有当前订阅与交互超时句柄，负责交互超时调度、取消与强制释放。
 * 不持实例锁；启停决策与状态机判定由 {@code ChatRunInstance} 在实例锁内完成。
 *
 * @author Jin
 */
@Slf4j
public final class AgentStreamLifecycle {

    private final String runId;
    private final ScheduledExecutorService scheduler;
    private final AiProperties properties;
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> interactionTimeout = new AtomicReference<>();

    /**
     * 创建源流生命周期组件。
     *
     * @param runId 运行标识
     * @param scheduler 定时任务执行器
     * @param properties AI 模块配置
     */
    public AgentStreamLifecycle(String runId, ScheduledExecutorService scheduler, AiProperties properties) {
        this.runId = runId;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    /** 清除当前订阅句柄；阶段开始时重置、源流正常完成后清除均调用此方法。 */
    public void clear() {
        disposable.set(null);
    }

    /** 判断当前源流订阅是否仍在运行。 */
    public boolean isRunning() {
        Disposable current = disposable.get();
        return current != null && !current.isDisposed();
    }

    /** 占用订阅句柄；订阅同步回调期间已发生终态时由调用方直接 dispose 而不占用。 */
    public void adopt(Disposable next) {
        disposable.compareAndSet(null, next);
    }

    /**
     * 注册交互超时任务；根 {@code AGENT_END} 到达后由 {@link #cancelInteractionTimeout()} 取消，
     * 未到达则在截止时执行超时动作。
     *
     * @param timeoutAction 超时动作（通常为租户上下文包装的实例超时处理）
     */
    public void scheduleInteractionTimeout(Runnable timeoutAction) {
        cancelInteractionTimeout();
        if (scheduler.isShutdown()) {
            return;
        }
        long seconds = properties.getChat().getRun().getMaxRunDurationSeconds();
        interactionTimeout.set(scheduler.schedule(timeoutAction, seconds, TimeUnit.SECONDS));
    }

    /** 取消交互超时任务。 */
    public void cancelInteractionTimeout() {
        ScheduledFuture<?> timeout = interactionTimeout.getAndSet(null);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    /** 强制释放当前源流订阅，只释放资源，不覆盖已提交的终态。 */
    public void forceDispose() {
        Disposable current = disposable.getAndSet(null);
        if (current != null && !current.isDisposed()) {
            current.dispose();
        }
    }

    /**
     * 交互超时中断宽限期结束后执行释放动作；调度器已关闭或调度失败时立即执行。
     *
     * @param forceDisposeAction 释放动作（通常为租户上下文包装的 {@link #forceDispose()}）
     */
    public void scheduleForceDispose(Runnable forceDisposeAction) {
        if (scheduler.isShutdown()) {
            forceDisposeAction.run();
            return;
        }
        try {
            scheduler.schedule(
                    forceDisposeAction, properties.getChat().getRun().getStopGraceSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException scheduleFailure) {
            log.warn("Run交互超时后无法调度强制取消，立即释放源流: runId={}", runId, scheduleFailure);
            forceDisposeAction.run();
        }
    }
}
