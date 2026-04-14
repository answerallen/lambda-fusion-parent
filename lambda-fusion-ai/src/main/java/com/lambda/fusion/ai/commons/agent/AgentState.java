package com.lambda.fusion.ai.commons.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import lombok.Data;

/**
 * 智能体执行图的状态对象 (等同于 LangGraph 的 State)
 * 存储在图的每次流转中各个节点共享的上下文信息
 * <p>
 * 线程安全设计：
 * - messages 和 pendingToolRequests 使用 CopyOnWriteArrayList 保证线程安全
 * - attributes 使用 ConcurrentHashMap 保证线程安全
 */
@Data
public class AgentState {

    public static final String NODE_EXECUTOR_ATTRIBUTE = "_nodeExecutor";

    /**
     * 对话消息流转历史记录 - 使用 CopyOnWriteArrayList 保证线程安全
     */
    private List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    /**
     * 会话属性
     */
    private String sessionId;

    /**
     * 知识库ID
     */
    private String kbId;

    /**
     * 引用的LLM模型ID
     */
    private String llmModelId;

    /**
     * 当前待执行的工具请求信息(由 LLMNode 产生，提供给 ToolNode) - 使用 CopyOnWriteArrayList 保证线程安全
     */
    private List<ToolExecutionRequest> pendingToolRequests = new CopyOnWriteArrayList<>();

    /**
     * 自定义扩展属性（例如用于临时存放意图识别标识等）
     */
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    private transient BiFunction<String, AgentState, AgentState> nodeExecutor;

    private transient Map<String, AgentNode> availableNodes;

    /**
     * 内部标识图是否应该终止
     */
    private volatile boolean finished = false;

    public void addMessage(ChatMessage message) {
        if (this.messages == null) {
            this.messages = new CopyOnWriteArrayList<>();
        }
        this.messages.add(message);
    }

    public void addMessages(List<ChatMessage> newMessages) {
        if (this.messages == null) {
            this.messages = new CopyOnWriteArrayList<>();
        }
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
    }

    public String getCurrentNodeId() {
        Object currentNodeId = this.attributes.get(AgentGraph.CURRENT_NODE_ID_ATTRIBUTE);
        return currentNodeId == null ? null : currentNodeId.toString();
    }

    public String getCurrentNodeType() {
        Object currentNodeType = this.attributes.get(AgentGraph.CURRENT_NODE_TYPE_ATTRIBUTE);
        return currentNodeType == null ? null : currentNodeType.toString();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCurrentNodeProperties() {
        Object properties = this.attributes.get(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE);
        if (properties instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getGraphNodeProperties() {
        Object properties = this.attributes.get(AgentGraph.GRAPH_NODE_PROPERTIES_ATTRIBUTE);
        if (properties instanceof Map<?, ?> map) {
            Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
            ((Map<String, Object>) map)
                    .forEach((key, value) -> copy.put(
                            key,
                            value instanceof Map<?, ?> valueMap
                                    ? new LinkedHashMap<>((Map<String, Object>) valueMap)
                                    : Map.of()));
            return copy;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getExecutionTrace() {
        Object trace = this.attributes.get(AgentGraph.EXECUTION_TRACE_ATTRIBUTE);
        if (trace instanceof List<?> list) {
            List<Map<String, Object>> copy = new CopyOnWriteArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    copy.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return copy;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionStats() {
        Object stats = this.attributes.get(AgentGraph.EXECUTION_STATS_ATTRIBUTE);
        if (stats instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public BiFunction<String, AgentState, AgentState> getNodeExecutor() {
        if (nodeExecutor != null) {
            return nodeExecutor;
        }
        if (this.attributes == null) {
            return null;
        }
        Object executor = this.attributes.get(NODE_EXECUTOR_ATTRIBUTE);
        if (executor instanceof BiFunction) {
            return (BiFunction<String, AgentState, AgentState>) executor;
        }
        return null;
    }

    public void setNodeExecutor(BiFunction<String, AgentState, AgentState> executor) {
        this.nodeExecutor = executor;
        if (this.attributes != null) {
            this.attributes.remove(NODE_EXECUTOR_ATTRIBUTE);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, AgentNode> getAvailableNodes() {
        if (availableNodes != null) {
            return availableNodes;
        }
        if (this.attributes == null) {
            return Map.of();
        }
        Object nodes = this.attributes.get(AgentGraph.AVAILABLE_NODES_ATTRIBUTE);
        if (nodes instanceof Map<?, ?> map) {
            return (Map<String, AgentNode>) map;
        }
        return Map.of();
    }

    public void setAvailableNodes(Map<String, AgentNode> nodes) {
        this.availableNodes = nodes;
        if (this.attributes != null) {
            this.attributes.remove(AgentGraph.AVAILABLE_NODES_ATTRIBUTE);
        }
    }
}
