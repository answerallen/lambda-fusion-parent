package com.lambda.fusion.ai.agent;

import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图节点：负责向大语言模型请求分析，决定是回复用户还是调用后续Tool。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProcessingNode implements AgentNode {

    public static final String NAME = "LLM_PROCESSOR";

    private final ChatModelFactory chatModelFactory;
    private final AgentToolProvider toolProvider;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String execute(AgentState state) {
        log.info("LlmProcessingNode: 正在推理决策...");

        List<ToolSpecification> tools = toolProvider.getToolSpecifications();
        StreamingChatResponseHandler handler =
                (StreamingChatResponseHandler) state.getAttributes().get("streamHandler");

        ChatResponse response;

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(state.getMessages());
        if (!tools.isEmpty()) {
            requestBuilder.toolSpecifications(tools);
        }
        ChatRequest request = requestBuilder.build();

        if (handler != null) {
            StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(state.getLlmModelId());
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();

            StreamingChatResponseHandler innerHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    handler.onPartialResponse(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse res) {
                    future.complete(res);
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                    handler.onError(error);
                }
            };

            streamingModel.chat(request, innerHandler);

            try {
                response = future.join();
            } catch (Exception e) {
                log.error("LlmProcessingNode 异步流被中断", e);
                state.setFinished(true);
                return AgentGraph.END_NODE;
            }
        } else {
            ChatModel chatModel = chatModelFactory.getChatModel(state.getLlmModelId());
            response = chatModel.chat(request);
        }

        // 保存机器人的返回（不论是意图声明还是普通会话）
        state.addMessage(response.aiMessage());

        if (response.tokenUsage() != null) {
            int pTokens = (int) state.getAttributes().getOrDefault("promptTokens", 0);
            int cTokens = (int) state.getAttributes().getOrDefault("completionTokens", 0);
            state.getAttributes()
                    .put("promptTokens", pTokens + response.tokenUsage().inputTokenCount());
            state.getAttributes()
                    .put("completionTokens", cTokens + response.tokenUsage().outputTokenCount());
        }

        if (response.aiMessage().hasToolExecutionRequests()) {
            // 将请求丢给状态中继供下一个节点(ToolNode)挂载执行
            state.setPendingToolRequests(response.aiMessage().toolExecutionRequests());
            return ToolExecutingNode.NAME; // 流转到动作执行节点
        } else {
            // 没有进一步工具请求，推理结束，可以答复用户
            state.setFinished(true);
            return AgentGraph.END_NODE;
        }
    }
}
