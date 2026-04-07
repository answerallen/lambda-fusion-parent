package com.lambda.fusion.ai.commons.agent;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 图节点：负责向大语言模型请求分析，决定是回复用户还是调用后续Tool。
 * <p>
 * 容错特性：
 * - 重试：失败时自动重试最多3次
 * - 熔断：失败率超过50%时熔断30秒
 * - 降级：熔断时返回友好提示
 */
@Slf4j
@Component
public class LlmProcessingNode implements AgentNode {

    public static final String NAME = "LLM_PROCESSOR";

    private ChatModelFactory chatModelFactory;
    private final AgentToolProvider toolProvider;
    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    public LlmProcessingNode(
            AgentToolProvider toolProvider,
            PromptTemplateService promptTemplateService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry) {
        this.toolProvider = toolProvider;
        this.promptTemplateService = promptTemplateService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
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
    public ExecutionResult execute(AgentState nextState) {
        log.info("LlmProcessingNode: 正在推理决策...");

        Map<String, Object> nodeProperties = nextState.getCurrentNodeProperties();
        String effectiveModelId = resolveModelId(nextState, nodeProperties);
        String systemPrompt = resolveSystemPrompt(nextState, nodeProperties);
        Set<String> allowedTools = resolveToolNames(nodeProperties);

        List<ToolSpecification> tools = allowedTools.isEmpty()
                ? toolProvider.getToolSpecifications()
                : toolProvider.getToolSpecifications(allowedTools);
        StreamingChatResponseHandler handler =
                (StreamingChatResponseHandler) nextState.getAttributes().get("streamHandler");

        try {
            ChatResponse response = executeWithResilience(nextState, effectiveModelId, systemPrompt, tools, handler);
            return handleResponse(nextState, response);
        } catch (CallNotPermittedException e) {
            log.warn("LLM 熔断器已打开，执行降级策略");
            return handleFallback(nextState, "服务暂时不可用，请稍后重试");
        } catch (Throwable e) {
            log.error("LLM 调用失败", e);
            return handleFallback(nextState, "服务调用失败: " + e.getMessage());
        }
    }

    private ChatResponse executeWithResilience(
            AgentState state,
            String modelId,
            String systemPrompt,
            List<ToolSpecification> tools,
            StreamingChatResponseHandler handler)
            throws Throwable {

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(LLM_CIRCUIT_BREAKER);
        Retry retry = retryRegistry.retry(LLM_RETRY);

        // 使用 Resilience4j 的装饰器链
        Supplier<ChatResponse> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return doLlmCall(state, modelId, systemPrompt, tools, handler);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);

        try {
            return decoratedSupplier.get();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof Exception) {
                throw e.getCause();
            }
            throw e;
        }
    }

    private ChatResponse doLlmCall(
            AgentState state,
            String modelId,
            String systemPrompt,
            List<ToolSpecification> tools,
            StreamingChatResponseHandler handler)
            throws Exception {

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(buildMessages(state, systemPrompt));
        if (!tools.isEmpty()) {
            requestBuilder.toolSpecifications(tools);
        }
        ChatRequest request = requestBuilder.build();

        if (handler != null) {
            StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(modelId);
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();

            StreamingChatResponseHandler innerHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    handler.onPartialResponse(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse res) {
                    future.complete(res);
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                    handler.onError(error);
                }
            };

            streamingModel.chat(request, innerHandler);
            return future.join();
        } else {
            ChatModel chatModel = chatModelFactory.getChatModel(modelId);
            return chatModel.chat(request);
        }
    }

    private ExecutionResult handleResponse(AgentState nextState, ChatResponse response) {
        nextState.addMessage(response.aiMessage());

        if (response.tokenUsage() != null) {
            int pTokens = (int) nextState.getAttributes().getOrDefault("promptTokens", 0);
            int cTokens = (int) nextState.getAttributes().getOrDefault("completionTokens", 0);
            nextState
                    .getAttributes()
                    .put("promptTokens", pTokens + response.tokenUsage().inputTokenCount());
            nextState
                    .getAttributes()
                    .put("completionTokens", cTokens + response.tokenUsage().outputTokenCount());
        }

        if (response.aiMessage().hasToolExecutionRequests()) {
            nextState.setPendingToolRequests(response.aiMessage().toolExecutionRequests());
            return new ExecutionResult(nextState, null);
        } else {
            nextState.setFinished(true);
            return new ExecutionResult(nextState, AgentGraph.END_NODE);
        }
    }

    private ExecutionResult handleFallback(AgentState nextState, String fallbackMessage) {
        nextState.addMessage(AiMessage.from(fallbackMessage));
        nextState.setFinished(true);
        nextState.getAttributes().put("fallback", true);
        return new ExecutionResult(nextState, AgentGraph.END_NODE);
    }

    private List<ChatMessage> buildMessages(AgentState nextState, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SystemMessage.from(systemPrompt.trim()));
        }
        if (nextState.getMessages() != null && !nextState.getMessages().isEmpty()) {
            messages.addAll(nextState.getMessages());
        }
        return messages;
    }

    private String resolveSystemPrompt(AgentState nextState, Map<String, Object> nodeProperties) {
        String systemPrompt = resolveString(nodeProperties);
        if (systemPrompt != null) {
            return systemPrompt;
        }
        String promptTemplateId = resolveLong(nodeProperties);
        if (promptTemplateId == null) {
            return null;
        }
        return promptTemplateService.renderTemplate(
                promptTemplateId, buildTemplateVariables(nextState, nodeProperties));
    }

    private String resolveModelId(AgentState nextState, Map<String, Object> nodeProperties) {
        Object configuredModelId = firstNonNull(nodeProperties, "llmModelId", "modelId");
        if (configuredModelId instanceof Number number) {
            return number.toString();
        }
        if (configuredModelId instanceof String value && StrUtil.isNotBlank(value)) {
            try {
                return value;
            } catch (NumberFormatException e) {
                log.warn("LlmProcessingNode: 节点 {} 配置了非法模型ID: {}", nextState.getCurrentNodeId(), value);
            }
        }
        return nextState.getLlmModelId();
    }

    private String resolveLong(Map<String, Object> nodeProperties) {
        Object configuredValue = firstNonNull(nodeProperties, "promptTemplateId", "systemPromptTemplateId");
        if (configuredValue instanceof Number number) {
            return number.toString();
        }
        if (configuredValue instanceof String value && StrUtil.isNotBlank(value)) {
            try {
                return value;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> buildTemplateVariables(AgentState nextState, Map<String, Object> nodeProperties) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("sessionId", nextState.getSessionId());
        variables.put("kbId", nextState.getKbId());
        variables.put("llmModelId", nextState.getLlmModelId());
        variables.put("currentNodeId", nextState.getCurrentNodeId());
        variables.put("currentNodeType", nextState.getCurrentNodeType());
        variables.put("currentNodeProperties", nextState.getCurrentNodeProperties());
        variables.put("graphNodeProperties", nextState.getGraphNodeProperties());
        variables.put("attributes", nextState.getAttributes());
        if (nextState.getAttributes() != null) {
            variables.putAll(nextState.getAttributes());
        }
        Object templateVariables = firstNonNull(nodeProperties, "templateVariables", "promptVariables");
        if (templateVariables instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    variables.put(String.valueOf(key), value);
                }
            });
        }
        List<ChatMessage> messages = nextState.getMessages();
        if (messages != null && !messages.isEmpty()) {
            variables.put("messageCount", messages.size());
            variables.put("lastMessage", messages.getLast());
        }
        return variables;
    }

    private String resolveString(Map<String, Object> nodeProperties) {
        Object value = firstNonNull(nodeProperties, "systemPrompt", "systemMessage");
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Set<String> resolveToolNames(Map<String, Object> nodeProperties) {
        Object value = firstNonNull(nodeProperties, "allowedTools", "toolNames", "tools");
        if (value == null) {
            return Set.of();
        }
        Set<String> toolNames = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !item.toString().isBlank()) {
                    toolNames.add(item.toString().trim());
                }
            }
        } else {
            for (String item : value.toString().split(",")) {
                if (!item.isBlank()) {
                    toolNames.add(item.trim());
                }
            }
        }
        return toolNames;
    }

    private Object firstNonNull(Map<String, Object> nodeProperties, String... keys) {
        for (String key : keys) {
            Object value = nodeProperties.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
