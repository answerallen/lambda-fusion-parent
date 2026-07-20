package com.lambda.fusion.ai.agent.runtime;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.security.KeyEncryptionService;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.store.PgVectorStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 知识库工厂：按 kbId 构造 {@link SimpleKnowledge}（embedding 模型按 provider 选 + {@link PgVectorStore}
 * 复用 ai-postgres），Caffeine 缓存 1h/50。多 KB 返回 {@code List<SimpleKnowledge>}，跨 KB 合并由
 * {@link KnowledgeRetrievalTool} 负责。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeFactory {

    private static final int DEFAULT_DIMENSIONS = 1536;
    private static final String DEFAULT_VECTOR_TABLE = "ai_vector_store_default";
    private static final String DS_PROPERTY_PREFIX = "spring.datasource.dynamic.datasource.";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final LlmModelService llmModelService;
    private final KeyEncryptionService keyEncryptionService;
    private final AiProperties aiProperties;
    private final Environment environment;

    private final Cache<String, SimpleKnowledge> knowledgeCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(50)
            .<String, SimpleKnowledge>removalListener((key, value, cause) -> closeSilently(value, String.valueOf(key)))
            .build();

    /** 按 kbId 获取 {@link SimpleKnowledge}（含缓存）；kbId 为空返回 null（无 RAG）。 */
    public SimpleKnowledge get(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            return null;
        }
        return knowledgeCache.get(kbId, this::build);
    }

    /**
     * 按 kbIds 获取 {@link SimpleKnowledge} 列表（多 KB 检索）：单 KB 返单元素列表，多 KB 返多元素；
     * kbIds 为空返回空列表。单个 KB 装配失败跳过（不阻断），全部失败返回空列表（agent 无 RAG）。
     * 跨 KB 合并由 {@link KnowledgeRetrievalTool} 在应用层完成。
     */
    public List<SimpleKnowledge> get(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        return kbIds.stream().map(this::getOrNull).filter(Objects::nonNull).toList();
    }

    private SimpleKnowledge getOrNull(String kbId) {
        try {
            return get(kbId);
        } catch (Exception e) {
            log.warn("KnowledgeFactory: 单 KB 装配失败，跳过，kbId={}", kbId, e.getMessage());
            return null;
        }
    }

    public void invalidate(String kbId) {
        if (kbId != null) {
            knowledgeCache.invalidate(kbId);
            log.info("KnowledgeFactory: 已清理知识库缓存，kbId: {}", kbId);
        }
    }

    /** 失效全部 Knowledge 缓存（embedding 模型配置变更等全局场景）。 */
    public void invalidateAll() {
        knowledgeCache.invalidateAll();
        log.info("KnowledgeFactory: 已清理全部知识库缓存");
    }

    // ==================== 私有 ====================

    private SimpleKnowledge build(String kbId) {
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在: " + kbId);
        }
        EmbeddingModel embeddingModel = buildEmbeddingModel(kb);
        PgVectorStore store = buildPgVectorStore(kb);
        log.info(
                "KnowledgeFactory: 构造 SimpleKnowledge，kbId={} table={} dims={}",
                kbId,
                store.getTableName(),
                store.getDimensions());
        return SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
    }

    private EmbeddingModel buildEmbeddingModel(KnowledgeBaseEntity kb) {
        if (!StringUtils.hasText(kb.getEmbeddingModel())) {
            throw new AiBusinessException(AiErrorCode.EMBEDDING_FAILED, "知识库未配置 embedding 模型，kbId: " + kb.getId());
        }
        LlmModelEntity model = llmModelService.getById(kb.getEmbeddingModel());
        if (model == null) {
            throw new AiBusinessException(AiErrorCode.EMBEDDING_FAILED, "embedding 模型不存在: " + kb.getEmbeddingModel());
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_DISABLED, model.getId());
        }
        int dimensions = kb.getEmbeddingDimension() != null ? kb.getEmbeddingDimension() : DEFAULT_DIMENSIONS;
        String provider = model.getProvider() != null ? model.getProvider().toUpperCase() : "";
        return switch (provider) {
            case "OPENAI" -> {
                String apiKey = decryptApiKey(model);
                OpenAITextEmbedding.Builder b = OpenAITextEmbedding.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .dimensions(dimensions);
                if (StringUtils.hasText(model.getBaseUrl())) {
                    b.baseUrl(model.getBaseUrl());
                }
                yield b.build();
            }
            case "DASHSCOPE" -> {
                String apiKey = decryptApiKey(model);
                DashScopeTextEmbedding.Builder b = DashScopeTextEmbedding.builder()
                        .apiKey(apiKey)
                        .modelName(model.getModelName())
                        .dimensions(dimensions);
                if (StringUtils.hasText(model.getBaseUrl())) {
                    b.baseUrl(model.getBaseUrl());
                }
                yield b.build();
            }
            case "OLLAMA" -> {
                if (!StringUtils.hasText(model.getBaseUrl())) {
                    throw new AiBusinessException(
                            AiErrorCode.EMBEDDING_FAILED, "Ollama embedding BaseURL未配置，模型ID: " + model.getId());
                }
                yield OllamaTextEmbedding.builder()
                        .baseUrl(model.getBaseUrl())
                        .modelName(model.getModelName())
                        .dimensions(dimensions)
                        .build();
            }
            default -> throw new AiBusinessException(AiErrorCode.EMBEDDING_FAILED, "不支持的 embedding 提供商: " + provider);
        };
    }

    private PgVectorStore buildPgVectorStore(KnowledgeBaseEntity kb) {
        String dsName = aiProperties.getDataSource().getName();
        String prefix = DS_PROPERTY_PREFIX + dsName;
        String jdbcUrl = environment.getProperty(prefix + ".url");
        String username = environment.getProperty(prefix + ".username");
        String password = environment.getProperty(prefix + ".password");
        if (!StringUtils.hasText(jdbcUrl) || !StringUtils.hasText(username)) {
            throw new AiBusinessException(
                    AiErrorCode.SYSTEM_ERROR,
                    "无法解析 ai-postgres JDBC 凭据（属性 " + prefix + ".url/.username），kbId: " + kb.getId());
        }
        int dimensions = kb.getEmbeddingDimension() != null ? kb.getEmbeddingDimension() : DEFAULT_DIMENSIONS;
        String tableName =
                StringUtils.hasText(kb.getVectorTableName()) ? kb.getVectorTableName() : DEFAULT_VECTOR_TABLE;
        try {
            return PgVectorStore.builder()
                    .jdbcUrl(jdbcUrl)
                    .username(username)
                    .password(password)
                    .schema("public")
                    .tableName(tableName)
                    .dimensions(dimensions)
                    .distanceType(PgVectorStore.DistanceType.COSINE)
                    .build();
        } catch (VectorStoreException e) {
            throw new AiBusinessException(
                    AiErrorCode.VECTOR_SEARCH_FAILED,
                    "构造 PgVectorStore 失败，kbId: " + kb.getId() + "，原因: " + e.getMessage(),
                    e);
        }
    }

    private String decryptApiKey(LlmModelEntity model) {
        if (!StringUtils.hasText(model.getApiKeyEncrypted())) {
            throw new AiBusinessException(AiErrorCode.EMBEDDING_FAILED, "embedding 模型 API密钥未配置，模型ID: " + model.getId());
        }
        try {
            return keyEncryptionService.decrypt(model.getApiKeyEncrypted());
        } catch (Exception e) {
            throw new AiBusinessException(
                    AiErrorCode.EMBEDDING_FAILED, "embedding 模型 API密钥解密失败，模型ID: " + model.getId(), e);
        }
    }

    private static void closeSilently(SimpleKnowledge knowledge, String kbId) {
        if (knowledge == null) {
            return;
        }
        try {
            VDBStoreBase store = knowledge.getEmbeddingStore();
            if (store instanceof AutoCloseable closeable) {
                closeable.close();
                log.debug("KnowledgeFactory: 已关闭 PgVectorStore，kbId: {}", kbId);
            }
        } catch (Exception e) {
            log.warn("KnowledgeFactory: 关闭向量库失败，kbId: {}，原因: {}", kbId, e.getMessage());
        }
    }
}
