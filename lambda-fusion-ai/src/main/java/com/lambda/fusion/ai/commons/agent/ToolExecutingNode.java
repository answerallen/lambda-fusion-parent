package com.lambda.fusion.ai.commons.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图节点：专门处理上级流转过来的需要调用的业务实现方法，拦截本地框架组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutingNode implements AgentNode {

    public static final String NAME = "TOOL_EXECUTOR";

    private final AgentToolProvider toolProvider;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState nextState) {
        log.info("ToolExecutingNode: 开始执行本地动作回调...");
        List<ToolExecutionRequest> requests = nextState.getPendingToolRequests();
        Map<String, Object> nodeProperties = nextState.getCurrentNodeProperties();
        if (Boolean.FALSE.equals(resolveBoolean(nodeProperties, "enabled", "toolExecutionEnabled"))) {
            log.info("ToolExecutingNode: 节点 {} 已禁用工具执行", nextState.getCurrentNodeId());
            nextState.getPendingToolRequests().clear();
            return new ExecutionResult(nextState, LlmProcessingNode.NAME);
        }
        Set<String> allowedTools = resolveToolNames(nodeProperties, "allowedTools", "toolNames", "tools");

        if (requests == null || requests.isEmpty()) {
            return new ExecutionResult(nextState, LlmProcessingNode.NAME);
        }

        for (ToolExecutionRequest request : requests) {
            if (!allowedTools.isEmpty() && !allowedTools.contains(request.name())) {
                String deniedResult = "Tool '" + request.name() + "' is not allowed in current node.";
                log.warn("ToolExecutingNode: 节点 {} 拒绝执行工具 {}", nextState.getCurrentNodeId(), request.name());
                nextState.addMessage(ToolExecutionResultMessage.from(request, deniedResult));
                continue;
            }
            log.info("执行目标 Tool ->: {} | Arguments: {}", request.name(), request.arguments());
            String result = toolProvider.executeTool(request);
            log.debug("执行结果：{}", result);

            ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(request, result);
            nextState.addMessage(resultMessage);
        }

        nextState.getPendingToolRequests().clear();

        return new ExecutionResult(nextState, null);
    }

    private Boolean resolveBoolean(Map<String, Object> nodeProperties, String... keys) {
        Object value = firstNonNull(nodeProperties, keys);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private Set<String> resolveToolNames(Map<String, Object> nodeProperties, String... keys) {
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
                    }
                }
            }
        } else {
            for (String item : value.toString().split(",")) {
                if (!item.isBlank()) {
                    String toolName = item.trim();
                    if (toolProvider.hasTool(toolName)) {
                        toolNames.add(toolName);
                    }
                }
            }
        }
        return toolNames;
    }

    private Object firstNonNull(Map<String, Object> nodeProperties, String... keys) {
        for (String key : keys) {
            Object value = nodeProperties.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
