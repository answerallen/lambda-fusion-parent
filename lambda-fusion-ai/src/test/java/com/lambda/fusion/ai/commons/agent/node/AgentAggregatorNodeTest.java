package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_ID_ATTRIBUTE;
import static com.lambda.fusion.ai.commons.agent.AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.AgentState;
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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        node.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-agg")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("综合结论：建议采用方案B"))
                        .build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("请汇总所有专家结论"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "aggregator-1");
        state.getAttributes()
                .put("__parallel_results__", Map.of("branch1", Map.of("success", true, "messages", "专家1认为方案A可行")));
        state.getAttributes().put("reactAgentResults", Map.of("agentB", Map.of("finalResponse", "专家2建议方案B，风险更低")));

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-agg");
        properties.put("outputKey", "finalAnswer");
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

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
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        node.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-agg")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("阶段汇总完成"))
                        .build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("先做阶段汇总"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "aggregator-2");
        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-agg");
        properties.put("finishOnResponse", false);
        properties.put("nextNode", "finalReviewer");
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

        var result = node.execute(state);

        assertThat(state.isFinished()).isFalse();
        assertThat(result.nextNode()).isEqualTo("finalReviewer");
    }
}
