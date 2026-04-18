package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_CIRCUIT_BREAKER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RATE_LIMITER;
import static com.lambda.fusion.ai.AiConfigure.LlmResilienceConfig.LLM_RETRY;

import com.lambda.fusion.ai.commons.agent.*;
import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
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
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 图节点：负责向大语言模型请求分析，决定是回复用户还是调用后续Tool。
 * <p>
 * 容错特性：
 * - 限流：每分钟最多60次请求，超限自动排队等待
 * - 重试：失败时自动重试最多3次
 * - 熔断：失败率超过50%时熔断30秒
 * - 降级：熔断时返回友好提示
 */
@Slf4j
@Component
public class LlmProcessingNode implements AgentNode {

    public static final String NAME = "LLM_PROCESSOR";

    private final ObjectProvider<ChatModelFactory> chatModelFactoryProvider;
    private final AgentToolProvider toolProvider;
    private final PromptTemplateService promptTemplateService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    public LlmProcessingNode(
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
    public ExecutionResult execute(AgentState nextState) {
        log.info("LlmProcessingNode: 正在推理决策...");

        Map<String, Object> nodeProperties = nextState.getCurrentNodeProperties();
        String effectiveModelId = AgentUtils.resolveModelId(nextState, nodeProperties);
        String systemPrompt =
                AgentUtils.resolveSystemPrompt(nodeProperties, promptTemplateService::renderTemplate, () -> {
                    Map<String, Object> variables = AgentUtils.buildBaseTemplateVariables(nextState);
                    AgentUtils.mergeTemplateVariables(variables, nodeProperties);
                    return variables;
                });
        Set<String> allowedTools =
                AgentUtils.resolveToolNames(nodeProperties, toolProvider, "allowedTools", "toolNames", "tools");

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
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(LLM_RATE_LIMITER);

        // 使用 Resilience4j 的装饰器链：RateLimiter -> CircuitBreaker -> Retry
        // 顺序说明：
        // 1. RateLimiter 在最外层，确保请求先被限流排队
        // 2. CircuitBreaker 在中间，监控失败率并熔断
        // 3. Retry 在最内层，失败时自动重试
        Supplier<ChatResponse> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return doLlmCall(state, modelId, systemPrompt, tools, handler);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 如果是流式输出（handler != null），为了防止流式传输中途失败导致无状态重试产生重复脏数据，不启用 Retry 机制。
        if (handler == null) {
            decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        }
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
            StreamingChatModel streamingModel = getChatModelFactory().getStreamingChatModel(modelId);
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();

            StreamingChatResponseHandler innerHandler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    handler.onPartialResponse(token);
                }

                @Override
                public void onCompleteResponse(ChatResponse res) {
                    future.complete(res);
                    if (res != null
                            && res.aiMessage() != null
                            && !res.aiMessage().hasToolExecutionRequests()) {
                        handler.onCompleteResponse(res);
                    }
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
            ChatModel chatModel = getChatModelFactory().getChatModel(modelId);
            return chatModel.chat(request);
        }
    }

    private ChatModelFactory getChatModelFactory() {
        ChatModelFactory factory = chatModelFactoryProvider.getIfAvailable();
        if (factory == null) {
            throw new IllegalStateException("ChatModelFactory 未初始化");
        }
        return factory;
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
}
