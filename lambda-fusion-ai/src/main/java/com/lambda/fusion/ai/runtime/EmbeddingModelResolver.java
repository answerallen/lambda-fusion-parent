package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.ModelType;
import com.lambda.fusion.ai.AiConstants.ProviderType;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.llm.service.LlmProviderService;
import com.lambda.fusion.ai.security.KeyEncryptionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行时嵌入模型解析器：按 modelId 从数据库加载 EMBEDDING 类型模型与提供方配置，
 * 解密 API Key，构建对应的 AgentScope {@link EmbeddingModel} 客户端。
 *
 * <p>平行于 {@link ModelResolver}（ChatModel）。无缓存——嵌入模型实例由
 * {@code SimpleKnowledgeAdapter} 按知识库维度缓存持有。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class EmbeddingModelResolver {

    private final LlmModelService llmModelService;
    private final LlmProviderService llmProviderService;
    private final KeyEncryptionService keyEncryptionService;

    /**
     * 解析嵌入模型。
     *
     * @param modelId 模型ID（必须为已启用的 EMBEDDING 类型模型）
     * @param dimensions 向量维度（空则由模型默认）
     * @return AgentScope 嵌入模型客户端
     */
    public EmbeddingModel resolve(String modelId, Integer dimensions) {
        LlmModelEntity model = llmModelService.loadById(modelId);
        if (model.getModelType() != ModelType.EMBEDDING) {
            throw new AiBusinessException(AiErrorCode.KB_EMBEDDING_MODEL_INVALID, modelId);
        }
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_DISABLED, modelId);
        }
        LlmProviderEntity provider = llmProviderService.loadById(model.getProviderId());
        if (Boolean.FALSE.equals(provider.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_DISABLED, provider.getId());
        }
        return buildModel(model, provider, dimensions);
    }

    private EmbeddingModel buildModel(LlmModelEntity model, LlmProviderEntity provider, Integer dimensions) {
        ProviderType type = ProviderType.of(provider.getProviderType());
        if (type == null) {
            throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_TYPE_NOT_SUPPORTED, provider.getProviderType());
        }
        String apiKey = keyEncryptionService.decrypt(provider.getApiKeyEncrypted());
        int dims = dimensions != null ? dimensions : 0;
        switch (type) {
            case DASHSCOPE:
                var dashscope = DashScopeTextEmbedding.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl());
                if (dimensions != null) {
                    dashscope.dimensions(dims);
                }
                return dashscope.build();
            case OPENAI:
                var openai = OpenAITextEmbedding.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl());
                if (dimensions != null) {
                    openai.dimensions(dims);
                }
                return openai.build();
            case OLLAMA:
                var ollama = OllamaTextEmbedding.builder()
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl());
                if (dimensions != null) {
                    ollama.dimensions(dims);
                }
                return ollama.build();
            default:
                throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_TYPE_NOT_SUPPORTED, provider.getProviderType());
        }
    }
}
