package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParallelNode implements AgentNode {

    public static final String NAME = "PARALLEL";

    private static final String PARALLEL_CONTEXT_KEY = "__parallel_context__";
    private static final String PARALLEL_RESULTS_KEY = "__parallel_results__";
    private static final String PARALLEL_ERRORS_KEY = "__parallel_errors__";
    private static final long DEFAULT_TIMEOUT = 30000;

    private final Executor executor;

    public ParallelNode(@Qualifier("agentParallelExecutor") Executor executor) {
        this.executor = executor;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("并行节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        ParallelContext context = getOrCreateParallelContext(state);

        Object branchesObj = properties.get("branches");
        if (!(branchesObj instanceof List<?> branchList)) {
            log.warn("并行节点缺少分支配置");
            return new ExecutionResult(state, null);
        }

        String joinNode = (String) properties.get("joinNode");
        Object timeoutObj = properties.getOrDefault("timeout", DEFAULT_TIMEOUT);
        long timeout = timeoutObj instanceof Number n ? n.longValue() : DEFAULT_TIMEOUT;
        boolean waitAll = !Boolean.FALSE.equals(properties.getOrDefault("waitAll", true));
        String errorStrategy = (String) properties.getOrDefault("errorStrategy", "failFast");

        if (context.isCompleted) {
            clearParallelContext(state);
            return new ExecutionResult(state, joinNode);
        }

        if (context.isStarted) {
            return checkParallelCompletion(state, context, joinNode, waitAll, errorStrategy);
        }

        return startParallelExecution(state, context, branchList, timeout, waitAll, errorStrategy, joinNode);
    }

    private ExecutionResult startParallelExecution(
            AgentState state,
            ParallelContext context,
            List<?> branchList,
            long timeout,
            boolean waitAll,
            String errorStrategy,
            String joinNode) {

        BiFunction<String, AgentState, AgentState> nodeExecutor = state.getNodeExecutor();
        if (nodeExecutor == null) {
            log.warn("并行节点无法获取节点执行器，可能不在 AgentGraph 上下文中执行");
            return new ExecutionResult(state, joinNode);
        }

        context.isStarted = true;
        context.startTime = System.currentTimeMillis();
        context.timeout = timeout;
        context.errorStrategy = errorStrategy;
        context.waitAll = waitAll;
        context.joinNode = joinNode;

        List<BranchConfig> branches = new ArrayList<>();
        for (Object branchObj : branchList) {
            if (branchObj instanceof Map<?, ?> branch) {
                String branchId = (String) branch.get("id");
                String target = (String) branch.get("target");
                if (branchId != null && target != null) {
                    branches.add(new BranchConfig(branchId, target));
                    context.pendingBranches.add(branchId);
                }
            }
        }

        if (branches.isEmpty()) {
            log.warn("并行节点没有有效的分支配置");
            clearParallelContext(state);
            return new ExecutionResult(state, null);
        }

        saveParallelContext(state, context);

        Map<String, Object> results = new ConcurrentHashMap<>();
        Map<String, Throwable> errors = new ConcurrentHashMap<>();
        AtomicBoolean hasFailure = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(branches.size());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (BranchConfig branch : branches) {
            if (cancelled.get() && "failFast".equals(errorStrategy)) {
                latch.countDown();
                continue;
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> {
                        if (cancelled.get()) {
                            latch.countDown();
                            return;
                        }

                        long branchStartedAt = System.currentTimeMillis();
                        try {
                            AgentState branchState = deepCopyState(state);
                            branchState.getAttributes().put("__branch_id__", branch.id());

                            log.debug("并行分支 {} 开始执行，目标节点: {}", branch.id(), branch.target());

                            AgentState branchResult = nodeExecutor.apply(branch.target(), branchState);

                            long branchDuration = System.currentTimeMillis() - branchStartedAt;

                            Map<String, Object> resultInfo = new HashMap<>();
                            resultInfo.put("branchId", branch.id());
                            resultInfo.put("targetNode", branch.target());
                            resultInfo.put("startedAt", branchStartedAt);
                            resultInfo.put("durationMs", branchDuration);
                            resultInfo.put("success", true);

                            if (branchResult != null && branchResult.getAttributes() != null) {
                                Map<String, Object> branchOutput = new HashMap<>();
                                branchResult.getAttributes().forEach((k, v) -> {
                                    if (!k.startsWith("_")) {
                                        branchOutput.put(k, v);
                                    }
                                });
                                resultInfo.put("output", branchOutput);
                            }

                            results.put(branch.id(), resultInfo);
                            context.completedBranches.put(branch.id(), resultInfo);
                            context.pendingBranches.remove(branch.id());

                            log.debug("并行分支 {} 执行完成，耗时: {}ms", branch.id(), branchDuration);

                        } catch (Exception e) {
                            long branchDuration = System.currentTimeMillis() - branchStartedAt;
                            log.error("并行分支 {} 执行失败，耗时: {}ms", branch.id(), branchDuration, e);

                            errors.put(branch.id(), e);
                            context.failedBranches.put(branch.id(), e);
                            context.pendingBranches.remove(branch.id());
                            hasFailure.set(true);

                            firstError.compareAndSet(null, e);

                            Map<String, Object> errorInfo = new HashMap<>();
                            errorInfo.put("branchId", branch.id());
                            errorInfo.put("targetNode", branch.target());
                            errorInfo.put("startedAt", branchStartedAt);
                            errorInfo.put("durationMs", branchDuration);
                            errorInfo.put("success", false);
                            errorInfo.put("error", e.getMessage());
                            results.put(branch.id(), errorInfo);

                            if ("failFast".equals(errorStrategy)) {
                                cancelled.set(true);
                            }
                        } finally {
                            latch.countDown();
                        }
                    },
                    executor);

            futures.add(future);
        }

        try {
            boolean completed;
            if (waitAll) {
                completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            } else {
                completed = latch.await(1, TimeUnit.MILLISECONDS);
                if (!completed) {
                    long remainingTimeout = timeout;
                    long checkInterval = Math.min(100, timeout / 10);
                    while (remainingTimeout > 0 && !completed) {
                        if (results.size() > 0 || errors.size() > 0) {
                            break;
                        }
                        completed = latch.await(Math.min(checkInterval, remainingTimeout), TimeUnit.MILLISECONDS);
                        remainingTimeout -= checkInterval;
                    }
                }
            }

            if (!completed && waitAll) {
                log.warn("并行节点执行超时，已完成: {}, 总数: {}", branches.size() - latch.getCount(), branches.size());
                cancelled.set(true);
            }

            context.isCompleted = true;
            context.completedAt = System.currentTimeMillis();

            if (state.getAttributes() == null) {
                state.setAttributes(new ConcurrentHashMap<>());
            }
            state.getAttributes().put(PARALLEL_RESULTS_KEY, new HashMap<>(results));

            if (!errors.isEmpty()) {
                Map<String, String> errorMessages = new HashMap<>();
                errors.forEach((id, e) -> errorMessages.put(id, e.getMessage()));
                state.getAttributes().put(PARALLEL_ERRORS_KEY, errorMessages);
            }

            saveParallelContext(state, context);

            log.info(
                    "并行节点执行完成，成功: {}, 失败: {}, 耗时: {}ms",
                    results.size() - errors.size(),
                    errors.size(),
                    context.completedAt - context.startTime);

            clearParallelContext(state);

            if (hasFailure.get() && "failFast".equals(errorStrategy) && firstError.get() != null) {
                log.warn("并行节点因分支失败而终止: {}", firstError.get().getMessage());
            }

            return new ExecutionResult(state, joinNode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("并行节点执行被中断", e);
            cancelled.set(true);
            clearParallelContext(state);
            return new ExecutionResult(state, joinNode);
        }
    }

    private ExecutionResult checkParallelCompletion(
            AgentState state, ParallelContext context, String joinNode, boolean waitAll, String errorStrategy) {

        long elapsed = System.currentTimeMillis() - context.startTime;
        if (elapsed > context.timeout) {
            log.warn("并行节点执行超时");
            context.isCompleted = true;
            saveParallelContext(state, context);
            clearParallelContext(state);
            return new ExecutionResult(state, joinNode);
        }

        return new ExecutionResult(state, null);
    }

    private AgentState deepCopyState(AgentState original) {
        AgentState copy = new AgentState();
        copy.setSessionId(original.getSessionId());
        copy.setKbId(original.getKbId());
        copy.setLlmModelId(original.getLlmModelId());
        copy.setFinished(original.isFinished());
        copy.setMessages(
                new CopyOnWriteArrayList<>(original.getMessages() != null ? original.getMessages() : List.of()));
        copy.setPendingToolRequests(new CopyOnWriteArrayList<>(
                original.getPendingToolRequests() != null ? original.getPendingToolRequests() : List.of()));
        copy.setNodeExecutor(original.getNodeExecutor());
        copy.setAvailableNodes(original.getAvailableNodes());

        if (original.getAttributes() != null) {
            Map<String, Object> copiedAttributes = new ConcurrentHashMap<>();
            original.getAttributes().forEach((key, value) -> {
                if (value instanceof Map<?, ?> mapValue) {
                    copiedAttributes.put(key, deepCopyMap(mapValue));
                } else if (value instanceof List<?> listValue) {
                    copiedAttributes.put(key, new CopyOnWriteArrayList<>(listValue));
                } else {
                    copiedAttributes.put(key, value);
                }
            });
            copy.setAttributes(copiedAttributes);
        } else {
            copy.setAttributes(new ConcurrentHashMap<>());
        }

        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<?, ?> source) {
        Map<String, Object> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(String.valueOf(key), deepCopyMap(mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.put(String.valueOf(key), new CopyOnWriteArrayList<>(listValue));
            } else {
                copy.put(String.valueOf(key), value);
            }
        });
        return copy;
    }

    public void markBranchCompleted(AgentState state, String branchId, Object result) {
        ParallelContext context = getOrCreateParallelContext(state);
        context.pendingBranches.remove(branchId);
        context.completedBranches.put(branchId, result);
        saveParallelContext(state, context);
    }

    public void markBranchFailed(AgentState state, String branchId, Throwable error) {
        ParallelContext context = getOrCreateParallelContext(state);
        context.pendingBranches.remove(branchId);
        context.failedBranches.put(branchId, error);
        saveParallelContext(state, context);
    }

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

    private void saveParallelContext(AgentState state, ParallelContext context) {
        if (state.getAttributes() != null) {
            state.getAttributes().put(PARALLEL_CONTEXT_KEY, context.toMap());
        }
    }

    private void clearParallelContext(AgentState state) {
        if (state.getAttributes() != null) {
            state.getAttributes().remove(PARALLEL_CONTEXT_KEY);
        }
    }

    private record BranchConfig(String id, String target) {}

    private static class ParallelContext {
        boolean isStarted = false;
        boolean isCompleted = false;
        long startTime = 0;
        long completedAt = 0;
        long timeout = DEFAULT_TIMEOUT;
        String errorStrategy = "failFast";
        boolean waitAll = true;
        String joinNode = null;
        Set<String> pendingBranches = ConcurrentHashMap.newKeySet();
        Map<String, Object> completedBranches = new ConcurrentHashMap<>();
        Map<String, Throwable> failedBranches = new ConcurrentHashMap<>();

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("isStarted", isStarted);
            map.put("isCompleted", isCompleted);
            map.put("startTime", startTime);
            map.put("completedAt", completedAt);
            map.put("timeout", timeout);
            map.put("errorStrategy", errorStrategy);
            map.put("waitAll", waitAll);
            map.put("joinNode", joinNode);
            map.put("pendingBranches", new HashSet<>(pendingBranches));
            map.put("completedBranches", new HashMap<>(completedBranches));
            Map<String, String> failedInfo = new HashMap<>();
            failedBranches.forEach((k, v) -> failedInfo.put(k, v.getMessage()));
            map.put("failedBranches", failedInfo);
            return map;
        }

        @SuppressWarnings("unchecked")
        static ParallelContext fromMap(Map<String, Object> map) {
            ParallelContext context = new ParallelContext();
            context.isStarted = Boolean.TRUE.equals(map.get("isStarted"));
            context.isCompleted = Boolean.TRUE.equals(map.get("isCompleted"));
            Object startTimeVal = map.get("startTime");
            context.startTime = startTimeVal instanceof Number n ? n.longValue() : 0L;
            Object completedAtVal = map.get("completedAt");
            context.completedAt = completedAtVal instanceof Number n ? n.longValue() : 0L;
            Object timeoutVal = map.get("timeout");
            context.timeout = timeoutVal instanceof Number n ? n.longValue() : DEFAULT_TIMEOUT;
            context.errorStrategy = (String) map.getOrDefault("errorStrategy", "failFast");
            Object waitAllVal = map.get("waitAll");
            context.waitAll = waitAllVal == null || Boolean.TRUE.equals(waitAllVal);
            context.joinNode = (String) map.get("joinNode");
            context.pendingBranches = ConcurrentHashMap.newKeySet();
            context.pendingBranches.addAll((Set<String>) map.getOrDefault("pendingBranches", new HashSet<>()));
            context.completedBranches = new ConcurrentHashMap<>(
                    (Map<String, Object>) map.getOrDefault("completedBranches", new HashMap<>()));
            context.failedBranches = new ConcurrentHashMap<>();
            Object failedInfoObj = map.get("failedBranches");
            if (failedInfoObj instanceof Map<?, ?> failedInfo) {
                failedInfo.forEach((k, v) -> {
                    if (k != null && v != null) {
                        context.failedBranches.put(String.valueOf(k), new RuntimeException(String.valueOf(v)));
                    }
                });
            }
            return context;
        }
    }
}
