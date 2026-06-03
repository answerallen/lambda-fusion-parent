package com.lambda.fusion.ai.agent.node;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RATE_LIMITER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.service.PromptTemplateService;
import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.utils.AgentUtils;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.agentexecutor.MessageWindowConversationContextPolicy;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 LangGraph4j AgentExecutor 的 ReAct 智能体节点。
 *
 * <p>该节点把官方 ReAct agent 作为单个图节点嵌入到现有 AgentGraph 中，
 * 从而可以通过多个 REACT_AGENT 节点配合 CONDITIONAL、PARALLEL、SUBGRAPH
 * 组成 supervisor/专家式多智能体工作流。</p>
 */
@Slf4j
@Component
public class ReactAgentNode implements AgentNode {

    public static final String NAME = "REACT_AGENT";
    private static final String REACT_AGENT_RESULTS_KEY = "reactAgentResults";
    private static final String LAST_REACT_AGENT_RESULT_KEY = "lastReactAgentResult";
    private static final long STREAMING_EXECUTION_TIMEOUT_SECONDS = 120;

    private final ObjectProvider<ChatModelFactory> chatModelFactoryProvider;
    private final AgentToolProvider toolProvider;
    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public ReactAgentNode(
            ObjectProvider<ChatModelFactory> chatModelFactoryProvider,
            AgentToolProvider toolProvider,
            PromptTemplateService promptTemplateService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.chatModelFactoryProvider = chatModelFactoryProvider;
        this.toolProvider = toolProvider;
        this.promptTemplateService = promptTemplateService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        log.info("ReactAgentNode: 开始执行 LangGraph4j ReAct Agent...");

        Map<String, Object> nodeProperties = state.getCurrentNodeProperties();
        String effectiveModelId = AgentUtils.resolveModelId(state, nodeProperties);
        String systemPrompt = resolveSystemPrompt(state, nodeProperties);
        Set<String> allowedTools = resolveToolNames(nodeProperties);
        String nextNode = AgentUtils.resolveText(nodeProperties, "nextNode", "afterNode");
        boolean finishOnResponse = Boolean.TRUE.equals(
                AgentUtils.resolveBoolean(nodeProperties, "finishOnResponse", "markFinished", "finishWhenDone"));
        StreamingChatResponseHandler streamHandler = resolveStreamHandler(state);
        boolean streamingEnabled = streamHandler != null
                && !Boolean.FALSE.equals(AgentUtils.resolveBoolean(nodeProperties, "streamingEnabled", "stream"));

        if (StringUtils.hasText(effectiveModelId)) {
            state.setLlmModelId(effectiveModelId);
        }

        try {
            AgentExecutionResult result = executeWithResilience(
                    state,
                    effectiveModelId,
                    systemPrompt,
                    allowedTools,
                    streamingEnabled,
                    streamHandler,
                    nodeProperties);
            applyExecutionResult(state, result, finishOnResponse);
            return new ExecutionResult(state, finishOnResponse ? AgentGraph.END_NODE : nextNode);
        } catch (CallNotPermittedException e) {
            log.warn("ReAct Agent 熔断器已打开，执行降级策略");
            return handleFallback(state, "ReAct Agent 服务暂时不可用，请稍后重试");
        } catch (Throwable e) {
            log.error("ReAct Agent 调用失败", e);
            return handleFallback(state, "ReAct Agent 调用失败: " + e.getMessage());
        }
    }

    private AgentExecutionResult executeWithResilience(
            AgentState state,
            String modelId,
            String systemPrompt,
            Set<String> allowedTools,
            boolean streamingEnabled,
            StreamingChatResponseHandler streamHandler,
            Map<String, Object> nodeProperties)
            throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(LLM_CIRCUIT_BREAKER);
        Retry retry = retryRegistry.retry(LLM_RETRY);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(LLM_RATE_LIMITER);

        Supplier<AgentExecutionResult> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return doReactAgentCall(
                        state, modelId, systemPrompt, allowedTools, streamingEnabled, streamHandler, nodeProperties);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);

        try {
            return decoratedSupplier.get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception) {
                throw e.getCause();
            }
            throw e;
        }
    }

    private AgentExecutionResult doReactAgentCall(
            AgentState state,
            String modelId,
            String systemPrompt,
            Set<String> allowedTools,
            boolean streamingEnabled,
            StreamingChatResponseHandler streamHandler,
            Map<String, Object> nodeProperties)
            throws Exception {
        List<ToolSpecification> toolSpecifications = allowedTools.isEmpty()
                ? toolProvider.getToolSpecifications()
                : toolProvider.getToolSpecifications(allowedTools);
        if (toolSpecifications == null) {
            toolSpecifications = List.of();
        }

        AgentExecutor.Builder builder = AgentExecutor.builder();
        if (streamingEnabled) {
            builder.chatModel(getChatModelFactory().getStreamingChatModel(modelId));
        } else {
            builder.chatModel(getChatModelFactory().getChatModel(modelId));
        }
        if (StringUtils.hasText(systemPrompt)) {
            builder.systemMessage(SystemMessage.from(systemPrompt.trim()));
        }
        Integer maxContextMessages = AgentUtils.resolveInteger(
                nodeProperties, "maxContextMessages", "messageWindowSize", "contextWindowSize");
        if (maxContextMessages != null && maxContextMessages > 0) {
            builder.conversationContextPolicy(new MessageWindowConversationContextPolicy(maxContextMessages));
        }

        for (ToolSpecification toolSpecification : toolSpecifications) {
            builder.tool(toolSpecification, (request, memoryId) -> toolProvider.executeTool(request));
        }

        CompiledGraph<AgentExecutor.State> workflow = builder.build().compile();
        Map<String, Object> input = Map.of(
                MessagesState.MESSAGES_STATE,
                state.getMessages() == null ? List.of() : new ArrayList<>(state.getMessages()));

        AgentExecutor.State outputState;
        if (streamingEnabled) {
            NodeOutput<AgentExecutor.State> finalOutput = workflow.stream(input)
                    .<NodeOutput<AgentExecutor.State>>reduce(null, (previous, output) -> {
                        if (output instanceof StreamingOutput<AgentExecutor.State> streamingOutput
                                && !streamingOutput.isStreamingEnd()) {
                            streamHandler.onPartialResponse(streamingOutput.chunk());
                        }
                        return output;
                    })
                    .orTimeout(STREAMING_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
            outputState = finalOutput == null ? null : finalOutput.state();
        } else {
            outputState = workflow.invoke(input).orElse(null);
        }

        String finalResponse = extractFinalResponse(outputState);
        List<String> toolNames = toolSpecifications.stream()
                .map(ToolSpecification::name)
                .distinct()
                .toList();
        return new AgentExecutionResult(outputState, finalResponse, toolNames, modelId);
    }

    private void applyExecutionResult(AgentState state, AgentExecutionResult result, boolean finishOnResponse) {
        List<ChatMessage> previousMessages =
                state.getMessages() == null ? List.of() : new ArrayList<>(state.getMessages());
        AgentExecutor.State outputState = result.outputState();
        if (outputState != null && outputState.messages() != null) {
            state.setMessages(new CopyOnWriteArrayList<>(outputState.messages()));
        }
        if (state.getPendingToolRequests() != null) {
            state.getPendingToolRequests().clear();
        }
        if (finishOnResponse) {
            state.setFinished(true);
        }
        recordExecutionMetadata(state, previousMessages.size(), result);
    }

    private void recordExecutionMetadata(AgentState state, int previousMessageSize, AgentExecutionResult result) {
        if (state.getAttributes() == null) {
            state.setAttributes(new HashMap<>());
        }
        List<ChatMessage> messages = state.getMessages() == null ? List.of() : state.getMessages();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", state.getCurrentNodeId());
        payload.put("modelId", result.modelId());
        payload.put("toolNames", result.toolNames());
        payload.put("messageCount", messages.size());
        payload.put("messageDelta", Math.max(0, messages.size() - previousMessageSize));
        payload.put("toolMessageDelta", countToolMessages(messages, previousMessageSize));
        if (StringUtils.hasText(result.finalResponse())) {
            payload.put("finalResponse", result.finalResponse());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reactResults = state.getAttributes().get(REACT_AGENT_RESULTS_KEY) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        reactResults.put(state.getCurrentNodeId(), payload);
        state.getAttributes().put(REACT_AGENT_RESULTS_KEY, reactResults);
        state.getAttributes().put(LAST_REACT_AGENT_RESULT_KEY, payload);
    }

    private int countToolMessages(List<ChatMessage> messages, int previousMessageSize) {
        if (messages == null || messages.size() <= previousMessageSize) {
            return 0;
        }
        int count = 0;
        for (int i = previousMessageSize; i < messages.size(); i++) {
            if (messages.get(i) instanceof ToolExecutionResultMessage) {
                count++;
            }
        }
        return count;
    }

    private ExecutionResult handleFallback(AgentState state, String fallbackMessage) {
        state.addMessage(AiMessage.from(fallbackMessage));
        state.setFinished(true);
        state.getAttributes().put("fallback", true);
        return new ExecutionResult(state, AgentGraph.END_NODE);
    }

    private String extractFinalResponse(AgentExecutor.State outputState) {
        if (outputState == null) {
            return null;
        }
        return outputState.finalResponse().orElseGet(() -> extractLastAiMessageText(outputState.messages()));
    }

    private String extractLastAiMessageText(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message instanceof AiMessage aiMessage) {
                return aiMessage.text();
            }
        }
        return null;
    }

    private StreamingChatResponseHandler resolveStreamHandler(AgentState state) {
        if (state.getAttributes() == null) {
            return null;
        }
        Object handler = state.getAttributes().get("streamHandler");
        return handler instanceof StreamingChatResponseHandler streamingChatResponseHandler
                ? streamingChatResponseHandler
                : null;
    }

    private String resolveSystemPrompt(AgentState state, Map<String, Object> nodeProperties) {
        return AgentUtils.resolveSystemPrompt(
                nodeProperties,
                promptTemplateService::renderTemplate,
                () -> buildTemplateVariables(state, nodeProperties));
    }

    private Map<String, Object> buildTemplateVariables(AgentState state, Map<String, Object> nodeProperties) {
        Map<String, Object> variables = AgentUtils.buildBaseTemplateVariables(state);
        AgentUtils.mergeTemplateVariables(variables, nodeProperties);
        return variables;
    }

    private Set<String> resolveToolNames(Map<String, Object> nodeProperties) {
        return AgentUtils.resolveToolNames(nodeProperties, toolProvider, "allowedTools", "toolNames", "tools");
    }

    private ChatModelFactory getChatModelFactory() {
        ChatModelFactory factory = chatModelFactoryProvider.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("ChatModelFactory 未初始化");
        }
        return factory;
    }

    private record AgentExecutionResult(
            AgentExecutor.State outputState, String finalResponse, List<String> toolNames, String modelId) {}
}
