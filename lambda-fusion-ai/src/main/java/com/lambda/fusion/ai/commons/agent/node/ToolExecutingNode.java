package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.LinkedHashMap;
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
    private static final String TOOL_RESULTS_KEY = "toolResults";
    private static final String LAST_TOOL_RESULT_KEY = "lastToolResult";

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
        if (Boolean.FALSE.equals(AgentUtils.resolveBoolean(nodeProperties, "enabled", "toolExecutionEnabled"))) {
            log.info("ToolExecutingNode: 节点 {} 已禁用工具执行", nextState.getCurrentNodeId());
            nextState.getPendingToolRequests().clear();
            return new ExecutionResult(nextState, LlmProcessingNode.NAME);
        }
        Set<String> allowedTools =
                AgentUtils.resolveToolNames(nodeProperties, toolProvider, "allowedTools", "toolNames", "tools");

        if (requests == null || requests.isEmpty()) {
            return new ExecutionResult(nextState, LlmProcessingNode.NAME);
        }

        for (ToolExecutionRequest request : requests) {
            if (!allowedTools.isEmpty() && !allowedTools.contains(request.name())) {
                String deniedResult = "Tool '" + request.name() + "' is not allowed in current node.";
                log.warn("ToolExecutingNode: 节点 {} 拒绝执行工具 {}", nextState.getCurrentNodeId(), request.name());
                nextState.addMessage(ToolExecutionResultMessage.from(request, deniedResult));
                recordToolResult(nextState, request, deniedResult, false, "tool_not_allowed");
                continue;
            }
            log.info("执行目标 Tool ->: {} | Arguments: {}", request.name(), request.arguments());
            String result;
            boolean success;
            String error = null;
            try {
                result = toolProvider.executeTool(request);
                success = !(result != null && result.startsWith("Error"));
                if (!success) {
                    error = result;
                }
            } catch (Exception e) {
                success = false;
                error = e.getMessage();
                result = "Error during tool execution: " + error;
            }
            log.debug("执行结果：{}", result);

            ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(request, result);
            nextState.addMessage(resultMessage);
            recordToolResult(nextState, request, result, success, error);
        }

        nextState.getPendingToolRequests().clear();

        return new ExecutionResult(nextState, null);
    }

    @SuppressWarnings("unchecked")
    private void recordToolResult(
            AgentState state, ToolExecutionRequest request, String result, boolean success, String error) {
        if (state.getAttributes() == null) {
            state.setAttributes(new java.util.concurrent.ConcurrentHashMap<>());
        }
        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("toolName", request.name());
        resultPayload.put("requestId", request.id());
        resultPayload.put("success", success);
        resultPayload.put("status", success ? "success" : "failed");
        resultPayload.put("code", success ? 200 : 500);
        resultPayload.put("result", result);
        if (error != null && !error.isBlank()) {
            resultPayload.put("error", error);
        }

        Object existingResults = state.getAttributes().get(TOOL_RESULTS_KEY);
        Map<String, Object> toolResults;
        if (existingResults instanceof Map<?, ?> map) {
            toolResults = new LinkedHashMap<>((Map<String, Object>) map);
            state.getAttributes().put(TOOL_RESULTS_KEY, toolResults);
        } else {
            toolResults = new LinkedHashMap<>();
            state.getAttributes().put(TOOL_RESULTS_KEY, toolResults);
        }
        String resultKey = request.id() != null && !request.id().isBlank()
                ? request.id()
                : request.name() + "_" + (toolResults.size() + 1);
        toolResults.put(resultKey, resultPayload);
        state.getAttributes().put(LAST_TOOL_RESULT_KEY, resultPayload);
    }
}
