package com.lambda.fusion.ai.commons.utils;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 节点工具类
 * <p>
 * 提供节点处理过程中的公共工具方法
 */
@Slf4j
@UtilityClass
public class AgentNodeUtils {

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
}
