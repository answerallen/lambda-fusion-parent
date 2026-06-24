package com.lambda.fusion.ai.agent.node;

import static com.lambda.fusion.ai.utils.AgentUtils.newStateWithCurrentNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.factory.ChatModelFactory;
import com.lambda.fusion.ai.prompt.service.PromptTemplateService;
import dev.langchain4j.data.message.AiMessage;
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
import tools.jackson.databind.ObjectMapper;

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
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("expert_b"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put(
                "candidates",
                List.of(
                        Map.of("id", "expert_a", "target", "agentA", "description", "财务专家"),
                        Map.of("id", "expert_b", "target", "agentB", "description", "代码专家")));
        AgentState state = newStateWithCurrentNode("supervisor-1", properties, "请找代码专家处理");

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
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("FINISH"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put("candidates", List.of(Map.of("id", "expert_a", "target", "agentA", "description", "通用专家")));
        AgentState state = newStateWithCurrentNode("supervisor-2", properties, "任务已经完成");

        var result = node.execute(state);

        assertThat(state.isFinished()).isTrue();
        assertThat(result.nextNode()).isEqualTo("END");
    }

    @Test
    @DisplayName("SUPERVISOR_AGENT 在 finishOnEnd=false 时仍应结束路由但不设置 finished")
    void shouldEndRouteWithoutMarkingFinishedWhenFinishOnEndDisabled() {
        SupervisorAgentNode node = new SupervisorAgentNode(
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("FINISH"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put("finishOnEnd", false);
        properties.put("candidates", List.of(Map.of("id", "expert_a", "target", "agentA", "description", "通用专家")));
        AgentState state = newStateWithCurrentNode("supervisor-3", properties, "任务已经完成");

        var result = node.execute(state);

        assertThat(state.isFinished()).isFalse();
        assertThat(result.nextNode()).isEqualTo("END");
    }

    @Test
    @DisplayName("SUPERVISOR_AGENT 支持解析 JSON 决策结果")
    void shouldParseJsonDecisionPayload() {
        SupervisorAgentNode node = new SupervisorAgentNode(
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("model-supervisor")).thenReturn(chatModel);
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"nextNode\":\"expert_b\"}"))
                        .build());

        Map<String, Object> properties = new HashMap<>();
        properties.put("modelId", "model-supervisor");
        properties.put(
                "candidates",
                List.of(
                        Map.of("id", "expert_a", "target", "agentA", "description", "财务专家"),
                        Map.of("id", "expert_b", "target", "agentB", "description", "代码专家")));
        AgentState state = newStateWithCurrentNode("supervisor-4", properties, "请找代码专家处理");

        var result = node.execute(state);

        assertThat(result.nextNode()).isEqualTo("agentB");
        assertThat(((Map<?, ?>) state.getAttributes().get("lastSupervisorDecision")).get("decisionSource"))
                .isEqualTo("candidate");
    }

    private ObjectProvider<ChatModelFactory> chatModelFactoryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModelFactory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModelFactory);
        return provider;
    }
}
