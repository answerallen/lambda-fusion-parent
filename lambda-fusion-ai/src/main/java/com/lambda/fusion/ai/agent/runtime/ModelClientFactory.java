package com.lambda.fusion.ai.agent.runtime;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.security.KeyEncryptionService;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AgentScope 模型客户端工厂。
 *
 * <p>取代旧 {@code ChatModelFactory} 产出 langchain4j {@code ChatModel} 的职责，改为产出 AgentScope
 * {@link Model}（{@code io.agentscope.core.model.Model}）。沿用 DB 驱动 + 密钥加密 + Caffeine 缓存的管理面：
 * <ul>
 *   <li>从 {@link LlmModelEntity}（{@code ai_llm_model}）读取 provider/baseUrl/apiKey/modelName；</li>
 *   <li>apiKey 经 {@link KeyEncryptionService} 解密；</li>
 *   <li>按 provider 选择扩展 builder：OPENAI -> {@link OpenAIChatModel}、OLLAMA -> {@link OllamaChatModel}；</li>
 *   <li>按 modelId 缓存（1h / 100），{@link #invalidateModelCache(String)} 失效。</li>
 * </ul>
 *
 * <p><strong>关键差异</strong>：温度/maxTokens 不在 model builder（AgentScope 把连接配置与运行参数分离），
 * 运行参数经 {@code GenerateOptions} 注入 {@code HarnessAgent.builder()}（见 {@link AgentRuntimeServiceImpl}）。
 * 故本工厂只负责"连接配置"。
 *
 * <p>spike 已核实 API：`OpenAIChatModel.builder().apiKey().baseUrl().modelName().stream().build()`、
 * `OllamaChatModel.builder().baseUrl().modelName().build()`、`DashScopeChatModel.builder().apiKey().modelName().stream().build()`（baseUrl 可选）。
 * GEMINI/ANTHROPIC 待 Phase 1 跟进核实 builder。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ModelClientFactory {

    private LlmModelService llmModelService;
    private final KeyEncryptionService keyEncryptionService;

    public ModelClientFactory(KeyEncryptionService keyEncryptionService) {
        this.keyEncryptionService = keyEncryptionService;
    }

    @Autowired
    @Lazy
    public void setLlmModelService(LlmModelService llmModelService) {
        this.llmModelService = llmModelService;
    }

    // 缓存模型客户端实例，避免频繁创建连接（沿用 ChatModelFactory 的策略）
    private final Cache<String, Model> modelCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * 获取模型客户端；modelId 为空时回落到默认 CHAT 模型。
     */
    public Model get(String modelId) {
        if (modelId == null) {
            return getDefault();
        }
        return modelCache.get(modelId, this::create);
    }

    public void invalidateModelCache(String modelId) {
        if (modelId == null) {
            return;
        }
        modelCache.invalidate(modelId);
        log.info("已清理 AgentScope 模型客户端缓存，模型ID: {}", modelId);
    }

    private Model create(String modelId) {
        LlmModelEntity entity = llmModelService.getById(modelId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "未找到LLM模型配置: " + modelId);
        }
        return build(entity);
    }

    private Model getDefault() {
        return get(loadDefaultModelEntity().getId());
    }

    private Model build(LlmModelEntity entity) {
        validate(entity);
        String provider = entity.getProvider().toUpperCase();
        return switch (provider) {
            case "OPENAI" -> {
                String apiKey = decryptApiKey(entity);
                String baseUrl = entity.getBaseUrl();
                if (!StringUtils.hasText(baseUrl)) {
                    throw new AiBusinessException(
                            AiErrorCode.SYSTEM_ERROR, "OpenAI BaseURL未配置，模型ID: " + entity.getId());
                }
                // stream(true) 让模型支持流式；运行参数（温度等）由 HarnessAgent 的 GenerateOptions 注入
                yield OpenAIChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(entity.getModelName()).stream(
                                true)
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
                        .build();
            }
            case "DASHSCOPE" -> {
                // DashScope 有默认 baseUrl（dashscope.aliyuncs.com），仅在 entity 显式配置时覆盖
                String apiKey = decryptApiKey(entity);
                DashScopeChatModel.Builder builder = DashScopeChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(entity.getModelName())
                        .stream(true);
                if (StringUtils.hasText(entity.getBaseUrl())) {
                    builder.baseUrl(entity.getBaseUrl());
                }
                yield builder.build();
            }
            // GEMINI/ANTHROPIC 待 Phase 1 跟进核实各自 builder（spike 核实 openai/ollama/dashscope）
            default ->
                throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "暂不支持的LLM提供商(待Phase1核实builder): " + provider);
        };
    }

    private LlmModelEntity loadDefaultModelEntity() {
        LambdaQueryChainWrapper<LlmModelEntity> query = llmModelService.lambdaQuery();
        List<LlmModelEntity> defaultModels = query.eq(LlmModelEntity::getIsDefault, true)
                .eq(LlmModelEntity::getEnabled, true)
                .list();
        LlmModelEntity def = defaultModels.stream()
                .filter(model -> "CHAT".equalsIgnoreCase(model.getModelType()))
                .findFirst()
                .orElse(null);
        if (def == null) {
            throw new AiBusinessException(AiErrorCode.DEFAULT_LLM_MODEL_NOT_CONFIGURED);
        }
        return def;
    }

    private void validate(LlmModelEntity entity) {
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

    private String decryptApiKey(LlmModelEntity entity) {
        if (!StringUtils.hasText(entity.getApiKeyEncrypted())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "API密钥未配置或解密失败，模型ID: " + entity.getId());
        }
        try {
            String decrypted = keyEncryptionService.decrypt(entity.getApiKeyEncrypted());
            if (!StringUtils.hasText(decrypted)) {
                throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "API密钥解密结果为空，模型ID: " + entity.getId());
            }
            return decrypted;
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("API密钥解密失败，模型ID: {}", entity.getId(), e);
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "API密钥解密失败，模型ID: " + entity.getId(), e);
        }
    }
}
