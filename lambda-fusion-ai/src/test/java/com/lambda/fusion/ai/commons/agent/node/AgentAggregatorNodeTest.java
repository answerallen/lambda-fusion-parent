package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.commons.utils.AgentUtils.newStateWithCurrentNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AgentAggregatorNodeTest {

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("AGENT_AGGREGATOR 会汇总结果并写回消息与属性")
    void shouldAggregateResultsAndWriteBackState() {
        AgentAggregatorNode node = new AgentAggregatorNode(
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-agg")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("综合结论：建议采用方案B"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-agg");
        properties.put("outputKey", "finalAnswer");
        AgentState state = newStateWithCurrentNode("aggregator-1", properties, "请汇总所有专家结论");
        state.getAttributes()
                .put("__parallel_results__", Map.of("branch1", Map.of("success", true, "messages", "专家1认为方案A可行")));
        state.getAttributes().put("reactAgentResults", Map.of("agentB", Map.of("finalResponse", "专家2建议方案B，风险更低")));

        var result = node.execute(state);

        assertThat(state.isFinished()).isTrue();
        assertThat(result.nextNode()).isEqualTo("END");
        assertThat(state.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) state.getMessages().getLast()).text()).isEqualTo("综合结论：建议采用方案B");
        assertThat(state.getAttributes().get("finalAnswer")).isEqualTo("综合结论：建议采用方案B");
        assertThat(state.getAttributes()).containsKeys("aggregatorResults", "lastAggregatorResult");
    }

    @Test
    @DisplayName("AGENT_AGGREGATOR 可作为中间汇总节点继续路由")
    void shouldContinueWorkflowWhenFinishDisabled() {
        AgentAggregatorNode node = new AgentAggregatorNode(
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-agg")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("阶段汇总完成"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-agg");
        properties.put("finishOnResponse", false);
        properties.put("nextNode", "finalReviewer");
        AgentState state = newStateWithCurrentNode("aggregator-2", properties, "先做阶段汇总");

        var result = node.execute(state);

        assertThat(state.isFinished()).isFalse();
        assertThat(result.nextNode()).isEqualTo("finalReviewer");
    }

    @Test
    @DisplayName("AGENT_AGGREGATOR 使用结构化 JSON 构造聚合输入")
    void shouldBuildStructuredJsonAggregationInput() {
        AgentAggregatorNode node = new AgentAggregatorNode(
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-agg")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("结构化汇总完成"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-agg");
        AgentState state = newStateWithCurrentNode("aggregator-3", properties, "请汇总");
        state.getAttributes()
                .put("__parallel_results__", Map.of("branch1", Map.of("success", true, "messages", List.of("delta"))));
        state.getAttributes().put("reactAgentResults", Map.of("agentB", Map.of("finalResponse", "专家建议")));

        node.execute(state);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        var lastMessage = captor.getValue().messages().getLast();
        assertThat(lastMessage).isInstanceOf(SystemMessage.class);
        String prompt = ((SystemMessage) lastMessage).text();
        assertThat(prompt).contains("\"parallelResults\"");
        assertThat(prompt).contains("\"reactAgentResults\"");
        assertThat(prompt).contains("\"currentNodeId\":\"aggregator-3\"");
    }

    private ObjectProvider<ChatModelFactory> chatModelFactoryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModelFactory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModelFactory);
        return provider;
    }
}
