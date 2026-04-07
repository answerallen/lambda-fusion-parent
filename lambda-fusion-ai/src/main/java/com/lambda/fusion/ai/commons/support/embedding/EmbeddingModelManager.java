package com.lambda.fusion.ai.commons.support.embedding;

import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * EmbeddingModel 动态管理器
 * <p>
 * 基于 LlmModelEntity 管理多个 EmbeddingModel 实例，支持：
 * 1. 动态创建和缓存 EmbeddingModel
 * 2. 根据 modelId 获取对应的模型
 * 3. 获取默认 EmbeddingModel
 * </p>
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelManager {

    private final LlmModelMapper llmModelMapper;
    private final com.lambda.fusion.ai.commons.support.security.KeyEncryptionService keyEncryptionService;

    /**
     * EmbeddingModel 缓存
     * key: modelId, value: EmbeddingModel
     */
    private final Map<String, EmbeddingModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 默认模型ID缓存，避免每次调用 getDefaultModel() 都查询数据库
     */
    private volatile String defaultModelId = null;

    /**
     * 获取 EmbeddingModel
     *
     * @param modelId 模型ID
     * @return EmbeddingModel
     * @throws IllegalArgumentException 如果模型不存在或不是 EMBEDDING 类型
     */
    public EmbeddingModel getModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            log.debug("ModelId is empty, returning default model");
            return getDefaultModel();
        }

        // 先从缓存获取
        EmbeddingModel cachedModel = modelCache.get(modelId);
        if (cachedModel != null) {
            log.debug("Returning cached EmbeddingModel for modelId: {}", modelId);
            return cachedModel;
        }

        // 从数据库加载
        LlmModelEntity modelEntity = llmModelMapper.selectByModelId(modelId);
        if (modelEntity == null) {
            log.warn("Embedding model not found for modelId: {}, using default", modelId);
            return getDefaultModel();
        }

        if (!"EMBEDDING".equalsIgnoreCase(modelEntity.getModelType())) {
            log.error("Model {} is not an EMBEDDING model, type: {}", modelId, modelEntity.getModelType());
            throw new IllegalArgumentException("Model " + modelId + " is not an EMBEDDING model");
        }

        // 创建并缓存模型
        EmbeddingModel model = createModel(modelEntity);
        modelCache.put(modelId, model);
        log.info("Created and cached EmbeddingModel for modelId: {}", modelId);

        return model;
    }

    /**
     * 获取默认的 EmbeddingModel
     * <p>
     * 优先使用缓存的 defaultModelId，避免遍历缓存执行 N 次数据库查询。
     *
     * @return 默认 EmbeddingModel
     * @throws IllegalStateException 如果没有默认模型
     */
    public EmbeddingModel getDefaultModel() {
        // 优先使用缓存的 defaultModelId
        if (defaultModelId != null && modelCache.containsKey(defaultModelId)) {
            log.debug("Returning cached default EmbeddingModel: {}", defaultModelId);
            return modelCache.get(defaultModelId);
        }

        // 从数据库加载默认模型
        LlmModelEntity defaultModel = llmModelMapper.selectDefaultModel("EMBEDDING");
        if (defaultModel == null) {
            log.error("No default EMBEDDING model configured");
            throw new IllegalStateException("No default EMBEDDING model configured");
        }

        // 缓存 defaultModelId
        defaultModelId = defaultModel.getModelId();

        // 创建并缓存模型
        EmbeddingModel model = modelCache.computeIfAbsent(defaultModelId, id -> {
            log.info("Created and cached default EmbeddingModel: {}", id);
            return createModel(defaultModel);
        });

        return model;
    }

    /**
     * 根据知识库配置获取 EmbeddingModel
     *
     * @param embeddingModel 模型ID或模型名称
     * @return EmbeddingModel
     */
    public EmbeddingModel getModelByKnowledgeBase(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isEmpty()) {
            return getDefaultModel();
        }

        // 先尝试作为 modelId 获取
        try {
            return getModel(embeddingModel);
        } catch (IllegalArgumentException e) {
            // 如果不是 modelId，尝试从数据库查找匹配的模型名称
            log.debug("Not a modelId, trying to find by model name: {}", embeddingModel);
        }

        // 尝试查找匹配的模型
        LlmModelEntity matchedModel = findModelByName(embeddingModel);
        if (matchedModel != null) {
            return getModel(matchedModel.getModelId());
        }

        log.warn("No matching embedding model found for: {}, using default", embeddingModel);
        return getDefaultModel();
    }

    /**
     * 创建 EmbeddingModel 实例
     *
     * @param entity 模型配置实体
     * @return EmbeddingModel
     */
    private EmbeddingModel createModel(LlmModelEntity entity) {
        String provider = entity.getProvider();

        if ("OPENAI".equalsIgnoreCase(provider)) {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(decryptApiKey(entity.getApiKeyEncrypted()))
                    .modelName(entity.getModelName())
                    .baseUrl(entity.getBaseUrl())
                    .build();
        }

        // TODO: 支持其他提供商（AZURE_OPENAI、OLLAMA 等）

        throw new IllegalArgumentException("Unsupported embedding model provider: " + provider);
    }

    /**
     * 根据模型名称查找模型
     *
     * @param modelName 模型名称
     * @return LlmModelEntity
     */
    private LlmModelEntity findModelByName(String modelName) {
        // 查询所有启用的 EMBEDDING 模型，匹配 modelName
        return llmModelMapper.selectEnabledModels("EMBEDDING").stream()
                .filter(m -> modelName.equalsIgnoreCase(m.getModelName())
                        || modelName.equalsIgnoreCase(m.getName())
                        || modelName.equalsIgnoreCase(m.getDisplayName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解密 API Key
     *
     * @param encryptedKey 加密的 API Key
     * @return 解密后的 API Key
     */
    private String decryptApiKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isEmpty()) {
            return encryptedKey;
        }
        return keyEncryptionService.decrypt(encryptedKey);
    }

    /**
     * 清除缓存
     *
     * @param modelId 模型ID
     */
    public void clearCache(String modelId) {
        if (modelId != null) {
            modelCache.remove(modelId);
            // 如果清除的是默认模型，重置 defaultModelId
            if (modelId.equals(defaultModelId)) {
                defaultModelId = null;
            }
            log.info("Cleared EmbeddingModel cache for modelId: {}", modelId);
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        modelCache.clear();
        defaultModelId = null;
        log.info("Cleared all EmbeddingModel cache");
    }

    /**
     * 重新加载模型
     *
     * @param modelId 模型ID
     * @return 重新加载后的 EmbeddingModel
     */
    public EmbeddingModel reloadModel(String modelId) {
        clearCache(modelId);
        return getModel(modelId);
    }
}
