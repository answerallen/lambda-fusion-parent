package com.lambda.fusion.ai.rag.runtime;

import com.lambda.fusion.ai.AiConstants.VectorStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.rag.service.KnowledgeBaseService;
import com.lambda.fusion.ai.runtime.EmbeddingModelResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.exception.VectorStoreException;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.PgVectorStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 知识检索/入库适配器：基于 AgentScope {@code agentscope-extensions-rag-simple}
 * （{@link SimpleKnowledge} + {@link VDBStoreBase}，未过期）实现 {@link KnowledgeRetriever}。
 *
 * <p><b>防腐层说明</b>：{@code io.agentscope.core.rag.model} 下的 {@link Document} /
 * {@link DocumentMetadata} / {@link RetrieveConfig} 已 {@code @Deprecated(forRemoval = true)}，
 * 全模块仅允许本类 import 这些类型——将来 AgentScope 删除该包时只需改造本类，
 * 业务层（service/中间件）只依赖自有的 {@link KnowledgeRetriever} / {@link RetrievedChunk} /
 * {@link IngestChunk}。
 *
 * <p>向量库双后端（{@code lambda.fusion.ai.rag.store.type}）：MEMORY（默认，
 * {@link InMemoryStore}，每知识库一个实例天然隔离，零配置但重启丢失）/ PGVECTOR
 * （{@link PgVectorStore}，每知识库一张向量表 {@code ai_kb_{kbId}}——{@code SearchDocumentDto}
 * 无 payload 过滤，且各知识库 embedding 维度可能不同，表名由系统在建库时生成）。
 * {@link SimpleKnowledge} 与知识库实体按 kbId 缓存；知识库配置变更/删除时由业务层调
 * {@link #evict(String)} 失效。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("removal")
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SimpleKnowledgeAdapter implements KnowledgeRetriever {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingModelResolver embeddingModelResolver;
    private final AiProperties aiProperties;

    private final Map<String, SimpleKnowledge> knowledgeCache = new ConcurrentHashMap<>();
    private final Map<String, KnowledgeBaseEntity> kbCache = new ConcurrentHashMap<>();

    @Override
    public Mono<List<RetrievedChunk>> retrieve(List<String> kbIds, String query) {
        // 单 KB 检索内部为阻塞 IO（embedding HTTP + JDBC），整体放弹性线程池串行执行
        return Mono.fromCallable(() -> doRetrieve(kbIds, query)).subscribeOn(Schedulers.boundedElastic());
    }

    private List<RetrievedChunk> doRetrieve(List<String> kbIds, String query) {
        AiProperties.Rag rag = aiProperties.getRag();
        List<RetrievedChunk> merged = new ArrayList<>();
        for (String kbId : kbIds) {
            KnowledgeBaseEntity kb = loadKb(kbId);
            if (kb == null || !Boolean.TRUE.equals(kb.getEnabled())) {
                continue;
            }
            try {
                RetrieveConfig config = RetrieveConfig.builder()
                        .limit(resolveLimit(kb, rag))
                        .scoreThreshold(resolveScoreThreshold(kb, rag))
                        .build();
                List<Document> docs = knowledge(kb).retrieve(query, config).block();
                if (docs == null) {
                    continue;
                }
                for (Document doc : docs) {
                    merged.add(new RetrievedChunk(
                            doc.getMetadata().getContentText(),
                            doc.getScore() != null ? doc.getScore() : 0d,
                            kbId,
                            doc.getMetadata().getDocId()));
                }
            } catch (Exception e) {
                // 单知识库检索失败跳过，不影响其他知识库与对话主流程
                log.warn("知识库 {} 检索失败，跳过: {}", kbId, e.getMessage());
            }
        }
        // 跨知识库合并按分数降序，截断到全局默认条数
        return mergeAndTruncate(merged, rag.getDefaultLimit());
    }

    // 包级可见便于单测
    static List<RetrievedChunk> mergeAndTruncate(List<RetrievedChunk> merged, int limit) {
        merged.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
    }

    /**
     * 文档切块入库：组装向量文档（payload 写入 kbId/tenantId/fileName）后写入知识库向量表。
     */
    public void addChunks(String kbId, String docId, String tenantId, String fileName, List<IngestChunk> chunks) {
        KnowledgeBaseEntity kb = requireKb(kbId);
        List<Document> documents = new ArrayList<>(chunks.size());
        // tenantId 可能为空，用 HashMap 承载 payload（Map.of 不允许 null 值）
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", kbId);
        payload.put("tenantId", tenantId);
        payload.put("fileName", fileName);
        for (IngestChunk chunk : chunks) {
            DocumentMetadata metadata = DocumentMetadata.builder()
                    .content(TextBlock.builder().text(chunk.text()).build())
                    .docId(docId)
                    .chunkId(chunk.chunkId())
                    .payload(payload)
                    .build();
            documents.add(new Document(metadata));
        }
        knowledge(kb).addDocuments(documents).block();
    }

    /**
     * 按文档ID删除整文档切块；向量库实例未构建过时跳过（库里本就没有该文档数据）。
     */
    public void deleteDocument(String kbId, String docId) {
        KnowledgeBaseEntity kb = loadKb(kbId);
        if (kb == null) {
            return;
        }
        try {
            knowledge(kb).getEmbeddingStore().delete(docId).block();
        } catch (Exception e) {
            log.warn("删除文档向量数据失败(kb={}, doc={}): {}", kbId, docId, e.getMessage());
        }
    }

    /**
     * 失效知识库缓存（实体 + 向量库连接）；配置变更或删除时调用。
     */
    public void evict(String kbId) {
        kbCache.remove(kbId);
        SimpleKnowledge knowledge = knowledgeCache.remove(kbId);
        if (knowledge != null && knowledge.getEmbeddingStore() instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn("关闭知识库 {} 向量库连接失败: {}", kbId, e.getMessage());
            }
        }
    }

    // 知识库实体缓存；不存在时不缓存 null（下次重试加载）
    private KnowledgeBaseEntity loadKb(String kbId) {
        return kbCache.computeIfAbsent(kbId, id -> {
            try {
                return knowledgeBaseService.loadById(id);
            } catch (AiBusinessException e) {
                return null;
            }
        });
    }

    private KnowledgeBaseEntity requireKb(String kbId) {
        KnowledgeBaseEntity kb = loadKb(kbId);
        if (kb == null) {
            throw new AiBusinessException(AiErrorCode.KB_NOT_FOUND, kbId);
        }
        return kb;
    }

    private SimpleKnowledge knowledge(KnowledgeBaseEntity kb) {
        return knowledgeCache.computeIfAbsent(kb.getId(), id -> buildKnowledge(kb));
    }

    private SimpleKnowledge buildKnowledge(KnowledgeBaseEntity kb) {
        EmbeddingModel embeddingModel = embeddingModelResolver.resolve(kb.getEmbeddingModelId(), kb.getDimensions());
        int dimensions = kb.getDimensions() != null ? kb.getDimensions() : embeddingModel.getDimensions();
        VDBStoreBase store = createStore(aiProperties.getRag(), kb, dimensions);
        return SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
    }

    // 按配置创建向量库实例（包级可见便于单测）；MEMORY 零依赖，PGVECTOR 需 JDBC 连接配置
    static VDBStoreBase createStore(AiProperties.Rag rag, KnowledgeBaseEntity kb, int dimensions) {
        VectorStoreType type = VectorStoreType.of(rag.getStore().getType());
        if (type == null) {
            throw new AiBusinessException(
                    AiErrorCode.CONFIGURATION_ERROR,
                    "未知向量库类型: " + rag.getStore().getType());
        }
        if (type == VectorStoreType.MEMORY) {
            // 进程内存实现：无需 jdbcUrl/vectorTable，数据重启丢失
            return InMemoryStore.builder().dimensions(dimensions).build();
        }
        AiProperties.Rag.PgVector pgvector = rag.getPgvector();
        if (StringUtils.isBlank(pgvector.getJdbcUrl())) {
            throw new AiBusinessException(AiErrorCode.KB_VECTOR_STORE_NOT_CONFIGURED);
        }
        try {
            return PgVectorStore.builder()
                    .jdbcUrl(pgvector.getJdbcUrl())
                    .username(pgvector.getUsername())
                    .password(pgvector.getPassword())
                    .schema(pgvector.getSchema())
                    .tableName(kb.getVectorTable())
                    .dimensions(dimensions)
                    .build();
        } catch (VectorStoreException e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, e);
        }
    }

    // 知识库级 limit 覆盖全局默认（包级可见便于单测）
    static int resolveLimit(KnowledgeBaseEntity kb, AiProperties.Rag rag) {
        return kb.getRetrieveLimit() != null ? kb.getRetrieveLimit() : rag.getDefaultLimit();
    }

    // 知识库级 scoreThreshold 覆盖全局默认（包级可见便于单测）
    static double resolveScoreThreshold(KnowledgeBaseEntity kb, AiProperties.Rag rag) {
        return kb.getScoreThreshold() != null ? kb.getScoreThreshold().doubleValue() : rag.getDefaultScoreThreshold();
    }
}
