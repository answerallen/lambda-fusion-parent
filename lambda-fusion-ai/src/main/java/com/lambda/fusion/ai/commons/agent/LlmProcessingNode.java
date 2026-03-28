package com.lambda.fusion.ai.commons.agent;

import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 图节点：负责向大语言模型请求分析，决定是回复用户还是调用后续Tool。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProcessingNode implements AgentNode {

    public static final String NAME = "LLM_PROCESSOR";

    private final ChatModelFactory chatModelFactory;
    private final AgentToolProvider toolProvider;
    private final PromptTemplateService promptTemplateService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ExecutionResult execute(AgentState nextState) {
        log.info("LlmProcessingNode: 正在推理决策...");

        Map<String, Object> nodeProperties = nextState.getCurrentNodeProperties();
        Long effectiveModelId = resolveModelId(nextState, nodeProperties);
        String systemPrompt = resolveSystemPrompt(nextState, nodeProperties);
        Set<String> allowedTools = resolveToolNames(nodeProperties, "allowedTools", "toolNames", "tools");

        List<ToolSpecification> tools = allowedTools.isEmpty()
                ? toolProvider.getToolSpecifications()
                : toolProvider.getToolSpecifications(allowedTools);
        StreamingChatResponseHandler handler =
                (StreamingChatResponseHandler) nextState.getAttributes().get("streamHandler");

        ChatResponse response;

        ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(buildMessages(nextState, systemPrompt));
        if (!tools.isEmpty()) {
            requestBuilder.toolSpecifications(tools);
        }
        ChatRequest request = requestBuilder.build();

        if (handler != null) {
            StreamingChatModel streamingModel = chatModelFactory.getStreamingChatModel(effectiveModelId);
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

            try {
                response = future.join();
            } catch (Exception e) {
                log.error("LlmProcessingNode 异步流被中断", e);
                nextState.setFinished(true);
                return new ExecutionResult(nextState, AgentGraph.END_NODE);
            }
        } else {
            ChatModel chatModel = chatModelFactory.getChatModel(effectiveModelId);
            response = chatModel.chat(request);
        }

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
        String systemPrompt = resolveString(nodeProperties, "systemPrompt", "systemMessage");
        if (systemPrompt != null) {
            return systemPrompt;
        }
        Long promptTemplateId = resolveLong(nodeProperties, "promptTemplateId", "systemPromptTemplateId");
        if (promptTemplateId == null) {
            return null;
        }
        return promptTemplateService.renderTemplate(
                promptTemplateId, buildTemplateVariables(nextState, nodeProperties));
    }

    private Long resolveModelId(AgentState nextState, Map<String, Object> nodeProperties) {
        Object configuredModelId = firstNonNull(nodeProperties, "llmModelId", "modelId");
        if (configuredModelId instanceof Number number) {
            return number.longValue();
        }
        if (configuredModelId instanceof String value && !value.isBlank()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                log.warn("LlmProcessingNode: 节点 {} 配置了非法模型ID: {}", nextState.getCurrentNodeId(), value);
            }
        }
        return nextState.getLlmModelId();
    }

    private Long resolveLong(Map<String, Object> nodeProperties, String... keys) {
        Object configuredValue = firstNonNull(nodeProperties, keys);
        if (configuredValue instanceof Number number) {
            return number.longValue();
        }
        if (configuredValue instanceof String value && !value.isBlank()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
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
            variables.put("lastMessage", messages.get(messages.size() - 1));
        }
        return variables;
    }

    private String resolveString(Map<String, Object> nodeProperties, String... keys) {
        Object value = firstNonNull(nodeProperties, keys);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Set<String> resolveToolNames(Map<String, Object> nodeProperties, String... keys) {
        Object value = firstNonNull(nodeProperties, keys);
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
