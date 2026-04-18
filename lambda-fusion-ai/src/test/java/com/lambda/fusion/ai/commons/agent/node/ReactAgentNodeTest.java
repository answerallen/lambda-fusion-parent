package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.commons.utils.AgentUtils.newStateWithCurrentNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
class ReactAgentNodeTest {

    @Mock
    private AgentToolProvider toolProvider;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("REACT_AGENT 节点会回写 ReAct 执行结果到 AgentState")
    void shouldWriteBackReactAgentResult() {
        ReactAgentNode reactAgentNode = new ReactAgentNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-1")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("react-agent-answer"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-1");
        AgentState state = newStateWithCurrentNode("react-node-1", properties, "帮我分析一下");

        var result = reactAgentNode.execute(state);

        assertThat(result.nextNode()).isNull();
        assertThat(state.isFinished()).isFalse();
        assertThat(state.getMessages()).hasSize(2);
        assertThat(state.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) state.getMessages().getLast()).text()).isEqualTo("react-agent-answer");
        assertThat(state.getAttributes()).containsKeys("lastReactAgentResult", "reactAgentResults");
        assertThat(((Map<?, ?>) state.getAttributes().get("lastReactAgentResult")).get("finalResponse"))
                .isEqualTo("react-agent-answer");
    }

    @Test
    @DisplayName("配置 finishOnResponse 时 REACT_AGENT 会结束当前工作流")
    void shouldFinishWorkflowWhenConfigured() {
        ReactAgentNode reactAgentNode = new ReactAgentNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-2")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-2");
        properties.put("finishOnResponse", true);
        AgentState state = newStateWithCurrentNode("react-node-2", properties, "结束流程");

        var result = reactAgentNode.execute(state);

        assertThat(state.isFinished()).isTrue();
        assertThat(result.nextNode()).isEqualTo("END");
    }

    @Test
    @DisplayName("REACT_AGENT 支持工具调用闭环并回写工具消息")
    void shouldExecuteToolLoopAndWriteBackToolMessages() {
        ReactAgentNode reactAgentNode = new ReactAgentNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("echoTool")
                .arguments("{\"message\":\"hello\"}")
                .build();
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("echoTool")
                .description("echo tool")
                .build();

        when(chatModelFactory.getChatModel("model-3")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of(toolSpecification));
        when(toolProvider.executeTool(any(ToolExecutionRequest.class))).thenReturn("tool-result");
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(toolRequest)))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("tool-loop-finished"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-3");
        AgentState state = newStateWithCurrentNode("react-node-3", properties, "调用工具并总结");

        var result = reactAgentNode.execute(state);

        assertThat(result.nextNode()).isNull();
        assertThat(state.getMessages()).anyMatch(message -> message instanceof ToolExecutionResultMessage);
        assertThat(state.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) state.getMessages().getLast()).text()).isEqualTo("tool-loop-finished");
        assertThat(((Map<?, ?>) state.getAttributes().get("lastReactAgentResult")).get("toolMessageDelta"))
                .isEqualTo(1);
        verify(toolProvider).executeTool(any(ToolExecutionRequest.class));
    }

    private ObjectProvider<ChatModelFactory> chatModelFactoryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModelFactory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModelFactory);
        return provider;
    }
}
