package com.lambda.fusion.ai.rag.runtime;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识检索器（防腐层自有接口）：对话时按绑定的知识库实时检索相关片段。
 *
 * <p>实现（{@code SimpleKnowledgeAdapter}）由 {@code AiConfigure.RagConfiguration}
 * 在 {@code lambda.fusion.ai.rag.enabled=true} 时装配；未启用时容器内无此 Bean，
 * {@code AgentFactory} 通过 {@code ObjectProvider} 判空跳过中间件挂载。
 *
 * @author Jin
 */
public interface KnowledgeRetriever {

    /**
     * 跨知识库检索，命中片段按分数降序合并返回；未命中返回空列表。
     *
     * @param kbIds 知识库ID列表
     * @param query 查询文本
     * @return 命中片段列表
     */
    Mono<List<RetrievedChunk>> retrieve(List<String> kbIds, String query);
}
