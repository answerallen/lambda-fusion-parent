package com.lambda.fusion.ai.commons.agent;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import com.lambda.fusion.ai.commons.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.util.StringUtils;

/**
 * 智能体可视化工作流执行引擎
 * <p>
 * 线程安全设计：
 * - edges 列表使用 Collections.synchronizedList 保证线程安全
 * - nodes 映射使用 Collections.synchronizedMap 保证线程安全
 */
@Slf4j
public class AgentGraph {

    public static final String END_NODE = "END";
    public static final int DEFAULT_MAX_ITERATIONS = 25;
    public static final String TRACE_ENABLED_ATTRIBUTE = "_traceEnabled";
    public static final String EXECUTION_TRACE_ATTRIBUTE = "_executionTrace";
    public static final String EXECUTION_STATS_ATTRIBUTE = "_executionStats";
    public static final String LAST_ROUTE_ATTRIBUTE = "_lastRoute";
    public static final String CURRENT_NODE_ID_ATTRIBUTE = "_currentNodeId";
    public static final String CURRENT_NODE_TYPE_ATTRIBUTE = "_currentNodeType";
    public static final String CURRENT_NODE_PROPERTIES_ATTRIBUTE = "_currentNodeProperties";
    public static final String GRAPH_NODE_PROPERTIES_ATTRIBUTE = "_graphNodeProperties";
    public static final String AVAILABLE_NODES_ATTRIBUTE = "_availableNodes";
    private static final int MAX_TRACE_ENTRIES = 64;

    private final Map<String, AgentNode> nodes = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Map<String, Object>> nodeProperties = Collections.synchronizedMap(new HashMap<>());
    private final List<Edge> edges = Collections.synchronizedList(new ArrayList<>());
    private String startNodeId;
    private volatile CompiledGraph<LangGraphRuntimeState> compiledGraph;
    private volatile boolean dirty = true;
    private volatile int maxIterations = DEFAULT_MAX_ITERATIONS;
    private volatile CompileConfig compileConfig;

    public static class AgentGraphExecutionException extends IllegalStateException {
        private final transient AgentState state;

        public AgentGraphExecutionException(String message, AgentState state) {
            super(message);
            this.state = state;
        }

        public AgentGraphExecutionException(String message, Throwable cause, AgentState state) {
            super(message, cause);
            this.state = state;
        }

        public AgentState getState() {
            return state;
        }
    }

    public static class LangGraphRuntimeState extends org.bsc.langgraph4j.state.AgentState {
        public static final String SESSION_ID_KEY = "sessionId";
        public static final String KB_ID_KEY = "kbId";
        public static final String LLM_MODEL_ID_KEY = "llmModelId";
        public static final String MESSAGES_KEY = "messages";
        public static final String PENDING_TOOL_REQUESTS_KEY = "pendingToolRequests";
        public static final String ATTRIBUTES_KEY = "attributes";
        public static final String FINISHED_KEY = "finished";
        public static final String NEXT_NODE_KEY = "_nextNode";
        private static final Map<String, Channel<?>> SCHEMA = Map.of(
                SESSION_ID_KEY,
                Channels.base(() -> "0"),
                KB_ID_KEY,
                Channels.base(() -> "0"),
                LLM_MODEL_ID_KEY,
                Channels.base(() -> "0"),
                MESSAGES_KEY,
                Channels.appender(ArrayList::new),
                PENDING_TOOL_REQUESTS_KEY,
                Channels.base((Supplier<List<ToolExecutionRequest>>) ArrayList::new),
                ATTRIBUTES_KEY,
                Channels.base((Supplier<Map<String, Object>>) HashMap::new),
                FINISHED_KEY,
                Channels.base(() -> false),
                NEXT_NODE_KEY,
                Channels.base(() -> ""));

        LangGraphRuntimeState(Map<String, Object> initData) {
            super(initData);
        }

        public static Map<String, Object> toInput(AgentState agentState) {
            Map<String, Object> input = new HashMap<>();
            input.put(SESSION_ID_KEY, agentState.getSessionId());
            input.put(KB_ID_KEY, agentState.getKbId());
            input.put(LLM_MODEL_ID_KEY, agentState.getLlmModelId());
            input.put(
                    MESSAGES_KEY,
                    agentState.getMessages() == null ? List.of() : new ArrayList<>(agentState.getMessages()));
            input.put(
                    PENDING_TOOL_REQUESTS_KEY,
                    agentState.getPendingToolRequests() == null
                            ? List.of()
                            : new ArrayList<>(agentState.getPendingToolRequests()));
            input.put(ATTRIBUTES_KEY, sanitizeAttributes(agentState.getAttributes()));
            input.put(FINISHED_KEY, agentState.isFinished());
            return input;
        }

        public AgentState agentState() {
            AgentState state = new AgentState();
            state.setSessionId(this.<String>value(SESSION_ID_KEY).orElse(null));
            state.setKbId(this.<String>value(KB_ID_KEY).orElse(null));
            state.setLlmModelId(this.<String>value(LLM_MODEL_ID_KEY).orElse(null));
            state.setMessages(
                    new ArrayList<>(this.<List<ChatMessage>>value(MESSAGES_KEY).orElse(List.of())));
            state.setPendingToolRequests(
                    new ArrayList<>(this.<List<ToolExecutionRequest>>value(PENDING_TOOL_REQUESTS_KEY)
                            .orElse(List.of())));
            state.setAttributes(new HashMap<>(
                    this.<Map<String, Object>>value(ATTRIBUTES_KEY).orElse(Map.of())));
            state.setFinished(this.<Boolean>value(FINISHED_KEY).orElse(false));
            return state;
        }

        public String nextNode() {
            return this.<String>value(NEXT_NODE_KEY).orElse(null);
        }
    }

    @Data
    public static class Edge {
        private String sourceId;
        private String targetId;
        private ConditionEvaluator conditionEvaluator;
        private String conditionExpression;
    }

    /**
     * 注册节点(带独立ID前缀)
     * <p>
     * 线程安全：使用 synchronized 保证复合操作的原子性
     */
    public synchronized AgentGraph addNode(String id, AgentNode node) {
        return addNode(id, node, Map.of());
    }

    /**
     * 注册节点(带属性)
     * <p>
     * 线程安全：使用 synchronized 保证 nodes 和 nodeProperties 操作的原子性
     */
    public synchronized AgentGraph addNode(String id, AgentNode node, Map<String, Object> properties) {
        if (!StringUtils.hasText(id) || node == null) {
            throw new IllegalArgumentException("Invalid node registration");
        }
        nodes.put(id, node);
        nodeProperties.put(id, properties == null ? Map.of() : new LinkedHashMap<>(properties));
        markDirty();
        return this;
    }

    /**
     * 增加边缘连线规划
     * <p>
     * 线程安全：使用 synchronized 保证 edges 操作的原子性
     */
    public synchronized AgentGraph addEdge(
            String sourceId, String targetId, ConditionEvaluator evaluator, String expression) {
        Edge edge = new Edge();
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setConditionEvaluator(evaluator);
        edge.setConditionExpression(expression);
        edges.add(edge);
        markDirty();
        return this;
    }

    /**
     * 声明图启动的入口Node ID
     * <p>
     * 线程安全：使用 synchronized 保证复合操作的原子性
     */
    public synchronized AgentGraph setEntryPoint(String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node id " + nodeId + " must be added before setting as entry point");
        }
        this.startNodeId = nodeId;
        markDirty();
        return this;
    }

    public void setCompileConfig(CompileConfig config) {
        this.compileConfig = config;
        markDirty();
    }

    public void setMaxIterations(int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be greater than 0");
        }
        this.maxIterations = maxIterations;
        markDirty();
    }

    /**
     * 引擎启动，按照 Edges 与 Node 返回态进行流转分发
     */
    public AgentState invoke(AgentState state) {
        return invokeOptional(state, null).orElse(state);
    }

    public Optional<AgentState> invokeOptional(AgentState state, RunnableConfig runnableConfig) {
        validateInputState(state);
        initializeObservability(state);
        long invokeStartedAt = System.nanoTime();
        CompiledGraph<LangGraphRuntimeState> executable = getOrBuildCompiledGraph();
        Optional<LangGraphRuntimeState> output;
        try {
            output = runnableConfig == null
                    ? executable.invoke(LangGraphRuntimeState.toInput(state))
                    : executable.invoke(LangGraphRuntimeState.toInput(state), runnableConfig);
        } catch (RuntimeException e) {
            throw propagateExecutionException(e, invokeStartedAt);
        }
        return output.map(runtimeState -> {
            AgentState finalState = runtimeState.agentState();
            recordWorkflowSummary(finalState, invokeStartedAt);
            return finalState;
        });
    }

    public Optional<AgentState> resumeOptional(Map<String, Object> stateUpdate, RunnableConfig runnableConfig) {
        long invokeStartedAt = System.nanoTime();
        CompiledGraph<LangGraphRuntimeState> executable = getOrBuildCompiledGraph();
        Optional<LangGraphRuntimeState> output;
        try {
            output = executable.invoke(
                    stateUpdate == null ? GraphInput.resume() : GraphInput.resume(stateUpdate), runnableConfig);
        } catch (RuntimeException e) {
            throw propagateExecutionException(e, invokeStartedAt);
        }
        return output.map(runtimeState -> {
            AgentState finalState = runtimeState.agentState();
            recordWorkflowSummary(finalState, invokeStartedAt);
            return finalState;
        });
    }

    public Optional<StateSnapshot<LangGraphRuntimeState>> stateSnapshotOf(RunnableConfig runnableConfig) {
        if (runnableConfig == null) {
            return Optional.empty();
        }
        return getOrBuildCompiledGraph().stateOf(runnableConfig);
    }

    public AsyncGenerator<NodeOutput<LangGraphRuntimeState>> stream(AgentState state) {
        return stream(state, null);
    }

    public AsyncGenerator<NodeOutput<LangGraphRuntimeState>> stream(AgentState state, RunnableConfig runnableConfig) {
        validateInputState(state);
        initializeObservability(state);
        CompiledGraph<LangGraphRuntimeState> executable = getOrBuildCompiledGraph();
        return runnableConfig == null
                ? executable.stream(LangGraphRuntimeState.toInput(state))
                : executable.stream(LangGraphRuntimeState.toInput(state), runnableConfig);
    }

    public AsyncGenerator<NodeOutput<LangGraphRuntimeState>> resumeStream(
            Map<String, Object> stateUpdate, RunnableConfig runnableConfig) {
        CompiledGraph<LangGraphRuntimeState> executable = getOrBuildCompiledGraph();
        return executable.stream(
                stateUpdate == null ? GraphInput.resume() : GraphInput.resume(stateUpdate), runnableConfig);
    }

    /**
     * 预编译工作流图
     * <p>
     * 触发图的编译过程，用于验证工作流配置是否有效。
     * 如果图结构无效（如缺少入口点、节点未定义等），将抛出异常。
     * <p>
     * 此方法主要用于配置验证场景，实际执行时会自动触发编译。
     */
    public void precompile() {
        getOrBuildCompiledGraph();
    }

    private synchronized CompiledGraph<LangGraphRuntimeState> getOrBuildCompiledGraph() {
        if (!dirty && compiledGraph != null) {
            return compiledGraph;
        }
        try {
            StateGraph<LangGraphRuntimeState> graph = new StateGraph<>(
                    LangGraphRuntimeState.SCHEMA, new LC4jStateSerializer<>(LangGraphRuntimeState::new));

            Map<String, AgentNode> nodeSnapshot;
            synchronized (nodes) {
                nodeSnapshot = new LinkedHashMap<>(nodes);
            }

            Map<String, Map<String, Object>> nodePropertiesSnapshot;
            synchronized (nodeProperties) {
                nodePropertiesSnapshot = deepCopyNodeProperties(nodeProperties);
            }

            List<Edge> edgeSnapshot;
            synchronized (edges) {
                edgeSnapshot = new ArrayList<>(edges);
            }

            // 构建边索引：按 sourceId 分组，避免每次路由都遍历全部边
            Map<String, List<Edge>> edgeIndex = new HashMap<>();
            for (Edge edge : edgeSnapshot) {
                edgeIndex
                        .computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>())
                        .add(edge);
            }

            Map<String, String> routeMap = buildRouteMap(nodeSnapshot);

            for (Map.Entry<String, AgentNode> entry : nodeSnapshot.entrySet()) {
                String nodeId = entry.getKey();
                AgentNode node = entry.getValue();
                graph.addNode(nodeId, node_async(runtimeState -> {
                    AgentState previousState = runtimeState.agentState();
                    if (previousState == null || previousState.isFinished()) {
                        return Map.of();
                    }
                    AgentState beforeExecutionSnapshot = AgentUtils.deepCopyState(previousState);
                    enrichExecutionContext(previousState, nodeId, node, nodePropertiesSnapshot);
                    log.debug("AgentGraph -> Executing Node ID: {} (Type: {})", nodeId, node.getName());
                    long nodeStartedAt = System.nanoTime();
                    AgentNode.ExecutionResult executionResult = node.execute(previousState);

                    AgentState nextState = executionResult == null ? previousState : executionResult.state();
                    if (nextState == null) {
                        nextState = previousState;
                    }
                    String suggestedNext = executionResult == null ? null : executionResult.nextNode();
                    recordNodeExecution(
                            beforeExecutionSnapshot, nextState, nodeId, node.getName(), nodeStartedAt, suggestedNext);
                    return toChannelUpdates(beforeExecutionSnapshot, nextState, suggestedNext);
                }));

                graph.addConditionalEdges(
                        nodeId, edge_async(runtimeState -> routeNext(nodeId, runtimeState, edgeIndex)), routeMap);
            }

            graph.addEdge(StateGraph.START, startNodeId);
            compiledGraph = graph.compile(buildEffectiveCompileConfig());
            dirty = false;
            return compiledGraph;
        } catch (Exception e) {
            throw new IllegalStateException("构建 LangGraph4j 执行图失败", e);
        }
    }

    private void validateInputState(AgentState state) {
        if (startNodeId == null) {
            throw new IllegalStateException("Entry point not set");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    private Map<String, String> buildRouteMap(Map<String, AgentNode> nodeSnapshot) {
        Map<String, String> routeMap = new HashMap<>();
        for (String nodeId : nodeSnapshot.keySet()) {
            routeMap.put(nodeId, nodeId);
        }
        routeMap.put(END_NODE, StateGraph.END);
        return routeMap;
    }

    private String routeNext(
            String currentNodeId, LangGraphRuntimeState runtimeState, Map<String, List<Edge>> edgeIndex) {
        AgentState state = runtimeState.agentState();
        if (state == null || state.isFinished()) {
            recordRouteDecision(state, currentNodeId, END_NODE, "finished", null);
            return END_NODE;
        }
        String suggestedNext = runtimeState.nextNode();
        if (StringUtils.hasText(suggestedNext)) {
            if (END_NODE.equals(suggestedNext)) {
                recordRouteDecision(state, currentNodeId, END_NODE, "suggested_end", null);
                return END_NODE;
            }
            if (!nodes.containsKey(suggestedNext)) {
                log.error("在Graph上下文中找不到试图进入的 Node ID: '{}'", suggestedNext);
                recordRouteDecision(state, currentNodeId, END_NODE, "invalid_suggested", suggestedNext);
                return END_NODE;
            }
            recordRouteDecision(state, currentNodeId, suggestedNext, "suggested", null);
            return suggestedNext;
        }

        RouteDecision routeDecision = evaluateEdgesForNext(currentNodeId, state, edgeIndex);
        if (routeDecision == null || !StringUtils.hasText(routeDecision.targetId())) {
            // 使用索引直接判断是否有出边，O(1) 复杂度
            boolean hasOutgoingEdges = edgeIndex.containsKey(currentNodeId);
            if (hasOutgoingEdges) {
                AgentNode currentNode = nodes.get(currentNodeId);
                String nodeName = currentNode != null ? currentNode.getName() : currentNodeId;
                if (state.getAttributes() == null) {
                    state.setAttributes(new HashMap<>());
                }
                log.error("当前节点 '{}' 执行完毕后没有匹配到任何有效的出向边，图执行被迫停止。", nodeName);
                state.getAttributes()
                        .put("__routing_error__", "No matching outgoing edge found for node: " + currentNodeId);
                recordRouteDecision(state, currentNodeId, END_NODE, "routing_error", null);
                String message = "Node '" + nodeName + "' (ID: " + currentNodeId
                        + ") finished without matching any conditional edge, and no default edge was provided.";
                throw new AgentGraphExecutionException(message, state);
            }
            recordRouteDecision(state, currentNodeId, END_NODE, "no_match", null);
            return END_NODE;
        }
        recordRouteDecision(
                state, currentNodeId, routeDecision.targetId(), routeDecision.reason(), routeDecision.detail());
        return routeDecision.targetId();
    }

    private void markDirty() {
        dirty = true;
        compiledGraph = null;
    }

    private void enrichExecutionContext(
            AgentState state, String nodeId, AgentNode node, Map<String, Map<String, Object>> nodePropertiesSnapshot) {
        if (state.getAttributes() == null) {
            state.setAttributes(new HashMap<>());
        }
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, nodeId);
        state.getAttributes().put(CURRENT_NODE_TYPE_ATTRIBUTE, node.getName());
        state.getAttributes()
                .put(
                        CURRENT_NODE_PROPERTIES_ATTRIBUTE,
                        new LinkedHashMap<>(nodePropertiesSnapshot.getOrDefault(nodeId, Map.of())));
        state.getAttributes().put(GRAPH_NODE_PROPERTIES_ATTRIBUTE, deepCopyNodeProperties(nodePropertiesSnapshot));
        state.setAvailableNodes(new HashMap<>(nodes));
        state.setNodeExecutor(
                (targetNodeId, inputState) -> executeSingleNode(targetNodeId, inputState, nodePropertiesSnapshot));
    }

    private AgentState executeSingleNode(
            String nodeId, AgentState inputState, Map<String, Map<String, Object>> nodePropertiesSnapshot) {
        AgentNode targetNode = nodes.get(nodeId);
        if (targetNode == null) {
            log.warn("无法找到节点: {}", nodeId);
            return inputState;
        }
        AgentState branchState = AgentUtils.deepCopyState(inputState);
        enrichExecutionContext(branchState, nodeId, targetNode, nodePropertiesSnapshot);
        AgentNode.ExecutionResult result = targetNode.execute(branchState);
        return result != null && result.state() != null ? result.state() : branchState;
    }

    private Map<String, Map<String, Object>> deepCopyNodeProperties(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null ? Map.of() : new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private void initializeObservability(AgentState state) {
        if (state == null) {
            return;
        }
        if (state.getAttributes() == null) {
            state.setAttributes(new HashMap<>());
        }
        if (isTraceDisabled(state)) {
            return;
        }
        state.getAttributes().putIfAbsent(EXECUTION_TRACE_ATTRIBUTE, new ArrayList<Map<String, Object>>());
        Map<String, Object> stats = ensureExecutionStats(state);
        stats.putIfAbsent("startedAtEpochMs", System.currentTimeMillis());
        stats.putIfAbsent("nodeExecutions", 0);
        stats.putIfAbsent("routeDecisions", 0);
    }

    private void recordWorkflowSummary(AgentState state, long invokeStartedAt) {
        if (isTraceDisabled(state)) {
            return;
        }
        Map<String, Object> stats = ensureExecutionStats(state);
        stats.put("mode", "invoke");
        stats.put("totalDurationMs", toDurationMs(invokeStartedAt));
        stats.put("finished", state.isFinished());
        stats.put(
                "pendingToolCount",
                state.getPendingToolRequests() == null
                        ? 0
                        : state.getPendingToolRequests().size());
    }

    private void recordNodeExecution(
            AgentState previousState,
            AgentState nextState,
            String nodeId,
            String nodeType,
            long nodeStartedAt,
            String suggestedNext) {
        if (isTraceDisabled(nextState)) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("kind", "node");
        event.put("nodeId", nodeId);
        event.put("nodeType", nodeType);
        event.put("durationMs", toDurationMs(nodeStartedAt));
        event.put(
                "messageDelta",
                appendedMessages(previousState.getMessages(), nextState.getMessages())
                        .size());
        event.put(
                "pendingToolCount",
                nextState.getPendingToolRequests() == null
                        ? 0
                        : nextState.getPendingToolRequests().size());
        event.put("finished", nextState.isFinished());
        if (StringUtils.hasText(suggestedNext)) {
            event.put("suggestedNext", suggestedNext);
        }
        appendTraceEvent(nextState, event);
        Map<String, Object> stats = ensureExecutionStats(nextState);
        stats.put("nodeExecutions", ((Number) stats.getOrDefault("nodeExecutions", 0)).intValue() + 1);
        stats.put("lastNodeId", nodeId);
        stats.put("lastNodeDurationMs", event.get("durationMs"));
    }

    private void recordRouteDecision(
            AgentState state, String sourceNodeId, String targetNodeId, String reason, Object detail) {
        if (isTraceDisabled(state)) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("kind", "route");
        event.put("sourceNodeId", sourceNodeId);
        event.put("targetNodeId", targetNodeId);
        event.put("reason", reason);
        if (detail != null) {
            event.put("detail", detail);
        }
        appendTraceEvent(state, event);
        state.getAttributes().put(LAST_ROUTE_ATTRIBUTE, new LinkedHashMap<>(event));
        Map<String, Object> stats = ensureExecutionStats(state);
        stats.put("routeDecisions", ((Number) stats.getOrDefault("routeDecisions", 0)).intValue() + 1);
        stats.put("lastRouteTarget", targetNodeId);
    }

    @SuppressWarnings("unchecked")
    private void appendTraceEvent(AgentState state, Map<String, Object> event) {
        List<Map<String, Object>> trace = (List<Map<String, Object>>) state.getAttributes()
                .computeIfAbsent(EXECUTION_TRACE_ATTRIBUTE, key -> new ArrayList<Map<String, Object>>());
        if (trace.size() >= MAX_TRACE_ENTRIES) {
            trace.removeFirst();
        }
        trace.add(event);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureExecutionStats(AgentState state) {
        return (Map<String, Object>) state.getAttributes()
                .computeIfAbsent(EXECUTION_STATS_ATTRIBUTE, key -> new LinkedHashMap<String, Object>());
    }

    private boolean isTraceDisabled(AgentState state) {
        if (state == null || state.getAttributes() == null) {
            return true;
        }
        Object enabled = state.getAttributes().get(TRACE_ENABLED_ATTRIBUTE);
        if (enabled instanceof Boolean booleanValue) {
            return !booleanValue;
        }
        if (enabled instanceof String text) {
            return !Boolean.parseBoolean(text);
        }
        return false;
    }

    private long toDurationMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private RuntimeException propagateExecutionException(RuntimeException exception, long invokeStartedAt) {
        AgentGraphExecutionException graphException = findExecutionException(exception);
        if (graphException == null) {
            return exception;
        }
        AgentState failedState = graphException.getState();
        if (failedState != null) {
            recordWorkflowFailure(failedState, invokeStartedAt, graphException.getMessage());
        }
        return graphException;
    }

    private AgentGraphExecutionException findExecutionException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AgentGraphExecutionException executionException) {
                return executionException;
            }
            current = current.getCause();
        }
        return null;
    }

    private void recordWorkflowFailure(AgentState state, long invokeStartedAt, String errorMessage) {
        if (isTraceDisabled(state)) {
            return;
        }
        Map<String, Object> stats = ensureExecutionStats(state);
        stats.put("mode", "invoke");
        stats.put("totalDurationMs", toDurationMs(invokeStartedAt));
        stats.put("finished", state.isFinished());
        stats.put("failed", true);
        if (StringUtils.hasText(errorMessage)) {
            stats.put("errorMessage", errorMessage);
        }
    }

    private Map<String, Object> toChannelUpdates(AgentState previousState, AgentState nextState, String suggestedNext) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(LangGraphRuntimeState.SESSION_ID_KEY, nextState.getSessionId());
        updates.put(LangGraphRuntimeState.KB_ID_KEY, nextState.getKbId());
        updates.put(LangGraphRuntimeState.LLM_MODEL_ID_KEY, nextState.getLlmModelId());
        updates.put(
                LangGraphRuntimeState.PENDING_TOOL_REQUESTS_KEY,
                nextState.getPendingToolRequests() == null
                        ? List.of()
                        : new ArrayList<>(nextState.getPendingToolRequests()));
        updates.put(LangGraphRuntimeState.ATTRIBUTES_KEY, sanitizeAttributes(nextState.getAttributes()));
        updates.put(LangGraphRuntimeState.FINISHED_KEY, nextState.isFinished());
        updates.put(
                LangGraphRuntimeState.MESSAGES_KEY,
                appendedMessages(previousState.getMessages(), nextState.getMessages()));
        updates.put(LangGraphRuntimeState.NEXT_NODE_KEY, StringUtils.hasText(suggestedNext) ? suggestedNext : "");
        return updates;
    }

    private List<?> appendedMessages(List<?> previousMessages, List<?> nextMessages) {
        if (nextMessages == null || nextMessages.isEmpty()) {
            return List.of();
        }
        int previousSize = previousMessages == null ? 0 : previousMessages.size();
        if (nextMessages.size() <= previousSize) {
            return List.of();
        }
        return new ArrayList<>(nextMessages.subList(previousSize, nextMessages.size()));
    }

    /**
     * 评估当前节点的出边，决定下一个节点
     * <p>
     * 使用边索引直接查找当前节点的出边，O(1) 复杂度。
     *
     * @param currentNodeId 当前节点ID
     * @param state 当前状态
     * @param edgeIndex 边索引（按 sourceId 分组）
     * @return 路由决策，如果没有匹配的边则返回 null
     */
    private RouteDecision evaluateEdgesForNext(
            String currentNodeId, AgentState state, Map<String, List<Edge>> edgeIndex) {
        // 直接从索引获取当前节点的出边，O(1) 复杂度
        List<Edge> outgoingEdges = edgeIndex.get(currentNodeId);
        if (outgoingEdges == null || outgoingEdges.isEmpty()) {
            return null;
        }

        Edge firstDirectEdge = null;
        for (Edge edge : outgoingEdges) {
            if (edge.getConditionEvaluator() != null && StringUtils.hasText(edge.getConditionExpression())) {
                boolean isPass = edge.getConditionEvaluator().evaluate(edge.getConditionExpression(), state);
                if (isPass) {
                    return new RouteDecision(
                            edge.getTargetId(),
                            "conditional",
                            edge.getConditionEvaluator().getType() + ":" + edge.getConditionExpression());
                }
            } else {
                // 无条件边作为兜底，等待后续条件边全部评估后再决定。
                if (firstDirectEdge == null) {
                    firstDirectEdge = edge;
                }
            }
        }
        if (firstDirectEdge != null) {
            return new RouteDecision(firstDirectEdge.getTargetId(), "direct", null);
        }
        return null;
    }

    private CompileConfig buildEffectiveCompileConfig() {
        CompileConfig baseConfig =
                compileConfig == null ? CompileConfig.builder().build() : compileConfig;
        return CompileConfig.builder(baseConfig).recursionLimit(maxIterations).build();
    }

    private record RouteDecision(String targetId, String reason, Object detail) {}

    private static final Set<String> NON_SERIALIZABLE_ATTRIBUTE_KEYS =
            Set.of(AgentState.NODE_EXECUTOR_ATTRIBUTE, AVAILABLE_NODES_ATTRIBUTE, "streamHandler");

    private static Map<String, Object> sanitizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        attributes.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (NON_SERIALIZABLE_ATTRIBUTE_KEYS.contains(key)) {
                return;
            }
            Object sanitizedValue = sanitizeSerializableValue(value);
            if (sanitizedValue != SKIP_VALUE) {
                sanitized.put(key, sanitizedValue);
            }
        });
        return sanitized;
    }

    private static final Object SKIP_VALUE = new Object();

    private static Object sanitizeSerializableValue(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case java.io.Serializable ignored -> {
                return value;
            }
            case Map<?, ?> map -> {
                Map<String, Object> sanitizedMap = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    Object nested = sanitizeSerializableValue(v);
                    if (nested != SKIP_VALUE) {
                        sanitizedMap.put(String.valueOf(k), nested);
                    }
                });
                return sanitizedMap;
            }
            case List<?> list -> {
                List<Object> sanitizedList = new ArrayList<>();
                for (Object item : list) {
                    Object nested = sanitizeSerializableValue(item);
                    if (nested != SKIP_VALUE) {
                        sanitizedList.add(nested);
                    }
                }
                return sanitizedList;
            }
            default -> {}
        }
        return SKIP_VALUE;
    }
}
