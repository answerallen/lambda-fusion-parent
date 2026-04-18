package com.lambda.fusion.ai.commons.utils;

import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_ID_ATTRIBUTE;
import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import dev.langchain4j.data.message.UserMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Agent 节点工具类
 * <p>
 * 提供节点处理过程中的公共工具方法
 */
@Slf4j
@UtilityClass
public class AgentUtils {

    public <T> T get(Supplier<T> supplier) throws Throwable {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception) {
                throw e.getCause();
            }
            throw e;
        }
    }

    public Integer resolveInteger(Map<String, Object> nodeProperties, String... keys) {
        Object value = AgentUtils.firstNonNull(nodeProperties, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                log.warn("ReactAgentNode: 无法解析整数配置值 '{}'", text);
            }
        }
        return null;
    }

    /**
     * 从节点配置中解析工具名称列表
     * <p>
     * 支持两种配置格式：
     * <ul>
     *   <li>Iterable 集合：如 List&lt;String&gt;</li>
     *   <li>逗号分隔字符串：如 "tool1,tool2,tool3"</li>
     * </ul>
     * <p>
     * 会自动过滤掉不存在的工具，并输出警告日志
     *
     * @param nodeProperties 节点配置属性
     * @param toolProvider   工具提供者，用于验证工具是否存在
     * @param keys           配置键名（支持多个备选键名）
     * @return 解析后的工具名称集合，如果配置为空则返回空集合
     */
    public Set<String> resolveToolNames(
            Map<String, Object> nodeProperties, AgentToolProvider toolProvider, String... keys) {
        Object value = firstNonNull(nodeProperties, keys);
        if (value == null) {
            return Set.of();
        }
        Set<String> toolNames = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !item.toString().isBlank()) {
                    String toolName = item.toString().trim();
                    if (toolProvider.hasTool(toolName)) {
                        toolNames.add(toolName);
                    } else {
                        log.warn("Iterable 配置的工具 '{}' 不存在，已跳过", toolName);
                    }
                }
            }
        } else {
            for (String item : value.toString().split(",")) {
                if (!item.isBlank()) {
                    String toolName = item.trim();
                    if (toolProvider.hasTool(toolName)) {
                        toolNames.add(toolName);
                    } else {
                        log.warn("String 配置的工具 '{}' 不存在，已跳过", toolName);
                    }
                }
            }
        }
        return toolNames;
    }

    /**
     * 从配置中获取第一个非空值
     *
     * @param nodeProperties 节点配置属性
     * @param keys           配置键名（按优先级顺序）
     * @return 第一个非空值，如果都为空则返回 null
     */
    public Object firstNonNull(Map<String, Object> nodeProperties, String... keys) {
        for (String key : keys) {
            Object value = nodeProperties.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将对象安全转换为 int。
     *
     * <p>支持 {@link Number} 和数字字符串，无法转换时返回 0。</p>
     *
     * @param value 原始值
     * @return 转换后的 int 值，无法转换时为 0
     */
    public int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 从配置中解析布尔值。
     *
     * @param nodeProperties 节点配置属性
     * @param keys           配置键名（按优先级顺序）
     * @return 解析后的布尔值，无法解析时返回 null
     */
    public Boolean resolveBoolean(Map<String, Object> nodeProperties, String... keys) {
        if (nodeProperties == null) {
            return null;
        }
        Object value = firstNonNull(nodeProperties, keys);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return null;
    }

    /**
     * 从节点配置中解析模型 ID，不存在时回退到状态中的模型 ID。
     *
     * @param state          当前状态
     * @param nodeProperties 节点配置
     * @return 解析后的模型 ID
     */
    public String resolveModelId(AgentState state, Map<String, Object> nodeProperties) {
        Object configuredModelId = firstNonNull(nodeProperties, "llmModelId", "modelId");
        if (configuredModelId instanceof Number number) {
            return number.toString();
        }
        if (configuredModelId instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return state == null ? null : state.getLlmModelId();
    }

    /**
     * 从节点配置中解析模板 ID。
     *
     * @param nodeProperties 节点配置
     * @return 模板 ID，不存在时返回 null
     */
    public String resolveTemplateId(Map<String, Object> nodeProperties) {
        Object configuredValue = firstNonNull(nodeProperties, "promptTemplateId", "systemPromptTemplateId");
        if (configuredValue instanceof Number number) {
            return number.toString();
        }
        if (configuredValue instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return null;
    }

    /**
     * 将对象安全转换为去除首尾空白的字符串。
     *
     * @param value 原始值
     * @return 去空白后的字符串；为空时返回 null
     */
    public String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 从节点配置中解析文本值。
     *
     * @param nodeProperties 节点配置
     * @param keys           配置键名（按优先级顺序）
     * @return 解析后的文本值；不存在时返回 null
     */
    public String resolveText(Map<String, Object> nodeProperties, String... keys) {
        return asText(firstNonNull(nodeProperties, keys));
    }

    /**
     * 构建模板渲染使用的基础变量集合。
     *
     * @param state 当前状态
     * @return 基础模板变量
     */
    public Map<String, Object> buildBaseTemplateVariables(AgentState state) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sessionId", state.getSessionId());
        variables.put("kbId", state.getKbId());
        variables.put("llmModelId", state.getLlmModelId());
        variables.put("currentNodeId", state.getCurrentNodeId());
        variables.put("currentNodeType", state.getCurrentNodeType());
        variables.put("currentNodeProperties", state.getCurrentNodeProperties());
        variables.put("graphNodeProperties", state.getGraphNodeProperties());
        variables.put("attributes", state.getAttributes());
        if (state.getAttributes() != null) {
            variables.putAll(state.getAttributes());
        }
        if (state.getMessages() != null && !state.getMessages().isEmpty()) {
            variables.put("messageCount", state.getMessages().size());
            variables.put("lastMessage", state.getMessages().getLast());
        }
        return variables;
    }

    /**
     * 将节点配置中的自定义模板变量合并到变量集合中。
     *
     * @param variables      目标变量集合
     * @param nodeProperties 节点配置
     */
    public void mergeTemplateVariables(Map<String, Object> variables, Map<String, Object> nodeProperties) {
        Object templateVariables = firstNonNull(nodeProperties, "templateVariables", "promptVariables");
        if (templateVariables instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    variables.put(String.valueOf(key), value);
                }
            });
        }
    }

    /**
     * 解析节点的 system prompt。
     *
     * <p>优先级：
     * 1. `systemPrompt/systemMessage`
     * 2. `promptTemplateId/systemPromptTemplateId` 渲染结果
     * 3. 未命中时返回 null</p>
     *
     * @param nodeProperties            节点配置
     * @param promptTemplateRenderer    模板渲染函数
     * @param templateVariablesSupplier 模板变量提供者
     * @return 解析后的 prompt；不存在时返回 null
     */
    public String resolveSystemPrompt(
            Map<String, Object> nodeProperties,
            java.util.function.BiFunction<String, Map<String, Object>, String> promptTemplateRenderer,
            Supplier<Map<String, Object>> templateVariablesSupplier) {
        String systemPrompt = resolveText(nodeProperties, "systemPrompt", "systemMessage");
        if (StringUtils.hasText(systemPrompt)) {
            return systemPrompt;
        }
        String templateId = resolveTemplateId(nodeProperties);
        if (templateId == null) {
            return null;
        }
        return promptTemplateRenderer.apply(templateId, templateVariablesSupplier.get());
    }

    /**
     * 解析节点的 system prompt，并在未命中时回退到默认值。
     *
     * @param nodeProperties            节点配置
     * @param promptTemplateRenderer    模板渲染函数
     * @param templateVariablesSupplier 模板变量提供者
     * @param defaultPrompt             默认 prompt
     * @return 解析后的 prompt
     */
    public String resolveSystemPrompt(
            Map<String, Object> nodeProperties,
            java.util.function.BiFunction<String, Map<String, Object>, String> promptTemplateRenderer,
            Supplier<Map<String, Object>> templateVariablesSupplier,
            String defaultPrompt) {
        String resolvedPrompt = resolveSystemPrompt(nodeProperties, promptTemplateRenderer, templateVariablesSupplier);
        return StringUtils.hasText(resolvedPrompt) ? resolvedPrompt : defaultPrompt;
    }

    /**
     * 深拷贝 AgentState 对象
     * <p>
     * 创建一个完全独立的 AgentState 副本，包括：
     * <ul>
     *   <li>基本属性（sessionId, kbId, llmModelId, finished）</li>
     *   <li>消息列表（CopyOnWriteArrayList）</li>
     *   <li>待处理工具请求列表（CopyOnWriteArrayList）</li>
     *   <li>属性映射（ConcurrentHashMap，递归深拷贝）</li>
     *   <li>可用节点映射</li>
     *   <li>节点执行器引用</li>
     * </ul>
     *
     * @param original 原始状态对象
     * @return 深拷贝后的状态对象
     */
    public AgentState deepCopyState(AgentState original) {
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

        Map<String, AgentNode> availableNodes = original.getAvailableNodes();
        if (availableNodes != null && !availableNodes.isEmpty()) {
            copy.setAvailableNodes(new ConcurrentHashMap<>(availableNodes));
        }

        if (original.getAttributes() != null) {
            Map<String, Object> copiedAttributes = new ConcurrentHashMap<>();
            original.getAttributes().forEach((key, value) -> {
                if (AgentState.NODE_EXECUTOR_ATTRIBUTE.equals(key)) {
                    return;
                }
                if (com.lambda.fusion.ai.commons.agent.AgentGraph.AVAILABLE_NODES_ATTRIBUTE.equals(key)) {
                    return;
                }
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

    /**
     * 深拷贝 Map 对象
     * <p>
     * 递归拷贝 Map 中的所有值，支持嵌套的 Map 和 List 结构。
     * 使用 ConcurrentHashMap 和 CopyOnWriteArrayList 保证线程安全。
     *
     * @param source 原始 Map
     * @return 深拷贝后的 Map
     */
    public Map<String, Object> deepCopyMap(Map<?, ?> source) {
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

    /**
     * 确保状态对象的 attributes 已初始化。
     *
     * @param state 目标状态
     * @return 可安全写入的 attributes
     */
    public Map<String, Object> ensureAttributes(AgentState state) {
        if (state.getAttributes() == null) {
            state.setAttributes(new ConcurrentHashMap<>());
        }
        return state.getAttributes();
    }

    /**
     * 设置当前节点属性。
     *
     * @param state      目标状态
     * @param properties 当前节点属性
     */
    public void setCurrentNodeProperties(AgentState state, Map<String, Object> properties) {
        Map<String, Object> attributes = ensureAttributes(state);
        if (properties == null) {
            attributes.remove(CURRENT_NODE_PROPERTIES_ATTRIBUTE);
            return;
        }
        attributes.put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);
    }

    /**
     * 设置当前节点 ID。
     *
     * @param state  目标状态
     * @param nodeId 节点 ID
     */
    public void setCurrentNodeId(AgentState state, String nodeId) {
        Map<String, Object> attributes = ensureAttributes(state);
        if (nodeId == null || nodeId.isBlank()) {
            attributes.remove(CURRENT_NODE_ID_ATTRIBUTE);
            return;
        }
        attributes.put(CURRENT_NODE_ID_ATTRIBUTE, nodeId);
    }

    /**
     * 同时设置当前节点 ID 和属性。
     *
     * @param state      目标状态
     * @param nodeId     节点 ID
     * @param properties 当前节点属性
     */
    public void setCurrentNodeContext(AgentState state, String nodeId, Map<String, Object> properties) {
        setCurrentNodeId(state, nodeId);
        setCurrentNodeProperties(state, properties);
    }

    /**
     * 创建带当前节点上下文的 AgentState。
     *
     * @param nodeId     节点 ID
     * @param properties 当前节点属性
     * @return 初始化后的状态
     */
    public AgentState newStateWithCurrentNode(String nodeId, Map<String, Object> properties) {
        AgentState state = new AgentState();
        setCurrentNodeContext(state, nodeId, properties);
        return state;
    }

    /**
     * 创建带当前节点上下文和用户消息的 AgentState。
     *
     * @param nodeId      节点 ID
     * @param properties  当前节点属性
     * @param userMessage 初始用户消息
     * @return 初始化后的状态
     */
    public AgentState newStateWithCurrentNode(String nodeId, Map<String, Object> properties, String userMessage) {
        AgentState state = newStateWithCurrentNode(nodeId, properties);
        if (userMessage != null && !userMessage.isBlank()) {
            state.addMessage(UserMessage.from(userMessage));
        }
        return state;
    }
}
