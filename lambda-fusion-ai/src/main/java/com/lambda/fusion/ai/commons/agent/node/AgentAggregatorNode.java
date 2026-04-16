package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RATE_LIMITER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import com.lambda.fusion.ai.service.PromptTemplateService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private ChatModelFactory chatModelFactory;

    public AgentAggregatorNode(
            PromptTemplateService promptTemplateService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.promptTemplateService = promptTemplateService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Autowired
    @Lazy
    public void setChatModelFactory(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        log.info("AgentAggregatorNode: 开始聚合多智能体输出...");

        Map<String, Object> nodeProperties = state.getCurrentNodeProperties();
        String effectiveModelId = resolveModelId(state, nodeProperties);
        String systemPrompt = resolveSystemPrompt(state, nodeProperties);
        String aggregationInput = buildAggregationInput(state, nodeProperties);
        String outputKey = resolveText(nodeProperties, "outputKey", "resultKey", "attributeKey");
        if (!StringUtils.hasText(outputKey)) {
            outputKey = "aggregatedResult";
        }
        String nextNode = resolveText(nodeProperties, "nextNode", "afterNode");
        boolean appendMessage =
                !Boolean.FALSE.equals(resolveBoolean(nodeProperties, "appendMessage", "writeMessage", "emitMessage"));
        boolean finishOnResponse = !Boolean.FALSE.equals(
                resolveBoolean(nodeProperties, "finishOnResponse", "markFinished", "finishWhenDone"));

        try {
            String answer = executeWithResilience(state, effectiveModelId, systemPrompt, aggregationInput);
            applyAggregationResult(state, answer, outputKey, appendMessage, finishOnResponse);
            return new ExecutionResult(state, finishOnResponse ? AgentGraph.END_NODE : nextNode);
        } catch (CallNotPermittedException e) {
            log.warn("AgentAggregatorNode: 熔断器已打开，降级为本地摘要");
            String fallback = buildFallbackSummary(state);
            applyAggregationResult(state, fallback, outputKey, appendMessage, finishOnResponse);
            return new ExecutionResult(state, finishOnResponse ? AgentGraph.END_NODE : nextNode);
        } catch (Throwable e) {
            log.error("AgentAggregatorNode: 聚合失败", e);
            String fallback = buildFallbackSummary(state);
            applyAggregationResult(state, fallback, outputKey, appendMessage, finishOnResponse);
            return new ExecutionResult(state, finishOnResponse ? AgentGraph.END_NODE : nextNode);
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

        try {
            return decoratedSupplier.get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception) {
                throw e.getCause();
            }
            throw e;
        }
    }

    private String doAggregate(AgentState state, String modelId, String systemPrompt, String aggregationInput)
            throws Exception {
        ChatModel model = chatModelFactory.getChatModel(modelId);
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

        @SuppressWarnings("unchecked")
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

        if (Boolean.FALSE != resolveBoolean(nodeProperties, "includeMessages", "withMessages")) {
            payload.put("messages", state.getMessages() == null ? List.of() : new ArrayList<>(state.getMessages()));
        }
        if (state.getAttributes() != null) {
            if (Boolean.FALSE != resolveBoolean(nodeProperties, "includeParallelResults", "withParallelResults")) {
                Object parallelResults = state.getAttributes().get("__parallel_results__");
                if (parallelResults != null) {
                    payload.put("parallelResults", parallelResults);
                }
            }
            if (Boolean.FALSE != resolveBoolean(nodeProperties, "includeReactAgentResults", "withReactAgentResults")) {
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
        return payload.toString();
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

    private String resolveSystemPrompt(AgentState state, Map<String, Object> nodeProperties) {
        String systemPrompt = resolveText(nodeProperties, "systemPrompt", "systemMessage");
        if (StringUtils.hasText(systemPrompt)) {
            return systemPrompt;
        }
        String templateId = resolveTemplateId(nodeProperties);
        if (templateId != null) {
            return promptTemplateService.renderTemplate(templateId, buildTemplateVariables(state, nodeProperties));
        }
        return "你是多智能体系统的结果汇总器。请结合专家输出、并行结果与对话历史，" + "给出一致、精炼、可直接返回给用户的综合结论。";
    }

    private Map<String, Object> buildTemplateVariables(AgentState state, Map<String, Object> nodeProperties) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("sessionId", state.getSessionId());
        variables.put("kbId", state.getKbId());
        variables.put("llmModelId", state.getLlmModelId());
        variables.put("currentNodeId", state.getCurrentNodeId());
        variables.put("currentNodeProperties", state.getCurrentNodeProperties());
        variables.put("attributes", state.getAttributes());
        variables.put("aggregationInput", buildAggregationInput(state, nodeProperties));
        if (state.getAttributes() != null) {
            variables.putAll(state.getAttributes());
        }
        Object templateVariables = AgentUtils.firstNonNull(nodeProperties, "templateVariables", "promptVariables");
        if (templateVariables instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    variables.put(String.valueOf(key), value);
                }
            });
        }
        return variables;
    }

    private String resolveModelId(AgentState state, Map<String, Object> nodeProperties) {
        Object configuredModelId = AgentUtils.firstNonNull(nodeProperties, "llmModelId", "modelId");
        if (configuredModelId instanceof Number number) {
            return number.toString();
        }
        if (configuredModelId instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return state.getLlmModelId();
    }

    private String resolveTemplateId(Map<String, Object> nodeProperties) {
        Object configuredValue = AgentUtils.firstNonNull(nodeProperties, "promptTemplateId", "systemPromptTemplateId");
        if (configuredValue instanceof Number number) {
            return number.toString();
        }
        if (configuredValue instanceof String value && StrUtil.isNotBlank(value)) {
            return value;
        }
        return null;
    }

    private String resolveText(Map<String, Object> nodeProperties, String... keys) {
        Object value = AgentUtils.firstNonNull(nodeProperties, keys);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean resolveBoolean(Map<String, Object> nodeProperties, String... keys) {
        Object value = AgentUtils.firstNonNull(nodeProperties, keys);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }
}
