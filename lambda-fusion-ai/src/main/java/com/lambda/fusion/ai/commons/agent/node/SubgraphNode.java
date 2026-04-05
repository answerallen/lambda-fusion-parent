package com.lambda.fusion.ai.commons.agent.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SubgraphNode implements AgentNode {

    public static final String NAME = "SUBGRAPH";

    private static final String SUBGRAPH_CONTEXT_KEY = "__subgraph_context__";
    private static final String SUBGRAPH_RESULT_KEY = "__subgraph_result__";
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
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("子图节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        SubgraphContext context = getOrCreateSubgraphContext(state);

        if (context.isCompleted) {
            clearSubgraphContext(state);
            String nextNode = (String) properties.get("nextNode");
            return new ExecutionResult(state, nextNode);
        }

        if (context.isExecuting && context.isAsync) {
            return checkAsyncCompletion(state, context, properties);
        }

        return startSubgraphExecution(state, context, properties);
    }

    private ExecutionResult startSubgraphExecution(
            AgentState state, SubgraphContext context, Map<String, Object> properties) {

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

            context.isExecuting = true;
            context.isAsync = async;
            context.startTime = System.currentTimeMillis();
            context.propagateErrors = propagateErrors;
            saveSubgraphContext(state, context);

            if (async) {
                return executeAsync(state, context, subgraph, subgraphInputState, properties);
            } else {
                return executeSync(state, context, subgraph, subgraphInputState, properties);
            }

        } catch (Exception e) {
            log.error("子图执行异常", e);
            handleExecutionError(state, context, e, propagateErrors);
            return new ExecutionResult(state, null);
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
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                log.warn("无法解析 subgraphId: {}", value);
                return null;
            }
        }
        return null;
    }

    private ExecutionResult executeSync(
            AgentState state,
            SubgraphContext context,
            AgentGraph subgraph,
            AgentState subgraphInputState,
            Map<String, Object> properties) {

        try {
            AgentState subgraphOutputState = subgraph.invoke(subgraphInputState);
            return handleSubgraphOutput(state, context, subgraphOutputState, properties);
        } catch (Exception e) {
            log.error("同步子图执行异常", e);
            handleExecutionError(state, context, e, context.propagateErrors);
            return new ExecutionResult(state, null);
        }
    }

    private ExecutionResult executeAsync(
            AgentState state,
            SubgraphContext context,
            AgentGraph subgraph,
            AgentState subgraphInputState,
            Map<String, Object> properties) {

        long asyncTimeout = getAsyncTimeout();
        AtomicReference<SubgraphContext> contextRef = new AtomicReference<>(context);

        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return subgraph.invoke(subgraphInputState);
                            } catch (Exception e) {
                                log.error("异步子图执行异常", e);
                                throw new RuntimeException(e);
                            }
                        },
                        executor)
                .orTimeout(asyncTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    SubgraphContext ctx = contextRef.get();
                    ctx.isCompleted = true;
                    ctx.completedAt = System.currentTimeMillis();

                    if (error != null) {
                        ctx.error = error.getMessage();
                    } else if (result != null) {
                        ctx.asyncResult = result;
                    }

                    log.info("异步子图执行完成，耗时: {}ms", ctx.completedAt - ctx.startTime);
                });

        log.debug("异步子图已启动，继续执行父图");
        String nextNode = (String) properties.get("nextNode");
        return new ExecutionResult(state, nextNode);
    }

    private ExecutionResult checkAsyncCompletion(
            AgentState state, SubgraphContext context, Map<String, Object> properties) {

        if (context.isCompleted) {
            String nextNode = (String) properties.get("nextNode");

            if (context.error != null && context.propagateErrors) {
                if (state.getAttributes() == null) {
                    state.setAttributes(new ConcurrentHashMap<>());
                }
                state.getAttributes().put(SUBGRAPH_ERROR_KEY, context.error);
            }

            if (context.asyncResult != null) {
                if (state.getAttributes() == null) {
                    state.setAttributes(new ConcurrentHashMap<>());
                }
                state.getAttributes().put(SUBGRAPH_RESULT_KEY, context.asyncResult);
            }

            clearSubgraphContext(state);
            return new ExecutionResult(state, nextNode);
        }

        return new ExecutionResult(state, null);
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

    @SuppressWarnings("unchecked")
    private ExecutionResult handleSubgraphOutput(
            AgentState parentState,
            SubgraphContext context,
            AgentState subgraphOutputState,
            Map<String, Object> properties) {

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

        context.isCompleted = true;
        context.completedAt = System.currentTimeMillis();

        long duration = context.completedAt - context.startTime;
        log.info("子图执行完成，耗时: {}ms", duration);

        clearSubgraphContext(subgraphOutputState);

        String nextNode = (String) properties.get("nextNode");
        return new ExecutionResult(parentState, nextNode);
    }

    private void handleExecutionError(AgentState state, SubgraphContext context, Exception e, boolean propagateErrors) {
        context.isCompleted = true;
        context.error = e.getMessage();
        saveSubgraphContext(state, context);

        if (propagateErrors && state.getAttributes() != null) {
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

        if (parts.length >= 2 && "attributes".equals(parts[0])) {
            if (state.getAttributes() != null) {
                Object current = state.getAttributes().get(parts[1]);
                for (int i = 2; i < parts.length && current instanceof Map; i++) {
                    current = ((Map<String, Object>) current).get(parts[i]);
                }
                return current;
            }
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

    @SuppressWarnings("unchecked")
    private SubgraphContext getOrCreateSubgraphContext(AgentState state) {
        if (state.getAttributes() != null) {
            Object contextObj = state.getAttributes().get(SUBGRAPH_CONTEXT_KEY);
            if (contextObj instanceof Map<?, ?> contextMap) {
                return SubgraphContext.fromMap((Map<String, Object>) contextMap);
            }
        }
        return new SubgraphContext();
    }

    private void saveSubgraphContext(AgentState state, SubgraphContext context) {
        if (state.getAttributes() != null) {
            state.getAttributes().put(SUBGRAPH_CONTEXT_KEY, context.toMap());
        }
    }

    private void clearSubgraphContext(AgentState state) {
        if (state.getAttributes() != null) {
            state.getAttributes().remove(SUBGRAPH_CONTEXT_KEY);
        }
    }

    private static class SubgraphContext {
        boolean isExecuting = false;
        boolean isCompleted = false;
        boolean isAsync = false;
        boolean propagateErrors = true;
        long startTime = 0;
        long completedAt = 0;
        String error = null;
        transient volatile AgentState asyncResult = null;

        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("isExecuting", isExecuting);
            map.put("isCompleted", isCompleted);
            map.put("isAsync", isAsync);
            map.put("propagateErrors", propagateErrors);
            map.put("startTime", startTime);
            map.put("completedAt", completedAt);
            map.put("error", error);
            return map;
        }

        static SubgraphContext fromMap(Map<String, Object> map) {
            SubgraphContext context = new SubgraphContext();
            context.isExecuting = Boolean.TRUE.equals(map.get("isExecuting"));
            context.isCompleted = Boolean.TRUE.equals(map.get("isCompleted"));
            context.isAsync = Boolean.TRUE.equals(map.get("isAsync"));
            Object propagateErrorsVal = map.get("propagateErrors");
            context.propagateErrors = propagateErrorsVal == null || Boolean.TRUE.equals(propagateErrorsVal);
            Object startTimeVal = map.get("startTime");
            context.startTime = startTimeVal instanceof Number n ? n.longValue() : 0L;
            Object completedAtVal = map.get("completedAt");
            context.completedAt = completedAtVal instanceof Number n ? n.longValue() : 0L;
            context.error = (String) map.get("error");
            return context;
        }
    }
}
