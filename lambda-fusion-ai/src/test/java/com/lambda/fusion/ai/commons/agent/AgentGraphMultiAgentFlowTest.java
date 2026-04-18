package com.lambda.fusion.ai.commons.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.commons.agent.node.AgentAggregatorNode;
import com.lambda.fusion.ai.commons.agent.node.ParallelNode;
import com.lambda.fusion.ai.commons.agent.node.ReactAgentNode;
import com.lambda.fusion.ai.commons.agent.node.SubgraphNode;
import com.lambda.fusion.ai.commons.agent.node.SupervisorAgentNode;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import com.lambda.fusion.ai.service.WorkflowService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AgentGraphMultiAgentFlowTest {

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private AgentToolProvider toolProvider;

    @Mock
    private ChatModelFactory chatModelFactory;

    @Mock
    private ChatModel supervisorModel;

    @Mock
    private ChatModel reactModel;

    @Mock
    private ChatModel aggregatorModel;

    @Mock
    private AgentGraphFactory graphFactory;

    @Mock
    private WorkflowService workflowService;

    @Test
    @DisplayName("AgentGraph 支持 SUPERVISOR_AGENT -> REACT_AGENT -> AGGREGATOR 端到端流转")
    void shouldExecuteSupervisorReactAggregatorFlow() {
        ObjectMapper objectMapper = new ObjectMapper();
        SupervisorAgentNode supervisor = new SupervisorAgentNode(
                objectMapper,
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        ReactAgentNode reactAgent = new ReactAgentNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        AgentAggregatorNode aggregator = new AgentAggregatorNode(
                objectMapper,
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModelFactory.getChatModel("supervisor-model")).thenReturn(supervisorModel);
        when(chatModelFactory.getChatModel("react-model")).thenReturn(reactModel);
        when(chatModelFactory.getChatModel("aggregator-model")).thenReturn(aggregatorModel);

        when(supervisorModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("expert_route"))
                        .build());
        when(reactModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("专家节点输出"))
                        .build());
        when(aggregatorModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("最终综合结论"))
                        .build());

        AgentGraph graph = new AgentGraph();
        graph.addNode(
                "supervisor",
                supervisor,
                Map.of(
                        "modelId",
                        "supervisor-model",
                        "candidates",
                        List.of(
                                Map.of("id", "expert_route", "target", "expert", "description", "专家节点"),
                                Map.of("id", "aggregator", "target", "aggregator", "description", "汇总节点"))));
        graph.addNode(
                "expert",
                reactAgent,
                Map.of("modelId", "react-model", "nextNode", "aggregator", "finishOnResponse", false));
        graph.addNode(
                "aggregator",
                aggregator,
                Map.of("modelId", "aggregator-model", "outputKey", "finalAnswer", "finishOnResponse", true));
        graph.setEntryPoint("supervisor");

        AgentState initialState = new AgentState();
        initialState.addMessage(UserMessage.from("请完成多智能体分析"));

        AgentState result = graph.invoke(initialState);

        assertThat(result.isFinished()).isTrue();
        assertThat(result.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) result.getMessages().getLast()).text()).isEqualTo("最终综合结论");
        assertThat(result.getAttributes().get("finalAnswer")).isEqualTo("最终综合结论");
        assertThat(result.getAttributes())
                .containsKeys("reactAgentResults", "supervisorDecisions", "aggregatorResults");
    }

    @Test
    @DisplayName("AgentGraph 支持 PARALLEL -> AGGREGATOR 端到端汇总并保留结构化输入")
    void shouldExecuteParallelAggregatorFlow() {
        ParallelNode parallelNode = new ParallelNode(Runnable::run);
        AgentAggregatorNode aggregator = new AgentAggregatorNode(
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());

        when(chatModelFactory.getChatModel("aggregator-model")).thenReturn(aggregatorModel);
        when(aggregatorModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("并行汇总结论"))
                        .build());

        AgentNode branchA = new AgentNode() {
            @Override
            public String getName() {
                return "BRANCH_A";
            }

            @Override
            public ExecutionResult execute(AgentState state) {
                state.getAttributes().put("summary", "专家A结论");
                state.addMessage(AiMessage.from("专家A消息"));
                return new ExecutionResult(state, null);
            }
        };

        AgentNode branchB = new AgentNode() {
            @Override
            public String getName() {
                return "BRANCH_B";
            }

            @Override
            public ExecutionResult execute(AgentState state) {
                state.getAttributes().put("summary", "专家B结论");
                state.addMessage(AiMessage.from("专家B消息"));
                return new ExecutionResult(state, null);
            }
        };

        Map<String, Object> parallelProperties = new HashMap<>();
        parallelProperties.put(
                "branches",
                List.of(Map.of("id", "branchA", "target", "branchA"), Map.of("id", "branchB", "target", "branchB")));
        parallelProperties.put("joinNode", "aggregator");
        parallelProperties.put("waitAll", true);

        AgentGraph graph = new AgentGraph();
        graph.addNode("parallel", parallelNode, parallelProperties);
        graph.addNode("branchA", branchA);
        graph.addNode("branchB", branchB);
        graph.addNode(
                "aggregator",
                aggregator,
                Map.of(
                        "modelId",
                        "aggregator-model",
                        "outputKey",
                        "finalAnswer",
                        "finishOnResponse",
                        true,
                        "includeParallelResults",
                        true));
        graph.setEntryPoint("parallel");

        AgentState initialState = new AgentState();
        initialState.addMessage(UserMessage.from("请并行分析"));

        AgentState result = graph.invoke(initialState);

        assertThat(result.isFinished()).isTrue();
        assertThat(result.getAttributes().get("finalAnswer")).isEqualTo("并行汇总结论");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aggregatorModel).chat(captor.capture());
        var lastMessage = captor.getValue().messages().getLast();
        assertThat(lastMessage).isInstanceOf(SystemMessage.class);
        String prompt = ((SystemMessage) lastMessage).text();
        assertThat(prompt).contains("专家A结论");
        assertThat(prompt).contains("专家B结论");
        assertThat(prompt).contains("\"parallelResults\"");
    }

    @Test
    @DisplayName("AgentGraph 支持 SUBGRAPH 嵌套 REACT_AGENT 与 AGGREGATOR 的端到端流转")
    void shouldExecuteSubgraphWithReactAndAggregatorFlow() throws Exception {
        ReactAgentNode reactAgent = new ReactAgentNode(
                chatModelFactoryProvider(),
                toolProvider,
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        AgentAggregatorNode aggregator = new AgentAggregatorNode(
                new ObjectMapper(),
                chatModelFactoryProvider(),
                promptTemplateService,
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
        SubgraphNode subgraphNode = new SubgraphNode(graphFactory, workflowService, Runnable::run, new AiProperties());

        when(toolProvider.getToolSpecifications()).thenReturn(List.of());
        when(chatModelFactory.getChatModel("react-model")).thenReturn(reactModel);
        when(chatModelFactory.getChatModel("aggregator-model")).thenReturn(aggregatorModel);
        when(reactModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("子图专家输出"))
                        .build());
        when(aggregatorModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("子图最终结论"))
                        .build());

        AgentGraph subgraph = new AgentGraph();
        subgraph.addNode(
                "expert",
                reactAgent,
                Map.of("modelId", "react-model", "nextNode", "aggregator", "finishOnResponse", false));
        subgraph.addNode(
                "aggregator",
                aggregator,
                Map.of("modelId", "aggregator-model", "outputKey", "finalAnswer", "finishOnResponse", true));
        subgraph.setEntryPoint("expert");

        when(graphFactory.buildFromDefinition(any(String.class))).thenReturn(subgraph);

        AgentGraph parentGraph = new AgentGraph();
        parentGraph.addNode(
                "subgraph",
                subgraphNode,
                Map.of(
                        "subgraphDefinition",
                        "{\"entryPoint\":\"expert\"}",
                        "outputMapping",
                        Map.of("attributes.subgraphAnswer", "finalAnswer")));
        parentGraph.setEntryPoint("subgraph");

        AgentState initialState = new AgentState();
        initialState.addMessage(UserMessage.from("请执行子图分析"));

        AgentState result = parentGraph.invoke(initialState);

        assertThat(result.isFinished()).isTrue();
        assertThat(result.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) result.getMessages().getLast()).text()).isEqualTo("子图最终结论");
        assertThat(result.getAttributes().get("subgraphAnswer")).isEqualTo("子图最终结论");
    }

    private ObjectProvider<ChatModelFactory> chatModelFactoryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModelFactory> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModelFactory);
        return provider;
    }
}
