package com.lambda.fusion.ai.commons.agent.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 子图执行节点
 *
 * <p>用于在工作流中嵌入执行另一个完整的工作流（子图）。
 *
 * <p><b>配置示例</b>：
 * <pre>
 * {
 *   "subgraphId": 123,
 *   "subgraphName": "legal_review_workflow",
 *   "inheritContext": false,
 *   "async": false,
 *   "propagateErrors": true,
 *   "inputMapping": {
 *     "query": "attributes.userQuery",
 *     "context": "attributes.context"
 *   },
 *   "outputMapping": {
 *     "attributes.legalOpinion": "opinion",
 *     "attributes.riskLevel": "riskLevel"
 *   },
 *   "nextNode": "next_step"
 * }
 * </pre>
 *
 * <p><b>行为说明</b>：
 * <ul>
 *   <li>同步模式（async=false）：在当前线程中执行子图，等待完成后继续</li>
 *   <li>异步模式（async=true）：在线程池中执行子图，但同步等待完成后再返回</li>
 *   <li>两种模式都确保子图结果在节点返回前写入 state</li>
 * </ul>
 */
@Slf4j
@Component
public class SubgraphNode implements AgentNode {

    public static final String NAME = "SUBGRAPH";

    private static final String SUBGRAPH_ERROR_KEY = "__subgraph_error__";
    private static final long DEFAULT_ASYNC_TIMEOUT = 30000;

    private final AgentGraphFactory graphFactory;
    private final WorkflowService workflowService;
    private final Executor executor;
    private final AiProperties.AgentConfig agentConfig;

    public SubgraphNode(
            AgentGraphFactory graphFactory,
            WorkflowService workflowService,
            @Qualifier("agentParallelExecutor") Executor executor,
            AiProperties aiProperties) {
        this.graphFactory = graphFactory;
        this.workflowService = workflowService;
        this.executor = executor;
        this.agentConfig = aiProperties != null ? aiProperties.getAgent() : null;
    }

    private long getAsyncTimeout() {
        if (agentConfig != null && agentConfig.getSubgraph() != null) {
            return agentConfig.getSubgraph().getAsyncTimeout();
        }
        return DEFAULT_ASYNC_TIMEOUT;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("子图节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        String subgraphDefinition = (String) properties.get("subgraphDefinition");
        Long subgraphId = parseSubgraphId(properties.get("subgraphId"));
        String subgraphName = (String) properties.get("subgraphName");
        boolean inheritContext = Boolean.TRUE.equals(properties.getOrDefault("inheritContext", false));
        boolean async = Boolean.TRUE.equals(properties.getOrDefault("async", false));
        boolean propagateErrors = !Boolean.FALSE.equals(properties.getOrDefault("propagateErrors", true));

        if (subgraphDefinition == null && subgraphId == null && subgraphName == null) {
            log.warn("子图节点缺少子图定义（subgraphDefinition、subgraphId 或 subgraphName）");
            return new ExecutionResult(state, null);
        }

        try {
            AgentGraph subgraph = loadSubgraph(subgraphDefinition, subgraphId, subgraphName);

            if (subgraph == null) {
                log.warn("无法加载子图定义: id={}, name={}", subgraphId, subgraphName);
                return new ExecutionResult(state, null);
            }

            AgentState subgraphInputState = prepareSubgraphInput(state, properties, inheritContext);

            long startTime = System.currentTimeMillis();

            if (async) {
                return executeAsync(state, subgraph, subgraphInputState, properties, propagateErrors, startTime);
            } else {
                return executeSync(state, subgraph, subgraphInputState, properties, propagateErrors, startTime);
            }

        } catch (Exception e) {
            log.error("子图执行异常", e);
            handleExecutionError(state, e, propagateErrors);
            return new ExecutionResult(state, (String) properties.get("nextNode"));
        }
    }

    private AgentGraph loadSubgraph(String subgraphDefinition, Long subgraphId, String subgraphName) throws Exception {
        if (subgraphDefinition != null && !subgraphDefinition.isBlank()) {
            log.debug("使用内联子图定义");
            return graphFactory.buildFromDefinition(subgraphDefinition);
        }

        WorkflowEntity workflow = null;

        if (subgraphId != null) {
            log.debug("从工作流存储加载子图定义, id={}", subgraphId);
            workflow = workflowService.getById(subgraphId);
        }

        if (workflow == null && subgraphName != null && !subgraphName.isBlank()) {
            log.debug("从工作流存储加载子图定义, name={}", subgraphName);
            workflow = workflowService.getOne(new LambdaQueryWrapper<WorkflowEntity>()
                    .eq(WorkflowEntity::getName, subgraphName)
                    .eq(WorkflowEntity::getEnabled, true));
        }

        if (workflow != null && workflow.getGraphJson() != null) {
            return graphFactory.buildFromDefinition(workflow.getGraphJson());
        }

        return null;
    }

    private Long parseSubgraphId(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Number number -> {
                return number.longValue();
            }
            case String str
            when !str.isBlank() -> {
                try {
                    return Long.parseLong(str.trim());
                } catch (NumberFormatException e) {
                    log.warn("无法解析 subgraphId: {}", value);
                    return null;
                }
            }
            default -> {}
        }
        return null;
    }

    private ExecutionResult executeSync(
            AgentState state,
            AgentGraph subgraph,
            AgentState subgraphInputState,
            Map<String, Object> properties,
            boolean propagateErrors,
            long startTime) {

        try {
            AgentState subgraphOutputState = subgraph.invoke(subgraphInputState);
            return handleSubgraphOutput(state, subgraphOutputState, properties, startTime);
        } catch (Exception e) {
            log.error("同步子图执行异常", e);
            handleExecutionError(state, e, propagateErrors);
            return new ExecutionResult(state, (String) properties.get("nextNode"));
        }
    }

    private ExecutionResult executeAsync(
            AgentState state,
            AgentGraph subgraph,
            AgentState subgraphInputState,
            Map<String, Object> properties,
            boolean propagateErrors,
            long startTime) {

        long asyncTimeout = getAsyncTimeout();

        try {
            AgentState subgraphOutputState = CompletableFuture.supplyAsync(
                            () -> subgraph.invoke(subgraphInputState), executor)
                    .orTimeout(asyncTimeout, TimeUnit.MILLISECONDS)
                    .get();

            return handleSubgraphOutput(state, subgraphOutputState, properties, startTime);

        } catch (CancellationException e) {
            log.warn("子图异步执行被取消");
            return new ExecutionResult(state, (String) properties.get("nextNode"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(state, (String) properties.get("nextNode"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("子图异步执行超时 {}ms", asyncTimeout);
                handleExecutionError(state, new RuntimeException("子图执行超时"), propagateErrors);
            } else {
                log.error("子图异步执行异常", cause);
                handleExecutionError(state, new RuntimeException(cause), propagateErrors);
            }
            return new ExecutionResult(state, (String) properties.get("nextNode"));
        }
    }

    private AgentState prepareSubgraphInput(
            AgentState parentState, Map<String, Object> properties, boolean inheritContext) {
        AgentState subgraphState = new AgentState();

        subgraphState.setSessionId(parentState.getSessionId());
        subgraphState.setKbId(parentState.getKbId());
        subgraphState.setLlmModelId(parentState.getLlmModelId());

        Object inputMappingObj = properties.get("inputMapping");
        if (inputMappingObj instanceof Map<?, ?> inputMapping) {
            Map<String, Object> attributes = new HashMap<>();

            inputMapping.forEach((key, value) -> {
                String targetKey = String.valueOf(key);
                String sourcePath = String.valueOf(value);
                Object sourceValue = extractValueFromState(parentState, sourcePath);
                attributes.put(targetKey, sourceValue);
            });

            subgraphState.setAttributes(attributes);
        }

        if (inheritContext && parentState.getAttributes() != null) {
            if (subgraphState.getAttributes() == null) {
                subgraphState.setAttributes(new HashMap<>());
            }
            subgraphState.getAttributes().putAll(parentState.getAttributes());
        }

        return subgraphState;
    }

    private ExecutionResult handleSubgraphOutput(
            AgentState parentState, AgentState subgraphOutputState, Map<String, Object> properties, long startTime) {

        Object outputMappingObj = properties.get("outputMapping");
        if (outputMappingObj instanceof Map<?, ?> outputMapping) {
            if (parentState.getAttributes() == null) {
                parentState.setAttributes(new HashMap<>());
            }

            outputMapping.forEach((key, value) -> {
                String targetPath = String.valueOf(key);
                String sourceKey = String.valueOf(value);

                Object sourceValue = null;
                if (subgraphOutputState.getAttributes() != null) {
                    sourceValue = subgraphOutputState.getAttributes().get(sourceKey);
                }

                setValueToState(parentState, targetPath, sourceValue);
            });
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("子图执行完成，耗时: {}ms", duration);

        String nextNode = (String) properties.get("nextNode");
        return new ExecutionResult(parentState, nextNode);
    }

    private void handleExecutionError(AgentState state, Exception e, boolean propagateErrors) {
        if (propagateErrors) {
            if (state.getAttributes() == null) {
                state.setAttributes(new HashMap<>());
            }
            state.getAttributes().put(SUBGRAPH_ERROR_KEY, e.getMessage());
        }
    }

    private Object extractValueFromState(AgentState state, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            return switch (parts[0]) {
                case "sessionId" -> state.getSessionId();
                case "kbId" -> state.getKbId();
                case "llmModelId" -> state.getLlmModelId();
                case "messages" -> state.getMessages();
                case "attributes" -> state.getAttributes();
                case "pendingToolRequests" -> state.getPendingToolRequests();
                case "finished" -> state.isFinished();
                default -> state.getAttributes() != null ? state.getAttributes().get(parts[0]) : null;
            };
        }

        if (parts.length >= 2 && "attributes".equals(parts[0]) && state.getAttributes() != null) {
            // 1. 获取初始节点
            Object root = state.getAttributes().get(parts[1]);
            // 2. 使用 reduce 逐层深入
            return Arrays.stream(parts)
                    .skip(2)
                    .reduce(
                            root,
                            (current, key) -> (current instanceof Map<?, ?> map) ? map.get(key) : null,
                            (a, b) -> b);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void setValueToState(AgentState state, String path, Object value) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            if (state.getAttributes() == null) {
                state.setAttributes(new HashMap<>());
            }
            state.getAttributes().put(parts[0], value);
        } else if (parts.length >= 2 && "attributes".equals(parts[0])) {
            if (state.getAttributes() == null) {
                state.setAttributes(new HashMap<>());
            }

            Map<String, Object> current = state.getAttributes();
            for (int i = 1; i < parts.length - 1; i++) {
                current.computeIfAbsent(parts[i], k -> new HashMap<String, Object>());
                Object next = current.get(parts[i]);
                if (!(next instanceof Map)) {
                    next = new HashMap<String, Object>();
                    current.put(parts[i], next);
                }
                current = (Map<String, Object>) next;
            }
            current.put(parts[parts.length - 1], value);
        }
    }
}
