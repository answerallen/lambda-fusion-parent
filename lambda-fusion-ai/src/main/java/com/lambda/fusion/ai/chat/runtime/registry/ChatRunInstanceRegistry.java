package com.lambda.fusion.ai.chat.runtime.registry;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstance;
import com.lambda.fusion.ai.chat.runtime.engine.ChatRunInstanceFactory;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * 活动执行实例注册表：以运行标识维护并发注册的唯一规范实例并施加容量约束。
 * 实例注册成功后订阅其排空信号，最终源流与后处理全部结束时按实例身份摘除；
 * 业务终态本身不触发摘除，仍在排空的实例会保留到 {@code drainedSignal} 完成。
 *
 * @author Jin
 */
public final class ChatRunInstanceRegistry {

    private final ChatRunInstanceFactory instanceFactory;
    private final AiProperties properties;
    private final ConcurrentMap<String, ChatRunInstance> executions = new ConcurrentHashMap<>();

    /**
     * 创建活动实例注册表。
     *
     * @param instanceFactory 执行实例工厂
     * @param properties AI 模块配置
     */
    public ChatRunInstanceRegistry(ChatRunInstanceFactory instanceFactory, AiProperties properties) {
        this.instanceFactory = instanceFactory;
        this.properties = properties;
    }

    /**
     * 查询活动实例；不存在时恢复并注册，并发竞争时使用最先注册的实例。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param scheduler 定时任务执行器
     * @return 规范执行实例
     * @throws IllegalStateException 已达到全局或用户级活动实例上限（确认属用户发起交互，仍以容量约束拒绝）
     */
    public ChatRunInstance selectOrRestore(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        // 确认恢复会创建执行实例，因此与 CREATED 启动使用相同的容量约束。
        enforceCapacity(run, session);
        ChatRunInstance candidate = instanceFactory.restoreExecution(run, session, scheduler);
        return registerOrExisting(run.getId(), candidate);
    }

    /**
     * 查询活动实例；不存在时恢复一个仅用于终结的实例。终结和停止恢复不受容量上限约束，
     * 避免容量已满时已有运行无法收敛。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @param scheduler 定时任务执行器
     * @return 规范执行实例
     */
    public ChatRunInstance selectOrRestoreForFinalize(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        ChatRunInstance candidate = instanceFactory.restoreForFinalize(run, session, scheduler);
        return registerOrExisting(run.getId(), candidate);
    }

    /** 注册候选实例；竞争落败时返回已注册实例。 */
    private ChatRunInstance registerOrExisting(String runId, ChatRunInstance candidate) {
        ChatRunInstance existing = register(runId, candidate);
        return existing == null ? candidate : existing;
    }

    /**
     * 注册候选实例并订阅其排空信号；返回已存在的实例，注册成功返回 {@code null}。
     * 语义对齐 {@code ConcurrentMap.putIfAbsent}。
     *
     * @param runId 运行标识
     * @param candidate 待注册实例
     * @return 已注册实例；注册成功时返回 {@code null}
     */
    public ChatRunInstance register(String runId, ChatRunInstance candidate) {
        ChatRunInstance existing = executions.putIfAbsent(runId, candidate);
        if (existing == null) {
            candidate.drainedSignal().whenComplete((ignored, error) -> executions.remove(runId, candidate));
        }
        return existing;
    }

    /**
     * 查询活动实例。
     *
     * @param runId 运行标识
     * @return 活动实例；不存在时返回 {@code null}
     */
    public ChatRunInstance get(String runId) {
        return executions.get(runId);
    }

    /**
     * 遍历活动实例（定时检查点与停机中断）。
     *
     * @param action 实例处理动作
     */
    public void forEachActive(Consumer<ChatRunInstance> action) {
        executions.values().forEach(action);
    }

    /**
     * 判断能否再接纳一个新运行而不超容量（只读探测，不抛异常）。集群下待认领的 {@code CREATED} 运行
     * 在满载节点上应跳过而非终结，留待空闲节点认领；故 CREATED 调度路径用本方法探测，确认路径仍用
     * {@link #enforceCapacity} 拒绝。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @return 未达全局或用户级上限返回 {@code true}
     */
    public boolean hasCapacityFor(ChatRunEntity run, ChatSessionEntity session) {
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

    /**
     * 本节点是否已无活动执行实例（优雅停机排空的收敛判据）。
     *
     * @return 注册表为空返回 {@code true}
     */
    public boolean isIdle() {
        return executions.isEmpty();
    }

    /**
     * 施加全局与用户级活动实例容量约束。
     *
     * @param run 运行实体
     * @param session 会话实体
     * @throws IllegalStateException 已达到全局或用户级活动实例上限
     */
    public void enforceCapacity(ChatRunEntity run, ChatSessionEntity session) {
        if (!hasCapacityFor(run, session)) {
            int maxGlobal = properties.getChat().getRun().getMaxActiveRuns();
            int maxPerUser = properties.getChat().getRun().getMaxActiveRunsPerUser();
            throw new IllegalStateException(
                    executions.size() >= maxGlobal
                            ? "后台对话Run已达到实例上限: " + maxGlobal
                            : "当前用户后台对话Run已达到上限: " + maxPerUser);
        }
    }
}
