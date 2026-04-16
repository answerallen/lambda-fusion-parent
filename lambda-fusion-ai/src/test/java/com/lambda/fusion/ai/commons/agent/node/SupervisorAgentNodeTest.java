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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupervisorAgentNodeTest {

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel chatModel;

    @Test
    @DisplayName("SUPERVISOR_AGENT 会选择目标专家节点")
    void shouldRouteToTargetExpert() {
        SupervisorAgentNode node = new SupervisorAgentNode(
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        node.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("expert_b"))
                        .build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("请找代码专家处理"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "supervisor-1");
        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put(
                "candidates",
                List.of(
                        Map.of("id", "expert_a", "target", "agentA", "description", "财务专家"),
                        Map.of("id", "expert_b", "target", "agentB", "description", "代码专家")));
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

        var result = node.execute(state);

        assertThat(result.nextNode()).isEqualTo("agentB");
        assertThat(state.isFinished()).isFalse();
        assertThat(state.getAttributes()).containsKeys("supervisorDecisions", "lastSupervisorDecision");
        assertThat(((Map<?, ?>) state.getAttributes().get("lastSupervisorDecision")).get("nextNode"))
                .isEqualTo("agentB");
    }

    @Test
    @DisplayName("SUPERVISOR_AGENT 选择 FINISH 时结束工作流")
    void shouldFinishWhenModelReturnsFinish() {
        SupervisorAgentNode node = new SupervisorAgentNode(
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        node.setChatModelFactory(chatModelFactory);

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("FINISH"))
                        .build());

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("任务已经完成"));
        state.getAttributes().put(CURRENT_NODE_ID_ATTRIBUTE, "supervisor-2");
        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put("candidates", List.of(Map.of("id", "expert_a", "target", "agentA", "description", "通用专家")));
        state.getAttributes().put(CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);

        var result = node.execute(state);

        assertThat(state.isFinished()).isTrue();
        assertThat(result.nextNode()).isEqualTo("END");
    }
}
