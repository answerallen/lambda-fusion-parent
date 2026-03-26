package com.lambda.fusion.ai.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.List;
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
    public String execute(AgentState state) {
        log.info("ToolExecutingNode: 开始执行本地动作回调...");
        List<ToolExecutionRequest> requests = state.getPendingToolRequests();

        if (requests == null || requests.isEmpty()) {
            // 没有有效请求，返回主节点
            return LlmProcessingNode.NAME;
        }

        for (ToolExecutionRequest request : requests) {
            log.info("执行目标 Tool ->: {} | Arguments: {}", request.name(), request.arguments());
            String result = toolProvider.executeTool(request);
            log.debug("执行结果：{}", result);

            // 将结果包装为 Result 事件记录放入对话记录池
            ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(request, result);
            state.addMessage(resultMessage);
        }

        // 清空当前执行意图
        state.getPendingToolRequests().clear();

        // 执行成功后把信息交回给LLM分析节点闭环
        return LlmProcessingNode.NAME;
    }
}
