package com.lambda.fusion.ai.commons.agent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
                        log.warn("配置的工具 '{}' 不存在，已跳过", toolName);
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
                        log.warn("配置的工具 '{}' 不存在，已跳过", toolName);
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
}
