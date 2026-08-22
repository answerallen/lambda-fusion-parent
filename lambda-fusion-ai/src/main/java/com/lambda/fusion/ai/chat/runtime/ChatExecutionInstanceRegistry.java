package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
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
public final class ChatExecutionInstanceRegistry {

    private final ChatExecutionInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final Map<String, ChatExecutionInstance> executions = new HashMap<>();

    public ChatExecutionInstanceRegistry(ChatExecutionInstanceFactory instanceFactory, AiProperties properties) {
        this.instanceFactory = instanceFactory;
        this.properties = properties;
    }

    /**
     * 为新 Run 原子检查容量、构造并注册；容量不足返回空。幂等旧请求由调用方过滤，
     * 同标识活动实例已存在即违反调用契约，直接抛出以暴露重复启动缺陷。
     */
    public synchronized Optional<ChatExecutionInstance> registerForStartIfCapacity(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        if (executions.containsKey(run.getId())) {
            throw new IllegalStateException("Run已存在活动实例: " + run.getId());
        }
        if (!hasCapacityForLocked(session)) {
            return Optional.empty();
        }
        ChatExecutionInstance candidate = instanceFactory.createAgentBacked(run, session, scheduler);
        registerNew(run.getId(), candidate);
        return Optional.of(candidate);
    }

    /**
     * 查询当前节点实例；进程重启后可为已暂停的 HITL 阶段重建并注册一个实例。
     * 调用方必须先确认持久化快照确有待确认工具。
     */
    public synchronized ChatExecutionInstance registerPausedConfirmation(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatExecutionInstance existing = executions.get(run.getId());
        if (existing != null) {
            return existing;
        }
        enforceCapacityLocked(session);
        ChatExecutionInstance candidate = instanceFactory.createAgentBacked(run, session, scheduler);
        registerNew(run.getId(), candidate);
        return candidate;
    }

    /** 查询本进程当前持有的活动实例。 */
    public synchronized ChatExecutionInstance get(String runId) {
        return executions.get(runId);
    }

    /** 对调用时刻的活动实例快照执行本地动作（当前仅用于进程关闭）。 */
    public void forEachActive(Consumer<ChatExecutionInstance> action) {
        List<ChatExecutionInstance> active;
        synchronized (this) {
            active = List.copyOf(executions.values());
        }
        active.forEach(action);
    }

    private boolean hasCapacityForLocked(ChatSessionEntity session) {
        int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
        if (executions.size() >= maxGlobal) {
            return false;
        }
        long userRuns = executions.values().stream()
                .filter(execution -> Objects.equals(execution.session().getTenantId(), session.getTenantId()))
                .filter(execution -> Objects.equals(execution.session().getUserId(), session.getUserId()))
                .count();
        return userRuns < properties.getChat().getRun().getMaxActiveRunsPerUser();
    }

    private void enforceCapacityLocked(ChatSessionEntity session) {
        if (hasCapacityForLocked(session)) {
            return;
        }
        int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
        int maxPerUser = properties.getChat().getRun().getMaxActiveRunsPerUser();
        throw new IllegalStateException(
                executions.size() >= maxGlobal ? "后台对话Run已达到实例上限 " + maxGlobal : "当前用户后台对话Run已达到上限 " + maxPerUser);
    }

    /** 调用方已持有注册表锁时注册新实例。 */
    private void registerNew(String runId, ChatExecutionInstance candidate) {
        executions.put(runId, candidate);
        candidate.drainedSignal().whenComplete((ignored, error) -> remove(runId, candidate));
    }

    private synchronized void remove(String runId, ChatExecutionInstance candidate) {
        executions.remove(runId, candidate);
    }
}
