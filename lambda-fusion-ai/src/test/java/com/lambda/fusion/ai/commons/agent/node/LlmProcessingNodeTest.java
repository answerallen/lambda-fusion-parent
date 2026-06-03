package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.utils.AgentUtils.newStateWithCurrentNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.node.LlmProcessingNode;
import com.lambda.fusion.ai.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.service.PromptTemplateService;
import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class LlmProcessingNodeTest {

    @Mock
    private AgentToolProvider toolProvider;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Mock
    private StreamingChatModel streamingChatModel;

    @Test
    @DisplayName("LLM_PROCESSOR 渲染模板时应合并节点自定义变量")
    void shouldMergeCustomTemplateVariables() {
        LlmProcessingNode node = new LlmProcessingNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-1")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(promptTemplateService.renderTemplate(eq("tpl-1"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = invocation.getArgument(1);
            assertThat(variables).containsEntry("customVar", "customValue");
            assertThat(variables).containsEntry("sessionId", "session-1");
            return "system prompt";
        });
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("done"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-1");
        properties.put("promptTemplateId", "tpl-1");
        properties.put("templateVariables", Map.of("customVar", "customValue"));
        AgentState state = newStateWithCurrentNode("llm-node-1", properties, "测试模板变量");
        state.setSessionId("session-1");

        var result = node.execute(state);

        assertThat(result).isNotNull();
        verify(promptTemplateService).renderTemplate(eq("tpl-1"), any());
    }

    @Test
    @DisplayName("流式调用在终态响应时应向上传递完成事件")
    void shouldForwardStreamingCompletionEvent() {
        LlmProcessingNode node = new LlmProcessingNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        StreamingChatResponseHandler outerHandler = org.mockito.Mockito.mock(StreamingChatResponseHandler.class);
        ChatResponse response =
                ChatResponse.builder().aiMessage(AiMessage.from("done")).build();

        when(chatModelFactory.getStreamingChatModel("model-1")).thenReturn(streamingChatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
                    StreamingChatResponseHandler innerHandler = invocation.getArgument(1);
                    innerHandler.onPartialResponse("done");
                    innerHandler.onCompleteResponse(response);
                    return null;
                })
                .when(streamingChatModel)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-1");
        AgentState state = newStateWithCurrentNode("llm-node-1", properties, "测试流式完成事件");
        state.getAttributes().put("streamHandler", outerHandler);

        var result = node.execute(state);

        assertThat(result).isNotNull();
        verify(outerHandler).onPartialResponse("done");
        verify(outerHandler).onCompleteResponse(response);
    }

    private ObjectProvider<ChatModelFactory> chatModelFactoryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModelFactory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModelFactory);
        return provider;
    }
}
