package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_ID_ATTRIBUTE;
import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        reactAgentNode.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-1")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("react-agent-answer"))
                        .build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("帮我分析一下"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "react-node-1");
        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-1");
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

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
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        reactAgentNode.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-2")).thenReturn(chatModel);
        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(
                        ChatResponse.builder().aiMessage(AiMessage.from("done")).build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("结束流程"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "react-node-2");
        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-2");
        properties.put("finishOnResponse", true);
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

        var result = reactAgentNode.execute(state);

        assertThat(state.isFinished()).isTrue();
        assertThat(result.nextNode()).isEqualTo("END");
    }
}
