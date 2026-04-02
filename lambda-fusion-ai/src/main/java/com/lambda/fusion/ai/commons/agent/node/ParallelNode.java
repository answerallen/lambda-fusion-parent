package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 并行执行节点。
 * 同时执行多个分支，等待所有分支完成后汇聚结果。
 * <p>
 * 配置属性：
 * - branches: 并行分支配置数组，每个分支包含 id（分支标识）和 target（目标节点ID）
 * - joinNode: 汇聚节点ID（所有分支完成后跳转）
 * - timeout: 超时时间（毫秒，默认 30000）
 * - waitAll: 是否等待所有分支完成（默认 true，false 表示任意一个完成就继续）
 * - errorStrategy: 错误处理策略 (failFast | ignore | cancelOthers)
 */
@Slf4j
@Component
public class ParallelNode implements AgentNode {

    private static final String PARALLEL_CONTEXT_KEY = "__parallel_context__";
    private static final long DEFAULT_TIMEOUT = 30000;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public String getName() {
        return "PARALLEL";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("并行节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        // 获取并行上下文
        ParallelContext context = getOrCreateParallelContext(state);

        // 获取分支配置
        Object branchesObj = properties.get("branches");
        if (!(branchesObj instanceof List<?> branchList)) {
            log.warn("并行节点缺少分支配置");
            return new ExecutionResult(state, null);
        }

        String joinNode = (String) properties.get("joinNode");
        long timeout = ((Number) properties.getOrDefault("timeout", DEFAULT_TIMEOUT)).longValue();
        boolean waitAll = (boolean) properties.getOrDefault("waitAll", true);
        String errorStrategy = (String) properties.getOrDefault("errorStrategy", "failFast");

        // 如果已经启动了并行执行，检查是否完成
        if (context.isStarted) {
            return checkParallelCompletion(state, context, joinNode, waitAll, errorStrategy);
        }

        // 首次进入，启动并行执行
        return startParallelExecution(state, context, branchList, timeout);
    }

    /**
     * 启动并行执行
     */
    private ExecutionResult startParallelExecution(
            AgentState state, ParallelContext context, List<?> branchList, long timeout) {
        context.isStarted = true;
        context.startTime = System.currentTimeMillis();
        context.pendingBranches = new HashSet<>();
        context.completedBranches = new HashMap<>();
        context.failedBranches = new HashMap<>();

        // 记录所有待执行的分支
        for (Object branchObj : branchList) {
            if (branchObj instanceof Map<?, ?> branch) {
                String branchId = (String) branch.get("id");
                String target = (String) branch.get("target");
                if (branchId != null && target != null) {
                    context.pendingBranches.add(branchId);
                    context.branchTargets.put(branchId, target);
                }
            }
        }

        if (context.pendingBranches.isEmpty()) {
            log.warn("并行节点没有有效的分支配置");
            clearParallelContext(state);
            return new ExecutionResult(state, null);
        }

        // 保存上下文并返回，让调度器处理分支执行
        saveParallelContext(state, context);
        log.debug("并行节点启动 {} 个分支执行", context.pendingBranches.size());

        // 返回第一个分支作为起点（实际并行执行由上层调度器处理）
        String firstBranch = context.pendingBranches.iterator().next();
        return new ExecutionResult(state, context.branchTargets.get(firstBranch));
    }

    /**
     * 检查并行执行完成情况
     */
    private ExecutionResult checkParallelCompletion(
            AgentState state, ParallelContext context, String joinNode, boolean waitAll, String errorStrategy) {
        // 检查超时
        long elapsed = System.currentTimeMillis() - context.startTime;
        if (elapsed > context.timeout) {
            log.warn("并行节点执行超时");
            clearParallelContext(state);
            return new ExecutionResult(state, joinNode);
        }

        // 检查是否有失败的分支
        if (!context.failedBranches.isEmpty()) {
            switch (errorStrategy.toLowerCase()) {
                case "failfast" -> {
                    log.warn("并行节点分支执行失败，快速失败: {}", context.failedBranches.keySet());
                    clearParallelContext(state);
                    return new ExecutionResult(state, joinNode);
                }
                case "cancelothers" -> {
                    // 取消其他分支（标记为已完成）
                    context.pendingBranches.clear();
                    log.warn("并行节点分支执行失败，取消其他分支: {}", context.failedBranches.keySet());
                }
                case "ignore" -> log.debug("并行节点分支执行失败，忽略错误: {}", context.failedBranches.keySet());
            }
        }

        // 检查是否完成
        if (waitAll) {
            // 等待所有分支完成
            if (context.pendingBranches.isEmpty()) {
                // 所有分支完成
                log.debug("并行节点所有分支执行完成");
                clearParallelContext(state);
                return new ExecutionResult(state, joinNode);
            }
        } else {
            // 任意一个分支完成就继续
            if (!context.completedBranches.isEmpty()) {
                log.debug("并行节点至少一个分支执行完成");
                clearParallelContext(state);
                return new ExecutionResult(state, joinNode);
            }
        }

        // 还有分支在执行中，继续等待
        saveParallelContext(state, context);
        return new ExecutionResult(state, null);
    }

    /**
     * 标记分支完成（供外部调用）
     */
    @SuppressWarnings("unchecked")
    public void markBranchCompleted(AgentState state, String branchId, Object result) {
        ParallelContext context = getOrCreateParallelContext(state);
        context.pendingBranches.remove(branchId);
        context.completedBranches.put(branchId, result);
        saveParallelContext(state, context);
    }

    /**
     * 标记分支失败（供外部调用）
     */
    @SuppressWarnings("unchecked")
    public void markBranchFailed(AgentState state, String branchId, Throwable error) {
        ParallelContext context = getOrCreateParallelContext(state);
        context.pendingBranches.remove(branchId);
        context.failedBranches.put(branchId, error);
        saveParallelContext(state, context);
    }

    /**
     * 获取或创建并行上下文
     */
    @SuppressWarnings("unchecked")
    private ParallelContext getOrCreateParallelContext(AgentState state) {
        if (state.getAttributes() != null) {
            Object contextObj = state.getAttributes().get(PARALLEL_CONTEXT_KEY);
            if (contextObj instanceof Map<?, ?> contextMap) {
                return ParallelContext.fromMap((Map<String, Object>) contextMap);
            }
        }
        return new ParallelContext();
    }

    /**
     * 保存并行上下文
     */
    private void saveParallelContext(AgentState state, ParallelContext context) {
        if (state.getAttributes() != null) {
            state.getAttributes().put(PARALLEL_CONTEXT_KEY, context.toMap());
        }
    }

    /**
     * 清除并行上下文
     */
    private void clearParallelContext(AgentState state) {
        if (state.getAttributes() != null) {
            state.getAttributes().remove(PARALLEL_CONTEXT_KEY);
        }
    }

    /**
     * 并行上下文
     */
    private static class ParallelContext {
        boolean isStarted = false;
        long startTime = 0;
        long timeout = DEFAULT_TIMEOUT;
        Set<String> pendingBranches = new HashSet<>();
        Map<String, Object> completedBranches = new HashMap<>();
        Map<String, Throwable> failedBranches = new HashMap<>();
        Map<String, String> branchTargets = new HashMap<>();

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("isStarted", isStarted);
            map.put("startTime", startTime);
            map.put("timeout", timeout);
            map.put("pendingBranches", new HashSet<>(pendingBranches));
            map.put("completedBranches", new HashMap<>(completedBranches));
            map.put("failedBranches", new HashMap<>(failedBranches));
            map.put("branchTargets", new HashMap<>(branchTargets));
            return map;
        }

        @SuppressWarnings("unchecked")
        static ParallelContext fromMap(Map<String, Object> map) {
            ParallelContext context = new ParallelContext();
            context.isStarted = (boolean) map.getOrDefault("isStarted", false);
            context.startTime = ((Number) map.getOrDefault("startTime", 0L)).longValue();
            context.timeout = ((Number) map.getOrDefault("timeout", DEFAULT_TIMEOUT)).longValue();
            context.pendingBranches = new HashSet<>((Set<String>) map.getOrDefault("pendingBranches", new HashSet<>()));
            context.completedBranches =
                    new HashMap<>((Map<String, Object>) map.getOrDefault("completedBranches", new HashMap<>()));
            context.failedBranches =
                    new HashMap<>((Map<String, Throwable>) map.getOrDefault("failedBranches", new HashMap<>()));
            context.branchTargets =
                    new HashMap<>((Map<String, String>) map.getOrDefault("branchTargets", new HashMap<>()));
            return context;
        }
    }
}
