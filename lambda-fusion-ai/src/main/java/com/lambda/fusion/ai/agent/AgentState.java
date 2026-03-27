package com.lambda.fusion.ai.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Data;

/**
 * 智能体执行图的状态对象 (等同于 LangGraph 的 State)
 * 存储在图的每次流转中各个节点共享的上下文信息
 *
 * 线程安全设计：
 * - messages 和 pendingToolRequests 使用 CopyOnWriteArrayList 保证线程安全
 * - attributes 使用 ConcurrentHashMap 保证线程安全
 */
@Data
public class AgentState {
    /**
     * 对话消息流转历史记录 - 使用 CopyOnWriteArrayList 保证线程安全
     */
    private List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    /**
     * 会话属性
     */
    private Long sessionId;

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 引用的LLM模型ID
     */
    private Long llmModelId;

    /**
     * 当前待执行的工具请求信息(由 LLMNode 产生，提供给 ToolNode) - 使用 CopyOnWriteArrayList 保证线程安全
     */
    private List<ToolExecutionRequest> pendingToolRequests = new CopyOnWriteArrayList<>();

    /**
     * 自定义扩展属性（例如用于临时存放意图识别标识等）
     */
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

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
}
