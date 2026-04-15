package com.lambda.fusion.ai.commons.support.factory;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.commons.support.security.KeyEncryptionService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ChatModel 工厂类
 * 负责根据配置动态构建和缓存 LLM 实例
 */
@Slf4j
@Component
public class ChatModelFactory {

    private LlmModelService llmModelService;
    private final KeyEncryptionService keyEncryptionService;

    public ChatModelFactory(KeyEncryptionService keyEncryptionService) {
        this.keyEncryptionService = keyEncryptionService;
    }

    @Autowired
    @Lazy
    public void setLlmModelService(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    // 缓存模型实例，避免频繁创建连接
    private final Cache<String, ChatModel> chatModelCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    private final Cache<String, StreamingChatModel> streamingChatModelCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * 获取非流式对话模型
     */
    public ChatModel getChatModel(String modelId) {
        if (modelId == null) {
            return getDefaultChatModel();
        }
        return chatModelCache.get(modelId, this::createChatModel);
    }

    /**
     * 获取流式对话模型
     */
    public StreamingChatModel getStreamingChatModel(String modelId) {
        if (modelId == null) {
            return getDefaultStreamingChatModel();
        }
        return streamingChatModelCache.get(modelId, this::createStreamingChatModel);
    }

    public void invalidateModelCache(String modelId) {
        if (modelId == null) {
            return;
        }
        chatModelCache.invalidate(modelId);
        streamingChatModelCache.invalidate(modelId);
        log.info("已清理LLM模型缓存，模型ID: {}", modelId);
    }

    private ChatModel createChatModel(String modelId) {
        LlmModelEntity entity = llmModelService.getById(modelId);
        if (entity == null) {
            throw new RuntimeException("未找到LLM模型配置: " + modelId);
        }
        return buildChatModel(entity);
    }

    private StreamingChatModel createStreamingChatModel(String modelId) {
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
                    throw new AiBusinessException(
                            AiErrorCode.SYSTEM_ERROR, "OpenAI BaseURL未配置，模型ID: " + entity.getId());
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
                    throw new AiBusinessException(
                            AiErrorCode.SYSTEM_ERROR, "Ollama BaseURL未配置，模型ID: " + entity.getId());
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
            default -> throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "不支持的LLM提供商: " + provider);
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
                    throw new AiBusinessException(
                            AiErrorCode.SYSTEM_ERROR, "OpenAI BaseURL未配置，模型ID: " + entity.getId());
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
                    throw new AiBusinessException(
                            AiErrorCode.SYSTEM_ERROR, "Ollama BaseURL未配置，模型ID: " + entity.getId());
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
            default -> throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "不支持的LLM提供商: " + provider);
        };
    }

    private ChatModel getDefaultChatModel() {
        LlmModelEntity def = loadDefaultChatModelEntity();
        return getChatModel(def.getId());
    }

    private StreamingChatModel getDefaultStreamingChatModel() {
        LlmModelEntity def = loadDefaultChatModelEntity();
        return getStreamingChatModel(def.getId());
    }

    private LlmModelEntity loadDefaultChatModelEntity() {
        LambdaQueryChainWrapper<LlmModelEntity> query = llmModelService.lambdaQuery();
        LlmModelEntity def = query.eq(LlmModelEntity::getIsDefault, true)
                .eq(LlmModelEntity::getEnabled, true)
                .eq(LlmModelEntity::getModelType, "CHAT")
                .one();
        if (def == null) {
            throw new AiBusinessException(AiErrorCode.DEFAULT_LLM_MODEL_NOT_CONFIGURED);
        }
        return def;
    }

    /**
     * 验证LLM模型实体
     */
    private void validateLlmModelEntity(LlmModelEntity entity) {
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "LLM模型配置为空");
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_DISABLED, entity.getId());
        }
        if (!"CHAT".equalsIgnoreCase(entity.getModelType())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "LLM模型类型不是CHAT，模型ID: " + entity.getId());
        }
        if (!StringUtils.hasText(entity.getProvider())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "LLM模型提供商未配置，模型ID: " + entity.getId());
        }
        if (!StringUtils.hasText(entity.getModelName())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "LLM模型名称未配置，模型ID: " + entity.getId());
        }
    }

    /**
     * 解密API密钥
     * <p>
     * 使用 KeyEncryptionService 进行安全的密钥解密
     * 支持向后兼容未加密的密钥
     */
    private String validAndGetDecryptApiKey(LlmModelEntity entity) {
        if (!StringUtils.hasText(entity.getApiKeyEncrypted())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "OpenAI API密钥未配置或解密失败，模型ID: " + entity.getId());
        }

        try {
            // 使用密钥加密服务进行解密
            String decryptedKey = keyEncryptionService.decrypt(entity.getApiKeyEncrypted());

            if (!StringUtils.hasText(decryptedKey)) {
                throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "API密钥解密结果为空，模型ID: " + entity.getId());
            }

            return decryptedKey;
        } catch (Exception e) {
            log.error("API密钥解密失败，模型ID: {}", entity.getId(), e);
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "API密钥解密失败，模型ID: " + entity.getId(), e);
        }
    }
}
