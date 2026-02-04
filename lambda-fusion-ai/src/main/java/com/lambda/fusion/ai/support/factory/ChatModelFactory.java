package com.lambda.fusion.ai.support.factory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.service.LlmModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ChatModel 工厂类
 * 负责根据配置动态构建和缓存 LLM 实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final LlmModelService llmModelService;

    // 缓存模型实例，避免频繁创建连接
    private final Cache<Long, ChatModel> chatModelCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    private final Cache<Long, StreamingChatModel> streamingChatModelCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * 获取非流式对话模型
     */
    public ChatModel getChatModel(Long modelId) {
        if (modelId == null) {
            return getDefaultChatModel();
        }
        return chatModelCache.get(modelId, this::createChatModel);
    }

    /**
     * 获取流式对话模型
     */
    public StreamingChatModel getStreamingChatModel(Long modelId) {
        if (modelId == null) {
            return getDefaultStreamingChatModel();
        }
        return streamingChatModelCache.get(modelId, this::createStreamingChatModel);
    }

    private ChatModel createChatModel(Long modelId) {
        LlmModelEntity entity = llmModelService.getById(modelId);
        if (entity == null) {
            throw new RuntimeException("未找到LLM模型配置: " + modelId);
        }
        return buildChatModel(entity);
    }

    private StreamingChatModel createStreamingChatModel(Long modelId) {
        LlmModelEntity entity = llmModelService.getById(modelId);
        if (entity == null) {
            throw new RuntimeException("未找到LLM模型配置: " + modelId);
        }
        return buildStreamingChatModel(entity);
    }

    private ChatModel buildChatModel(LlmModelEntity entity) {
        String provider = entity.getProvider().toUpperCase();
        return switch (provider) {
            case "OPENAI" ->
                OpenAiChatModel.builder()
                        .apiKey(entity.getApiKeyEncrypted()) // 暂未解密
                        .baseUrl(entity.getBaseUrl())
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            case "OLLAMA" ->
                OllamaChatModel.builder()
                        .baseUrl(entity.getBaseUrl())
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            default -> throw new UnsupportedOperationException("不支持的提供商: " + provider);
        };
    }

    private StreamingChatModel buildStreamingChatModel(LlmModelEntity entity) {
        String provider = entity.getProvider().toUpperCase();
        return switch (provider) {
            case "OPENAI" ->
                OpenAiStreamingChatModel.builder()
                        .apiKey(entity.getApiKeyEncrypted())
                        .baseUrl(entity.getBaseUrl())
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            case "OLLAMA" ->
                OllamaStreamingChatModel.builder()
                        .baseUrl(entity.getBaseUrl())
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            default -> throw new UnsupportedOperationException("不支持的提供商: " + provider);
        };
    }

    private ChatModel getDefaultChatModel() {
        // 简单策略：查询 isDefault=true 的模型
        LlmModelEntity def = llmModelService
                .lambdaQuery()
                .eq(LlmModelEntity::getIsDefault, true)
                .one();
        if (def != null) {
            return getChatModel(def.getId());
        }
        throw new RuntimeException("未配置默认模型");
    }

    private StreamingChatModel getDefaultStreamingChatModel() {
        LlmModelEntity def = llmModelService
                .lambdaQuery()
                .eq(LlmModelEntity::getIsDefault, true)
                .one();
        if (def != null) {
            return getStreamingChatModel(def.getId());
        }
        throw new RuntimeException("未配置默认模型");
    }
}
