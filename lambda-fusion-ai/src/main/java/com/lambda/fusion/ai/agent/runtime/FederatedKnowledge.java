package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 联邦知识库（多 KB 检索的 {@link Knowledge} 适配器）。
 *
 * <p>{@link io.agentscope.core.rag.KnowledgeRetrievalTools} 构造期绑定单个 {@link Knowledge}
 * 并注册名为 {@code retrieve_knowledge} 的工具；若为每个 KB 各注册一个，同名工具冲突。本类把多个
 * {@link io.agentscope.core.rag.knowledge.SimpleKnowledge} 聚合为单一 {@link Knowledge}，使 agent
 * 仍只见一个 {@code retrieve_knowledge} 工具，调用时跨所有 KB 检索并按 {@link Document#getScore()}
 * 合并取 topK。
 *
 * <p><b>只读</b>：仅实现 {@link #retrieve(String, RetrieveConfig)}；{@link #addDocuments(List)} 抛
 * {@link UnsupportedOperationException}--ingestion 走各 KB 的 SimpleKnowledge（经 DocumentProcessor），
 * 联邦视图不承载写入（{@code KnowledgeRetrievalTools} 仅调用 retrieve，从不 addDocuments）。
 *
 * <p><b>合并策略</b>：对每个 KB 用同一 {@link RetrieveConfig}（含 limit）检索，合并后按 score 降序取
 * {@code config.getLimit()} 条。跨 KB score 可比性前提：各 KB embedding 模型/距离度量一致（本期
 * ai-postgres PgVectorStore + cosine）；若后续混用 embedding 空间，需在 KnowledgeFactory 校验或改归一化。
 * 单个 KB 检索失败不阻断整体（{@code onErrorResume} 跳过），agent 降级为可用 KB 的结果。
 *
 * @author Jin
 */
@Slf4j
public class FederatedKnowledge implements Knowledge {

    private final List<Knowledge> knowledgeBases;

    public FederatedKnowledge(List<Knowledge> knowledgeBases) {
        this.knowledgeBases = knowledgeBases != null ? knowledgeBases : List.of();
    }

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        if (knowledgeBases.isEmpty()) {
            return Mono.just(List.of());
        }
        int limit = config != null && config.getLimit() > 0 ? config.getLimit() : Integer.MAX_VALUE;
        return Flux.fromIterable(knowledgeBases)
                .flatMap(kb -> kb.retrieve(query, config)
                        .onErrorResume(e -> {
                            log.warn("FederatedKnowledge: 单 KB 检索失败，跳过，query={}", query, e);
                            return Mono.just(List.<Document>of());
                        }))
                .flatMapIterable(docs -> docs)
                .collectList()
                .map(all -> {
                    List<Document> ranked = new ArrayList<>(all);
                    ranked.sort(Comparator.comparingDouble(FederatedKnowledge::scoreOrZero).reversed());
                    return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
                });
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        throw new UnsupportedOperationException(
                "FederatedKnowledge is read-only; ingest via individual SimpleKnowledge through DocumentProcessor");
    }

    private static double scoreOrZero(Document d) {
        return d.getScore() != null ? d.getScore() : 0.0;
    }
}
