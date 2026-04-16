package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import dev.langchain4j.data.message.ChatMessage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 并行执行节点
 *
 * <p><b>重要说明</b>：每个 branch.target 只会执行目标节点本身（单节点），
 * 不会沿图边继续推进。如需并行执行多节点子流程，请将 branch.target
 * 指向一个 SUBGRAPH 类型节点。
 *
 * <p><b>配置示例</b>：
 * <pre>
 * {
 *   "branches": [
 *     { "id": "branch1", "target": "subgraph_node_legal" },
 *     { "id": "branch2", "target": "subgraph_node_finance" }
 *   ],
 *   "joinNode": "join_node",
 *   "timeout": 30000,
 *   "waitAll": true,
 *   "errorStrategy": "failFast"
 * }
 * </pre>
 *
 * <p><b>行为说明</b>：
 * <ul>
 *   <li>同步等待所有分支完成后返回，结果写入 state.attributes</li>
 *   <li>执行完成后直接跳转到 joinNode，无需回环边</li>
 *   <li>failFast 模式下，任一分支失败会取消其他分支</li>
 *   <li>超时会取消所有分支并返回</li>
 * </ul>
 */
@Slf4j
@Component
public class ParallelNode implements AgentNode {

    public static final String NAME = "PARALLEL";

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

        return startParallelExecution(state, branchList, timeout, waitAll, errorStrategy, joinNode);
    }

    private ExecutionResult startParallelExecution(
            AgentState state,
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

        List<BranchConfig> branches = parseBranches(branchList);
        if (branches.isEmpty()) {
            log.warn("并行节点没有有效的分支配置");
            return new ExecutionResult(state, null);
        }

        long startTime = System.currentTimeMillis();

        Map<String, BranchResult> results = new ConcurrentHashMap<>();
        Map<String, Throwable> errors = new ConcurrentHashMap<>();
        AtomicBoolean failFastTriggered = new AtomicBoolean(false);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (BranchConfig branch : branches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> {
                        if (failFastTriggered.get()) {
                            return;
                        }

                        long branchStartedAt = System.currentTimeMillis();
                        try {
                            AgentState branchState = AgentUtils.deepCopyState(state);
                            branchState.getAttributes().put("__branch_id__", branch.id());

                            log.debug("并行分支 {} 开始执行，目标节点: {}", branch.id(), branch.target());

                            AgentState branchOutputState = nodeExecutor.apply(branch.target(), branchState);

                            long branchDuration = System.currentTimeMillis() - branchStartedAt;

                            BranchResult result = new BranchResult(
                                    branch.id(),
                                    branch.target(),
                                    true,
                                    branchOutputState,
                                    null,
                                    branchDuration,
                                    branchOutputState != null ? new ArrayList<>(branchOutputState.getMessages()) : null,
                                    extractInputTokens(branchOutputState),
                                    extractOutputTokens(branchOutputState));

                            results.put(branch.id(), result);

                            log.debug("并行分支 {} 执行完成，耗时: {}ms", branch.id(), branchDuration);

                        } catch (Exception e) {
                            long branchDuration = System.currentTimeMillis() - branchStartedAt;
                            log.error("并行分支 {} 执行失败，耗时: {}ms", branch.id(), branchDuration, e);

                            errors.put(branch.id(), e);
                            firstError.compareAndSet(null, e);

                            BranchResult result = new BranchResult(
                                    branch.id(),
                                    branch.target(),
                                    false,
                                    null,
                                    e.getMessage(),
                                    branchDuration,
                                    null,
                                    null,
                                    null);

                            results.put(branch.id(), result);

                            if ("failFast".equals(errorStrategy)) {
                                failFastTriggered.set(true);
                            }
                        }
                    },
                    executor);

            futures.add(future);
        }

        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            if (waitAll) {
                allFutures.get(timeout, TimeUnit.MILLISECONDS);
            } else {
                CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                        .get(timeout, TimeUnit.MILLISECONDS);
                futures.forEach(f -> f.cancel(true));
            }
        } catch (TimeoutException e) {
            log.warn("并行节点执行超时 {}ms，强制取消所有分支", timeout);
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("并行节点执行被中断", e);
            futures.forEach(f -> f.cancel(true));
        } catch (ExecutionException e) {
            log.error("并行节点执行异常", e.getCause());
        }

        if (failFastTriggered.get() && "failFast".equals(errorStrategy)) {
            log.warn("并行节点因 failFast 策略取消其他分支");
            futures.forEach(f -> f.cancel(true));
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        if (state.getAttributes() == null) {
            state.setAttributes(new ConcurrentHashMap<>());
        }

        Map<String, Object> serializedResults = new HashMap<>();
        results.forEach((id, result) -> serializedResults.put(id, result.toMap()));
        state.getAttributes().put(PARALLEL_RESULTS_KEY, serializedResults);

        if (!errors.isEmpty()) {
            Map<String, String> errorMessages = new HashMap<>();
            errors.forEach((id, e) -> errorMessages.put(id, e.getMessage()));
            state.getAttributes().put(PARALLEL_ERRORS_KEY, errorMessages);
        }

        log.info("并行节点执行完成，成功: {}, 失败: {}, 总耗时: {}ms", results.size() - errors.size(), errors.size(), totalDuration);

        return new ExecutionResult(state, joinNode);
    }

    private List<BranchConfig> parseBranches(List<?> branchList) {
        List<BranchConfig> branches = new ArrayList<>();
        for (Object branchObj : branchList) {
            if (branchObj instanceof Map<?, ?> branch) {
                String branchId = (String) branch.get("id");
                String target = (String) branch.get("target");
                if (branchId != null && target != null) {
                    branches.add(new BranchConfig(branchId, target));
                }
            }
        }
        return branches;
    }

    private Integer extractInputTokens(AgentState state) {
        if (state == null || state.getAttributes() == null) {
            return null;
        }
        Object tokens = state.getAttributes().get("_inputTokens");
        if (!(tokens instanceof Number)) {
            tokens = state.getAttributes().get("promptTokens");
        }
        return tokens instanceof Number n ? n.intValue() : null;
    }

    private Integer extractOutputTokens(AgentState state) {
        if (state == null || state.getAttributes() == null) {
            return null;
        }
        Object tokens = state.getAttributes().get("_outputTokens");
        if (!(tokens instanceof Number)) {
            tokens = state.getAttributes().get("completionTokens");
        }
        return tokens instanceof Number n ? n.intValue() : null;
    }

    private record BranchConfig(String id, String target) {}

    /**
     * 分支执行结果
     */
    public record BranchResult(
            String branchId,
            String targetNode,
            boolean success,
            AgentState outputState,
            String errorMessage,
            long durationMs,
            List<ChatMessage> messages,
            Integer inputTokens,
            Integer outputTokens) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("branchId", branchId);
            map.put("targetNode", targetNode);
            map.put("success", success);
            map.put("durationMs", durationMs);
            if (errorMessage != null) {
                map.put("errorMessage", errorMessage);
            }
            if (outputState != null && outputState.getAttributes() != null) {
                map.put("outputAttributes", new HashMap<>(outputState.getAttributes()));
            }
            if (messages != null && !messages.isEmpty()) {
                map.put("messages", new ArrayList<>(messages));
            }
            if (inputTokens != null) {
                map.put("inputTokens", inputTokens);
            }
            if (outputTokens != null) {
                map.put("outputTokens", outputTokens);
            }
            return map;
        }
    }
}
