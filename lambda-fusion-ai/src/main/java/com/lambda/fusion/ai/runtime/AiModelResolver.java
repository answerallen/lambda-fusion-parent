package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.ProviderType;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.llm.service.LlmProviderService;
import com.lambda.fusion.ai.security.KeyEncryptionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行时模型解析器：按 modelId 从数据库加载模型与提供方配置，解密 API Key，
 * 构建对应的 AgentScope {@link Model} 客户端。
 *
 * <p>当前为无缓存实现——{@code AiAgentFactory} 在应用维度缓存 {@code HarnessAgent}
 * （其中持有 {@code Model}），配置变更时由 AgentFactory 失效缓存即可重建。
 *
 * <p>实现 {@link Function} 以便后续可直接作为 {@code HarnessAgent.Builder.modelResolver} 注入。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AiModelResolver implements Function<String, Model> {

    private final LlmModelService llmModelService;
    private final LlmProviderService llmProviderService;
    private final KeyEncryptionService keyEncryptionService;

    @Override
    public Model apply(String modelId) {
        LlmModelEntity model = llmModelService.loadById(modelId);
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_DISABLED, modelId);
        }
        LlmProviderEntity provider = llmProviderService.loadById(model.getProviderId());
        if (Boolean.FALSE.equals(provider.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_DISABLED, provider.getId());
        }
        return buildModel(model, provider);
    }

    private Model buildModel(LlmModelEntity model, LlmProviderEntity provider) {
        ProviderType type = ProviderType.of(provider.getProviderType());
        if (type == null) {
            throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_TYPE_NOT_SUPPORTED, provider.getProviderType());
        }
        String apiKey = keyEncryptionService.decrypt(provider.getApiKeyEncrypted());
        switch (type) {
            case DASHSCOPE:
                return DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl())
                        .stream(true)
                        .build();
            case OPENAI:
                return OpenAIChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl())
                        .stream(true)
                        .build();
            case OLLAMA:
                return OllamaChatModel.builder()
                        .modelName(model.getModelName())
                        .baseUrl(provider.getBaseUrl())
                        .build();
            default:
                throw new AiBusinessException(AiErrorCode.LLM_PROVIDER_TYPE_NOT_SUPPORTED, provider.getProviderType());
        }
    }
}
