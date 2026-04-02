package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 子图节点。
 * 允许在父图中嵌套执行另一个完整的子工作流。
 * <p>
 * 配置属性：
 * - subgraphId: 子图定义ID（从工作流存储中加载）
 * - subgraphDefinition: 子图定义JSON（内联定义）
 * - inputMapping: 输入参数映射（将父状态映射到子图输入）
 * - outputMapping: 输出结果映射（将子图输出映射回父状态）
 * - propagateErrors: 是否将子图错误传播到父图（默认 true）
 * - inheritContext: 是否继承父图上下文（默认 false）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubgraphNode implements AgentNode {

    private static final String SUBGRAPH_CONTEXT_KEY = "__subgraph_context__";

    private final AgentGraphFactory graphFactory;

    @Override
    public String getName() {
        return "SUBGRAPH";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("子图节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        // 获取子图上下文
        SubgraphContext context = getOrCreateSubgraphContext(state);

        // 如果子图正在执行中，检查是否完成
        if (context.isExecuting) {
            return checkSubgraphCompletion(state, context, properties);
        }

        // 首次进入，启动子图执行
        return startSubgraphExecution(state, context, properties);
    }

    /**
     * 启动子图执行
     */
    private ExecutionResult startSubgraphExecution(
            AgentState state, SubgraphContext context, Map<String, Object> properties) {
        String subgraphDefinition = (String) properties.get("subgraphDefinition");
        String subgraphId = (String) properties.get("subgraphId");

        if (subgraphDefinition == null && subgraphId == null) {
            log.warn("子图节点缺少子图定义（subgraphDefinition 或 subgraphId）");
            return new ExecutionResult(state, null);
        }

        boolean inheritContext = (boolean) properties.getOrDefault("inheritContext", false);

        try {
            // 构建子图
            AgentGraph subgraph;
            if (subgraphDefinition != null) {
                subgraph = graphFactory.buildFromDefinition(subgraphDefinition);
            } else {
                // TODO: 从工作流存储中加载子图定义
                log.warn("从存储加载子图功能尚未实现: {}", subgraphId);
                return new ExecutionResult(state, null);
            }

            // 准备子图输入状态
            AgentState subgraphInputState = prepareSubgraphInput(state, properties, inheritContext);

            // 执行子图
            context.isExecuting = true;
            context.startTime = System.currentTimeMillis();
            saveSubgraphContext(state, context);

            // 同步执行子图（简化实现）
            AgentState subgraphOutputState = subgraph.invoke(subgraphInputState);

            // 处理子图输出
            return handleSubgraphOutput(state, context, subgraphOutputState, properties);

        } catch (Exception e) {
            log.error("子图执行异常", e);
            boolean propagateErrors = (boolean) properties.getOrDefault("propagateErrors", true);

            if (propagateErrors) {
                // 将错误信息添加到状态中
                if (state.getAttributes() != null) {
                    state.getAttributes().put("__subgraph_error__", e.getMessage());
                }
            }

            clearSubgraphContext(state);
            return new ExecutionResult(state, null);
        }
    }

    /**
     * 检查子图执行完成情况
     */
    private ExecutionResult checkSubgraphCompletion(
            AgentState state, SubgraphContext context, Map<String, Object> properties) {
        // 简化实现：子图同步执行，直接返回
        // 实际项目中可以实现异步子图执行
        clearSubgraphContext(state);
        return new ExecutionResult(state, null);
    }

    /**
     * 准备子图输入状态
     */
    @SuppressWarnings("unchecked")
    private AgentState prepareSubgraphInput(
            AgentState parentState, Map<String, Object> properties, boolean inheritContext) {
        AgentState subgraphState = new AgentState();

        // 继承基础属性
        subgraphState.setSessionId(parentState.getSessionId());
        subgraphState.setKbId(parentState.getKbId());
        subgraphState.setLlmModelId(parentState.getLlmModelId());

        // 处理输入映射
        Object inputMappingObj = properties.get("inputMapping");
        if (inputMappingObj instanceof Map<?, ?> inputMapping) {
            Map<String, Object> attributes = new java.util.HashMap<>();

            inputMapping.forEach((key, value) -> {
                String targetKey = (String) key;
                String sourcePath = (String) value;

                // 从父状态中提取值
                Object sourceValue = extractValueFromState(parentState, sourcePath);
                attributes.put(targetKey, sourceValue);
            });

            subgraphState.setAttributes(attributes);
        }

        // 如果继承上下文，复制所有属性
        if (inheritContext && parentState.getAttributes() != null) {
            if (subgraphState.getAttributes() == null) {
                subgraphState.setAttributes(new java.util.HashMap<>());
            }
            subgraphState.getAttributes().putAll(parentState.getAttributes());
        }

        return subgraphState;
    }

    /**
     * 处理子图输出
     */
    @SuppressWarnings("unchecked")
    private ExecutionResult handleSubgraphOutput(
            AgentState parentState,
            SubgraphContext context,
            AgentState subgraphOutputState,
            Map<String, Object> properties) {
        // 处理输出映射
        Object outputMappingObj = properties.get("outputMapping");
        if (outputMappingObj instanceof Map<?, ?> outputMapping) {
            if (parentState.getAttributes() == null) {
                parentState.setAttributes(new java.util.HashMap<>());
            }

            outputMapping.forEach((key, value) -> {
                String targetPath = (String) key;
                String sourceKey = (String) value;

                // 从子图输出中提取值
                Object sourceValue = null;
                if (subgraphOutputState.getAttributes() != null) {
                    sourceValue = subgraphOutputState.getAttributes().get(sourceKey);
                }

                // 设置到父状态
                setValueToState(parentState, targetPath, sourceValue);
            });
        }

        // 记录执行统计
        long duration = System.currentTimeMillis() - context.startTime;
        log.debug("子图执行完成，耗时 {}ms", duration);

        clearSubgraphContext(subgraphOutputState);
        return new ExecutionResult(parentState, null);
    }

    /**
     * 从状态中提取值
     */
    private Object extractValueFromState(AgentState state, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        // 支持简单的路径访问，如 "attributes.userName" 或 "messages"
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
                return state.getAttributes().get(parts[1]);
            }
        }

        return null;
    }

    /**
     * 设置值到状态
     */
    @SuppressWarnings("unchecked")
    private void setValueToState(AgentState state, String path, Object value) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("\\.");

        if (parts.length == 1) {
            if (state.getAttributes() == null) {
                state.setAttributes(new java.util.HashMap<>());
            }
            state.getAttributes().put(parts[0], value);
        } else if (parts.length >= 2 && "attributes".equals(parts[0])) {
            if (state.getAttributes() == null) {
                state.setAttributes(new java.util.HashMap<>());
            }
            state.getAttributes().put(parts[1], value);
        }
    }

    /**
     * 获取或创建子图上下文
     */
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

    /**
     * 保存子图上下文
     */
    private void saveSubgraphContext(AgentState state, SubgraphContext context) {
        if (state.getAttributes() != null) {
            state.getAttributes().put(SUBGRAPH_CONTEXT_KEY, context.toMap());
        }
    }

    /**
     * 清除子图上下文
     */
    private void clearSubgraphContext(AgentState state) {
        if (state.getAttributes() != null) {
            state.getAttributes().remove(SUBGRAPH_CONTEXT_KEY);
        }
    }

    /**
     * 子图上下文
     */
    private static class SubgraphContext {
        boolean isExecuting = false;
        long startTime = 0;

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("isExecuting", isExecuting);
            map.put("startTime", startTime);
            return map;
        }

        static SubgraphContext fromMap(Map<String, Object> map) {
            SubgraphContext context = new SubgraphContext();
            context.isExecuting = (boolean) map.getOrDefault("isExecuting", false);
            context.startTime = ((Number) map.getOrDefault("startTime", 0L)).longValue();
            return context;
        }
    }
}
