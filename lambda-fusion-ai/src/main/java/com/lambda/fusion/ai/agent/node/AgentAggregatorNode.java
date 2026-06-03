package com.lambda.fusion.ai.agent.node;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RATE_LIMITER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.service.PromptTemplateService;
import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.utils.AgentUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 多智能体结果聚合节点。
 *
 * <p>用于读取并行专家结果、ReAct 节点执行结果以及附加属性，
 * 生成最终答复或中间摘要。</p>
 */
@Slf4j
@Component
public class AgentAggregatorNode implements AgentNode {

    public static final String NAME = "AGENT_AGGREGATOR";
    public static final String AGGREGATOR_RESULTS_KEY = "aggregatorResults";
    public static final String LAST_AGGREGATOR_RESULT_KEY = "lastAggregatorResult";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModelFactory> chatModelFactoryProvider;
    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public AgentAggregatorNode(
            ObjectMapper objectMapper,
            ObjectProvider<ChatModelFactory> chatModelFactoryProvider,
            PromptTemplateService promptTemplateService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.objectMapper = objectMapper;
        this.chatModelFactoryProvider = chatModelFactoryProvider;
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
        log.info("AgentAggregatorNode: 开始聚合多智能体输出...");

        Map<String, Object> nodeProperties = state.getCurrentNodeProperties();
        String effectiveModelId = AgentUtils.resolveModelId(state, nodeProperties);
        String systemPrompt = resolveSystemPrompt(state, nodeProperties);
        String aggregationInput = buildAggregationInput(state, nodeProperties);
        String outputKey = AgentUtils.resolveText(nodeProperties, "outputKey", "resultKey", "attributeKey");
        if (!StringUtils.hasText(outputKey)) {
            outputKey = "aggregatedResult";
        }
        String nextNode = AgentUtils.resolveText(nodeProperties, "nextNode", "afterNode");
        boolean appendMessage = !Boolean.FALSE.equals(
                AgentUtils.resolveBoolean(nodeProperties, "appendMessage", "writeMessage", "emitMessage"));
        boolean finishOnResponse = !Boolean.FALSE.equals(
                AgentUtils.resolveBoolean(nodeProperties, "finishOnResponse", "markFinished", "finishWhenDone"));

        ExecutionResult executionResult = new ExecutionResult(state, finishOnResponse ? AgentGraph.END_NODE : nextNode);
        try {
            String answer = executeWithResilience(state, effectiveModelId, systemPrompt, aggregationInput);
            applyAggregationResult(state, answer, outputKey, appendMessage, finishOnResponse);
            return executionResult;
        } catch (CallNotPermittedException e) {
            log.warn("AgentAggregatorNode: 熔断器已打开，降级为本地摘要");
            String fallback = buildFallbackSummary(state);
            applyAggregationResult(state, fallback, outputKey, appendMessage, finishOnResponse);
            return executionResult;
        } catch (Throwable e) {
            log.error("AgentAggregatorNode: 聚合失败", e);
            String fallback = buildFallbackSummary(state);
            applyAggregationResult(state, fallback, outputKey, appendMessage, finishOnResponse);
            return executionResult;
        }
    }

    private String executeWithResilience(AgentState state, String modelId, String systemPrompt, String aggregationInput)
            throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(LLM_CIRCUIT_BREAKER);
        Retry retry = retryRegistry.retry(LLM_RETRY);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(LLM_RATE_LIMITER);

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return doAggregate(state, modelId, systemPrompt, aggregationInput);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);

        return AgentUtils.get(decoratedSupplier);
    }

    private String doAggregate(AgentState state, String modelId, String systemPrompt, String aggregationInput)
            throws Exception {
        ChatModel model = getChatModelFactory().getChatModel(modelId);
        ChatRequest request = ChatRequest.builder()
                .messages(buildMessages(state, systemPrompt, aggregationInput))
                .build();
        ChatResponse response = model.chat(request);
        if (response == null || response.aiMessage() == null) {
            return buildFallbackSummary(state);
        }
        return response.aiMessage().text();
    }

    private List<ChatMessage> buildMessages(AgentState state, String systemPrompt, String aggregationInput) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(SystemMessage.from(systemPrompt.trim()));
        }
        List<ChatMessage> history = state.getMessages();
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(SystemMessage.from("以下是待聚合的多智能体结果与上下文，请输出最终综合结论：\n" + aggregationInput));
        return messages;
    }

    @SuppressWarnings("unchecked")
    private void applyAggregationResult(
            AgentState state, String answer, String outputKey, boolean appendMessage, boolean finishOnResponse) {
        if (state.getAttributes() == null) {
            state.setAttributes(new HashMap<>());
        }
        if (StringUtils.hasText(answer) && appendMessage) {
            state.addMessage(AiMessage.from(answer));
        }
        state.getAttributes().put(outputKey, answer);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", state.getCurrentNodeId());
        payload.put("outputKey", outputKey);
        payload.put("answer", answer);
        payload.put("appendMessage", appendMessage);
        payload.put("finishOnResponse", finishOnResponse);

        Map<String, Object> results = state.getAttributes().get(AGGREGATOR_RESULTS_KEY) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        results.put(state.getCurrentNodeId(), payload);
        state.getAttributes().put(AGGREGATOR_RESULTS_KEY, results);
        state.getAttributes().put(LAST_AGGREGATOR_RESULT_KEY, payload);

        if (finishOnResponse) {
            state.setFinished(true);
        }
    }

    private String buildAggregationInput(AgentState state, Map<String, Object> nodeProperties) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", state.getSessionId());
        payload.put("kbId", state.getKbId());
        payload.put("llmModelId", state.getLlmModelId());
        payload.put("currentNodeId", state.getCurrentNodeId());

        if (Boolean.FALSE != AgentUtils.resolveBoolean(nodeProperties, "includeMessages", "withMessages")) {
            payload.put("messages", state.getMessages() == null ? List.of() : new ArrayList<>(state.getMessages()));
        }
        if (state.getAttributes() != null) {
            if (Boolean.FALSE
                    != AgentUtils.resolveBoolean(nodeProperties, "includeParallelResults", "withParallelResults")) {
                Object parallelResults = state.getAttributes().get("__parallel_results__");
                if (parallelResults != null) {
                    payload.put("parallelResults", parallelResults);
                }
            }
            if (Boolean.FALSE
                    != AgentUtils.resolveBoolean(nodeProperties, "includeReactAgentResults", "withReactAgentResults")) {
                Object reactAgentResults = state.getAttributes().get("reactAgentResults");
                if (reactAgentResults != null) {
                    payload.put("reactAgentResults", reactAgentResults);
                }
            }
            Object sourceKeysObj = AgentUtils.firstNonNull(nodeProperties, "sourceAttributes", "inputKeys");
            if (sourceKeysObj instanceof Iterable<?> iterable) {
                Map<String, Object> selectedAttributes = new LinkedHashMap<>();
                for (Object key : iterable) {
                    if (key == null || state.getAttributes() == null) {
                        continue;
                    }
                    String attributeKey = key.toString().trim();
                    if (!attributeKey.isEmpty() && state.getAttributes().containsKey(attributeKey)) {
                        selectedAttributes.put(
                                attributeKey, state.getAttributes().get(attributeKey));
                    }
                }
                if (!selectedAttributes.isEmpty()) {
                    payload.put("selectedAttributes", selectedAttributes);
                }
            }
        }
        return serializeAggregationPayload(payload);
    }

    private String buildFallbackSummary(AgentState state) {
        if (state.getAttributes() != null) {
            Object aggregatedResult = state.getAttributes().get("reactAgentResults");
            if (aggregatedResult != null) {
                return "已收集多智能体结果，请查看 reactAgentResults 与 parallelResults 属性。";
            }
        }
        if (state.getMessages() != null && !state.getMessages().isEmpty()) {
            ChatMessage lastMessage = state.getMessages().getLast();
            if (lastMessage instanceof AiMessage aiMessage && StringUtils.hasText(aiMessage.text())) {
                return aiMessage.text();
            }
        }
        return "未生成可聚合的结果。";
    }

    private String serializeAggregationPayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("AgentAggregatorNode: 聚合输入序列化失败，回退到 toString", e);
            return payload.toString();
        }
    }

    private String resolveSystemPrompt(AgentState state, Map<String, Object> nodeProperties) {
        return AgentUtils.resolveSystemPrompt(
                nodeProperties,
                promptTemplateService::renderTemplate,
                () -> buildTemplateVariables(state, nodeProperties),
                "你是多智能体系统的结果汇总器。请结合专家输出、并行结果与对话历史，给出一致、精炼、可直接返回给用户的综合结论。");
    }

    private Map<String, Object> buildTemplateVariables(AgentState state, Map<String, Object> nodeProperties) {
        Map<String, Object> variables = AgentUtils.buildBaseTemplateVariables(state);
        variables.put("aggregationInput", buildAggregationInput(state, nodeProperties));
        AgentUtils.mergeTemplateVariables(variables, nodeProperties);
        return variables;
    }

    private ChatModelFactory getChatModelFactory() {
        ChatModelFactory factory = chatModelFactoryProvider.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("ChatModelFactory 未初始化");
        }
        return factory;
    }
}
