package com.lambda.fusion.ai.support.factory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.service.LlmModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
        validateLlmModelEntity(entity);
        String provider = entity.getProvider().toUpperCase();

        return switch (provider) {
            case "OPENAI" -> {
                String apiKey = validAndGetDecryptApiKey(entity);
                String baseUrl = entity.getBaseUrl();
                if (!StringUtils.hasText(baseUrl)) {
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                            "OpenAI BaseURL未配置，模型ID: " + entity.getId());
                }
                yield OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            case "OLLAMA" -> {
                String baseUrl = entity.getBaseUrl();
                if (!StringUtils.hasText(baseUrl)) {
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                            "Ollama BaseURL未配置，模型ID: " + entity.getId());
                }
                yield OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            default -> throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                    "不支持的LLM提供商: " + provider);
        };
    }

    private StreamingChatModel buildStreamingChatModel(LlmModelEntity entity) {
        validateLlmModelEntity(entity);
        String provider = entity.getProvider().toUpperCase();

        return switch (provider) {
            case "OPENAI" -> {
                String apiKey = validAndGetDecryptApiKey(entity);
                String baseUrl = entity.getBaseUrl();
                if (!StringUtils.hasText(baseUrl)) {
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                            "OpenAI BaseURL未配置，模型ID: " + entity.getId());
                }
                yield OpenAiStreamingChatModel.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            case "OLLAMA" -> {
                String baseUrl = entity.getBaseUrl();
                if (!StringUtils.hasText(baseUrl)) {
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                            "Ollama BaseURL未配置，模型ID: " + entity.getId());
                }
                yield OllamaStreamingChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(entity.getModelName())
                        .temperature(
                                entity.getDefaultTemperature() != null
                                        ? entity.getDefaultTemperature().doubleValue()
                                        : 0.7)
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }
            default -> throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                    "不支持的LLM提供商: " + provider);
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
        throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "未配置默认LLM模型");
    }

    /**
     * 验证LLM模型实体
     */
    private void validateLlmModelEntity(LlmModelEntity entity) {
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "LLM模型配置为空");
        }
        if (!StringUtils.hasText(entity.getProvider())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                    "LLM模型提供商未配置，模型ID: " + entity.getId());
        }
        if (!StringUtils.hasText(entity.getModelName())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                    "LLM模型名称未配置，模型ID: " + entity.getId());
        }
    }

    /**
     * 解密API密钥
     * <p>
     * TODO: 实现实际的密钥解密逻辑
     * 当前返回原值，需要集成密钥管理服务
     */
    private String validAndGetDecryptApiKey(LlmModelEntity entity) {
        if (!StringUtils.hasText(entity.getApiKeyEncrypted())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR,
                    "OpenAI API密钥未配置或解密失败，模型ID: " + entity.getId());
        }
        // TODO: 调用密钥解密服务
        // return keyDecryptionService.decrypt(encryptedKey);

        // 临时实现：直接返回（需要后续实现真正的解密）
        log.warn("API密钥解密未实现，使用原值。请实现密钥解密逻辑");
        return entity.getApiKeyEncrypted();
    }
}
