package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RATE_LIMITER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import com.lambda.fusion.ai.service.PromptTemplateService;
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
 * 主管智能体节点。
 *
 * <p>根据会话历史和候选专家列表做路由决策，返回下一个专家节点，
 * 或选择结束当前工作流。</p>
 */
@Slf4j
@Component
public class SupervisorAgentNode implements AgentNode {

    public static final String NAME = "SUPERVISOR_AGENT";
    public static final String SUPERVISOR_RESULTS_KEY = "supervisorDecisions";
    public static final String LAST_SUPERVISOR_RESULT_KEY = "lastSupervisorDecision";
    private static final String DEFAULT_FINISH_TOKEN = "FINISH";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModelFactory> chatModelFactoryProvider;
    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public SupervisorAgentNode(
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
        log.info("SupervisorAgentNode: 开始进行多智能体路由决策...");

        Map<String, Object> nodeProperties = state.getCurrentNodeProperties();
        List<RouteCandidate> candidates = resolveCandidates(nodeProperties);
        String defaultTarget = AgentUtils.resolveText(nodeProperties, "defaultTarget", "fallbackTarget");
        String finishToken = AgentUtils.resolveText(nodeProperties, "finishToken");
        if (!StringUtils.hasText(finishToken)) {
            finishToken = DEFAULT_FINISH_TOKEN;
        }
        boolean finishOnEnd = !Boolean.FALSE.equals(
                AgentUtils.resolveBoolean(nodeProperties, "finishOnEnd", "finishWhenEndSelected"));

        if (candidates.isEmpty()) {
            log.warn("SupervisorAgentNode: 未配置候选专家，使用默认目标节点 {}", defaultTarget);
            return new ExecutionResult(state, defaultTarget);
        }

        String effectiveModelId = AgentUtils.resolveModelId(state, nodeProperties);
        String systemPrompt = resolveSystemPrompt(state, nodeProperties, candidates, finishToken);

        try {
            DecisionResult result = executeWithResilience(
                    state, effectiveModelId, systemPrompt, candidates, defaultTarget, finishToken);
            recordDecision(state, result);
            if (result.finish()) {
                if (finishOnEnd) {
                    state.setFinished(true);
                }
                return new ExecutionResult(state, AgentGraph.END_NODE);
            }
            return new ExecutionResult(state, result.nextNode());
        } catch (CallNotPermittedException e) {
            log.warn("SupervisorAgentNode: 熔断器已打开，使用默认目标节点 {}", defaultTarget);
            return fallbackDecision(state, defaultTarget, "服务暂时不可用");
        } catch (Throwable e) {
            log.error("SupervisorAgentNode: 路由决策失败", e);
            return fallbackDecision(state, defaultTarget, e.getMessage());
        }
    }

    private DecisionResult executeWithResilience(
            AgentState state,
            String modelId,
            String systemPrompt,
            List<RouteCandidate> candidates,
            String defaultTarget,
            String finishToken)
            throws Throwable {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(LLM_CIRCUIT_BREAKER);
        Retry retry = retryRegistry.retry(LLM_RETRY);
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(LLM_RATE_LIMITER);

        Supplier<DecisionResult> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return doRouteDecision(state, modelId, systemPrompt, candidates, defaultTarget, finishToken);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter, decoratedSupplier);

        return AgentUtils.get(decoratedSupplier);
    }

    private DecisionResult doRouteDecision(
            AgentState state,
            String modelId,
            String systemPrompt,
            List<RouteCandidate> candidates,
            String defaultTarget,
            String finishToken) {
        ChatModel model = getChatModelFactory().getChatModel(modelId);
        ChatRequest request = ChatRequest.builder()
                .messages(buildMessages(state, systemPrompt))
                .build();
        ChatResponse response = model.chat(request);
        String rawDecision = response == null || response.aiMessage() == null
                ? null
                : response.aiMessage().text();
        return normalizeDecision(rawDecision, candidates, defaultTarget, finishToken);
    }

    private List<ChatMessage> buildMessages(AgentState state, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(SystemMessage.from(systemPrompt.trim()));
        }
        if (state.getMessages() != null && !state.getMessages().isEmpty()) {
            messages.addAll(state.getMessages());
        }
        return messages;
    }

    private DecisionResult normalizeDecision(
            String rawDecision, List<RouteCandidate> candidates, String defaultTarget, String finishToken) {
        String normalized = extractDecisionToken(rawDecision);
        if (!StringUtils.hasText(normalized)) {
            return new DecisionResult(defaultTarget, false, rawDecision, "default");
        }
        if (finishToken.equalsIgnoreCase(normalized)) {
            return new DecisionResult(null, true, rawDecision, "finish");
        }
        for (RouteCandidate candidate : candidates) {
            if (candidate.routeId().equalsIgnoreCase(normalized)
                    || candidate.targetNode().equalsIgnoreCase(normalized)) {
                return new DecisionResult(candidate.targetNode(), false, rawDecision, "candidate");
            }
        }
        for (RouteCandidate candidate : candidates) {
            if (rawDecision != null
                    && (rawDecision.contains(candidate.routeId()) || rawDecision.contains(candidate.targetNode()))) {
                return new DecisionResult(candidate.targetNode(), false, rawDecision, "fuzzy");
            }
        }
        return new DecisionResult(defaultTarget, false, rawDecision, "default");
    }

    private String extractDecisionToken(String rawDecision) {
        if (!StringUtils.hasText(rawDecision)) {
            return null;
        }
        String trimmed = rawDecision.trim();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(trimmed, Map.class);
            Object decision = AgentUtils.firstNonNull(payload, "nextNode", "target", "route", "decision");
            if (decision != null) {
                return decision.toString().trim();
            }
        } catch (Exception ignored) {
            // 非 JSON 输出按纯文本处理
        }
        int lineBreak = trimmed.indexOf('\n');
        return lineBreak >= 0 ? trimmed.substring(0, lineBreak).trim() : trimmed;
    }

    @SuppressWarnings("unchecked")
    private void recordDecision(AgentState state, DecisionResult result) {
        if (state.getAttributes() == null) {
            state.setAttributes(new HashMap<>());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", state.getCurrentNodeId());
        payload.put("nextNode", result.nextNode());
        payload.put("finish", result.finish());
        payload.put("rawDecision", result.rawDecision());
        payload.put("decisionSource", result.decisionSource());
        if (result.finish()) {
            payload.put("status", "finished");
        }

        Map<String, Object> decisions = state.getAttributes().get(SUPERVISOR_RESULTS_KEY) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        decisions.put(state.getCurrentNodeId(), payload);
        state.getAttributes().put(SUPERVISOR_RESULTS_KEY, decisions);
        state.getAttributes().put(LAST_SUPERVISOR_RESULT_KEY, payload);
    }

    private ExecutionResult fallbackDecision(AgentState state, String defaultTarget, String detail) {
        DecisionResult result = new DecisionResult(defaultTarget, false, detail, "fallback");
        recordDecision(state, result);
        return new ExecutionResult(state, defaultTarget);
    }

    private List<RouteCandidate> resolveCandidates(Map<String, Object> nodeProperties) {
        Object candidatesObj = AgentUtils.firstNonNull(nodeProperties, "candidates", "workers", "agents", "routes");
        if (!(candidatesObj instanceof List<?> candidateList)) {
            return List.of();
        }
        List<RouteCandidate> candidates = new ArrayList<>();
        for (Object item : candidateList) {
            if (!(item instanceof Map<?, ?> routeMap)) {
                continue;
            }
            String target = AgentUtils.asText(routeMap.get("target"));
            if (!StringUtils.hasText(target)) {
                continue;
            }
            String routeId = AgentUtils.asText(AgentUtils.firstNonNull(castMap(routeMap), "id", "name", "routeId"));
            if (!StringUtils.hasText(routeId)) {
                routeId = target;
            }
            String description = AgentUtils.asText(routeMap.get("description"));
            candidates.add(new RouteCandidate(routeId, target, description));
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        return (Map<String, Object>) source;
    }

    private String resolveSystemPrompt(
            AgentState state, Map<String, Object> nodeProperties, List<RouteCandidate> candidates, String finishToken) {
        String basePrompt = AgentUtils.resolveSystemPrompt(
                nodeProperties,
                promptTemplateService::renderTemplate,
                () -> buildTemplateVariables(state, nodeProperties, candidates, finishToken));
        if (!StringUtils.hasText(basePrompt)) {
            basePrompt = "你是多智能体系统的主管。根据对话内容，从候选专家中选择最适合的下一节点。" + " 只能返回一个路由ID，或在任务已完成时返回 " + finishToken + "。";
        }
        return basePrompt + "\n\n候选路由:\n" + formatCandidates(candidates) + "\n\n输出要求:\n"
                + "1. 只能返回一个 routeId 或 " + finishToken + "\n"
                + "2. 不要输出解释\n"
                + "3. 如果任务已完成或无需继续分派，返回 " + finishToken;
    }

    private String formatCandidates(List<RouteCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        for (RouteCandidate candidate : candidates) {
            builder.append("- routeId=")
                    .append(candidate.routeId())
                    .append(", target=")
                    .append(candidate.targetNode());
            if (StringUtils.hasText(candidate.description())) {
                builder.append(", description=").append(candidate.description());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private Map<String, Object> buildTemplateVariables(
            AgentState state, Map<String, Object> nodeProperties, List<RouteCandidate> candidates, String finishToken) {
        Map<String, Object> variables = AgentUtils.buildBaseTemplateVariables(state);
        variables.put("candidates", candidates);
        variables.put("finishToken", finishToken);
        AgentUtils.mergeTemplateVariables(variables, nodeProperties);
        return variables;
    }

    private record RouteCandidate(String routeId, String targetNode, String description) {}

    private record DecisionResult(String nextNode, boolean finish, String rawDecision, String decisionSource) {}

    private ChatModelFactory getChatModelFactory() {
        ChatModelFactory factory = chatModelFactoryProvider.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("ChatModelFactory 未初始化");
        }
        return factory;
    }
}
