package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import dev.langchain4j.data.message.ChatMessage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
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
        Map<String, String> cancellations = new ConcurrentHashMap<>();
        AtomicBoolean failFastTriggered = new AtomicBoolean(false);
        List<BranchTask> branchTasks = new ArrayList<>();

        for (BranchConfig branch : branches) {
            FutureTask<BranchResult> futureTask = new FutureTask<>(
                    () -> executeBranch(state, nodeExecutor, branch, errorStrategy, failFastTriggered));
            executor.execute(futureTask);
            branchTasks.add(new BranchTask(branch, futureTask));
        }

        try {
            if (waitAll) {
                collectAllBranchResults(branchTasks, timeout, results, errors, cancellations);
            } else {
                waitForAnyBranch(branchTasks, timeout);
                cancelUnfinishedBranches(branchTasks);
                collectSettledBranchResults(branchTasks, results, errors, cancellations);
            }
        } catch (TimeoutException e) {
            log.warn("并行节点执行超时 {}ms，强制取消所有分支", timeout);
            cancelUnfinishedBranches(branchTasks);
            collectSettledBranchResults(branchTasks, results, errors, cancellations);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("并行节点执行被中断", e);
            cancelUnfinishedBranches(branchTasks);
            collectSettledBranchResults(branchTasks, results, errors, cancellations);
        }

        if (failFastTriggered.get() && "failFast".equals(errorStrategy)) {
            log.warn("并行节点因 failFast 策略取消其他分支");
            cancelUnfinishedBranches(branchTasks);
            collectSettledBranchResults(branchTasks, results, errors, cancellations);
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
        } else {
            state.getAttributes().remove(PARALLEL_ERRORS_KEY);
        }

        long successCount =
                results.values().stream().filter(BranchResult::success).count();
        long cancelledCount =
                results.values().stream().filter(BranchResult::cancelled).count();
        log.info(
                "并行节点执行完成，成功: {}, 失败: {}, 取消: {}, 总耗时: {}ms",
                successCount,
                errors.size(),
                cancelledCount,
                totalDuration);

        return new ExecutionResult(state, joinNode);
    }

    private BranchResult executeBranch(
            AgentState state,
            BiFunction<String, AgentState, AgentState> nodeExecutor,
            BranchConfig branch,
            String errorStrategy,
            AtomicBoolean failFastTriggered) {
        long branchStartedAt = System.currentTimeMillis();
        if (failFastTriggered.get() || Thread.currentThread().isInterrupted()) {
            return cancelledBranchResult(branch, branchStartedAt, "Branch execution was cancelled before start");
        }
        try {
            AgentState branchState = AgentUtils.deepCopyState(state);
            branchState.getAttributes().put("__branch_id__", branch.id());

            log.debug("并行分支 {} 开始执行，目标节点: {}", branch.id(), branch.target());

            AgentState branchOutputState = nodeExecutor.apply(branch.target(), branchState);

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Branch execution was interrupted");
            }

            long branchDuration = System.currentTimeMillis() - branchStartedAt;
            log.debug("并行分支 {} 执行完成，耗时: {}ms", branch.id(), branchDuration);
            return new BranchResult(
                    branch.id(),
                    branch.target(),
                    true,
                    false,
                    branchOutputState,
                    null,
                    branchDuration,
                    extractAppendedMessages(state, branchOutputState),
                    extractInputTokens(branchOutputState),
                    extractOutputTokens(branchOutputState));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long branchDuration = System.currentTimeMillis() - branchStartedAt;
            log.warn("并行分支 {} 被取消，耗时: {}ms", branch.id(), branchDuration);
            return new BranchResult(
                    branch.id(), branch.target(), false, true, null, e.getMessage(), branchDuration, null, null, null);
        } catch (Exception e) {
            long branchDuration = System.currentTimeMillis() - branchStartedAt;
            log.error("并行分支 {} 执行失败，耗时: {}ms", branch.id(), branchDuration, e);
            if ("failFast".equals(errorStrategy)) {
                failFastTriggered.set(true);
            }
            return new BranchResult(
                    branch.id(), branch.target(), false, false, null, e.getMessage(), branchDuration, null, null, null);
        }
    }

    private void collectAllBranchResults(
            List<BranchTask> branchTasks,
            long timeout,
            Map<String, BranchResult> results,
            Map<String, Throwable> errors,
            Map<String, String> cancellations)
            throws InterruptedException, TimeoutException {
        long deadline = System.currentTimeMillis() + timeout;
        for (BranchTask branchTask : branchTasks) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new TimeoutException("Parallel execution timed out");
            }
            collectBranchResult(branchTask, remaining, results, errors, cancellations);
        }
    }

    private void waitForAnyBranch(List<BranchTask> branchTasks, long timeout)
            throws InterruptedException, TimeoutException {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            for (BranchTask branchTask : branchTasks) {
                if (branchTask.future().isDone()) {
                    return;
                }
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new TimeoutException("Parallel execution timed out");
    }

    private void cancelUnfinishedBranches(List<BranchTask> branchTasks) {
        for (BranchTask branchTask : branchTasks) {
            if (!branchTask.future().isDone()) {
                branchTask.future().cancel(true);
            }
        }
    }

    private void collectSettledBranchResults(
            List<BranchTask> branchTasks,
            Map<String, BranchResult> results,
            Map<String, Throwable> errors,
            Map<String, String> cancellations) {
        for (BranchTask branchTask : branchTasks) {
            if (results.containsKey(branchTask.branch().id())) {
                continue;
            }
            if (branchTask.future().isCancelled()) {
                BranchResult cancelled = cancelledBranchResult(
                        branchTask.branch(), System.currentTimeMillis(), "Branch execution was cancelled");
                storeBranchResult(cancelled, results, errors, cancellations, null);
                continue;
            }
            if (!branchTask.future().isDone()) {
                continue;
            }
            try {
                collectBranchResult(branchTask, 1, results, errors, cancellations);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (TimeoutException ignored) {
                return;
            }
        }
    }

    private void collectBranchResult(
            BranchTask branchTask,
            long timeoutMillis,
            Map<String, BranchResult> results,
            Map<String, Throwable> errors,
            Map<String, String> cancellations)
            throws InterruptedException, TimeoutException {
        BranchResult result;
        try {
            result = branchTask.future().get(timeoutMillis, TimeUnit.MILLISECONDS);
            storeBranchResult(result, results, errors, cancellations, null);
        } catch (CancellationException e) {
            result = cancelledBranchResult(
                    branchTask.branch(), System.currentTimeMillis(), "Branch execution was cancelled");
            storeBranchResult(result, results, errors, cancellations, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            result = new BranchResult(
                    branchTask.branch().id(),
                    branchTask.branch().target(),
                    false,
                    false,
                    null,
                    cause.getMessage(),
                    0L,
                    null,
                    null,
                    null);
            storeBranchResult(result, results, errors, cancellations, cause);
        }
    }

    private void storeBranchResult(
            BranchResult result,
            Map<String, BranchResult> results,
            Map<String, Throwable> errors,
            Map<String, String> cancellations,
            Throwable throwable) {
        results.put(result.branchId(), result);
        if (result.cancelled()) {
            cancellations.put(result.branchId(), result.errorMessage());
            return;
        }
        if (!result.success()) {
            Throwable error = throwable == null ? new RuntimeException(result.errorMessage()) : throwable;
            errors.put(result.branchId(), error);
        }
    }

    private BranchResult cancelledBranchResult(BranchConfig branch, long startedAt, String message) {
        long duration = Math.max(0L, System.currentTimeMillis() - startedAt);
        return new BranchResult(branch.id(), branch.target(), false, true, null, message, duration, null, null, null);
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

    private List<ChatMessage> extractAppendedMessages(AgentState inputState, AgentState outputState) {
        if (outputState == null
                || outputState.getMessages() == null
                || outputState.getMessages().isEmpty()) {
            return null;
        }
        return getChatMessages(inputState, outputState);
    }

    @NonNull
    static List<ChatMessage> getChatMessages(AgentState inputState, AgentState outputState) {
        int inputSize = inputState == null || inputState.getMessages() == null
                ? 0
                : inputState.getMessages().size();
        if (outputState.getMessages().size() <= inputSize) {
            return List.of();
        }
        return new ArrayList<>(outputState
                .getMessages()
                .subList(inputSize, outputState.getMessages().size()));
    }

    private record BranchConfig(String id, String target) {}

    private record BranchTask(BranchConfig branch, FutureTask<BranchResult> future) {}

    /**
     * 分支执行结果
     */
    public record BranchResult(
            String branchId,
            String targetNode,
            boolean success,
            boolean cancelled,
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
            map.put("cancelled", cancelled);
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
