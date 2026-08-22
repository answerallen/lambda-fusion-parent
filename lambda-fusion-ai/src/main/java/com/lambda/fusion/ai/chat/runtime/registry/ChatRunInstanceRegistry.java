package com.lambda.fusion.ai.chat.runtime.registry;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstance;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstanceFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * 本进程活动执行实例注册表：以运行标识维护唯一实例并施加本地容量约束。
 *
 * <p>注册成功后订阅实例排空信号；源流和后处理全部结束时按实例身份摘除。注册表只描述本进程内存状态，
 * 不承担节点发现、远程控制或故障接管。
 *
 * @author Jin
 */
public final class ChatRunInstanceRegistry {

    private final ChatRunInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final Map<String, ChatRunInstance> executions = new HashMap<>();

    public ChatRunInstanceRegistry(ChatRunInstanceFactory instanceFactory, AiProperties properties) {
        this.instanceFactory = instanceFactory;
        this.properties = properties;
    }

    /** 启动选择结果：{@code registered=true} 表示本次新建并注册，调用方应启动该实例。 */
    public record StartRegistration(ChatRunInstance execution, boolean registered) {}

    /**
     * 为 CREATED Run 原子检查容量、恢复并注册；容量不足返回空，已有实例返回 {@code registered=false}。
     */
    public synchronized Optional<StartRegistration> restoreForStartIfCapacity(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatRunInstance existing = executions.get(run.getId());
        if (existing != null) {
            return Optional.of(new StartRegistration(existing, false));
        }
        if (!hasCapacityForLocked(run, session)) {
            return Optional.empty();
        }
        ChatRunInstance candidate = instanceFactory.restoreExecution(run, session, scheduler);
        registerNew(run.getId(), candidate);
        return Optional.of(new StartRegistration(candidate, true));
    }

    /** 查询本地实例；不存在时在同一临界区内检查容量、恢复并注册。 */
    public synchronized ChatRunInstance selectOrRestore(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        enforceCapacityLocked(run, session);
        ChatRunInstance candidate = instanceFactory.restoreExecution(run, session, scheduler);
        registerNew(run.getId(), candidate);
        return candidate;
    }

    /** 查询本地实例；不存在时恢复待确认上下文清理实例。该清理不受新执行容量限制。 */
    public synchronized ChatRunInstance selectOrRestoreForConfirmationFinalization(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        ChatRunInstance candidate = instanceFactory.restoreConfirmationFinalizer(run, session, scheduler);
        registerNew(run.getId(), candidate);
        return candidate;
    }

    /** 查询本进程当前持有的活动实例。 */
    public synchronized ChatRunInstance get(String runId) {
        return executions.get(runId);
    }

    /** 对调用时刻的活动实例快照执行本地维护动作。 */
    public void forEachActive(Consumer<ChatRunInstance> action) {
        List<ChatRunInstance> active;
        synchronized (this) {
            active = List.copyOf(executions.values());
        }
        active.forEach(action);
    }

    private boolean hasCapacityForLocked(ChatRunEntity run, ChatSessionEntity session) {
        int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
        if (!executions.containsKey(run.getId()) && executions.size() >= maxGlobal) {
            return false;
        }
        long userRuns = executions.values().stream()
                .filter(execution -> Objects.equals(execution.session().getTenantId(), session.getTenantId()))
                .filter(execution -> Objects.equals(execution.session().getUserId(), session.getUserId()))
                .filter(execution -> !Objects.equals(execution.run().getId(), run.getId()))
                .count();
        return userRuns < properties.getChat().getRun().getMaxActiveRunsPerUser();
    }

    private void enforceCapacityLocked(ChatRunEntity run, ChatSessionEntity session) {
        if (hasCapacityForLocked(run, session)) {
            return;
        }
        int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
        int maxPerUser = properties.getChat().getRun().getMaxActiveRunsPerUser();
        throw new IllegalStateException(
                executions.size() >= maxGlobal ? "后台对话Run已达到实例上限 " + maxGlobal : "当前用户后台对话Run已达到上限 " + maxPerUser);
    }

    /** 调用方已持有注册表锁时注册新实例。 */
    private void registerNew(String runId, ChatRunInstance candidate) {
        executions.put(runId, candidate);
        candidate.drainedSignal().whenComplete((ignored, error) -> remove(runId, candidate));
    }

    private synchronized void remove(String runId, ChatRunInstance candidate) {
        executions.remove(runId, candidate);
    }
}
