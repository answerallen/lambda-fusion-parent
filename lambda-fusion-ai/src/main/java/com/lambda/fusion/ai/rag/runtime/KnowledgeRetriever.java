package com.lambda.fusion.ai.rag.runtime;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识检索器（防腐层自有接口）：对话时按绑定的知识库实时检索相关片段。实现
 * （{@code SimpleKnowledgeAdapter}）由 {@code AiConfigure.RagConfiguration}
 * 在 {@code lambda.fusion.ai.rag.enabled=true} 时装配；未启用时容器内无此 Bean，
 * {@code AgentFactory} 判空跳过中间件挂载。
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

    /**
     * 带条数限制的检索（供 Agentic 工具按模型请求截断）；默认忽略 limit 走两参版本，
     * 兼容其他实现。
     *
     * @param kbIds 知识库ID列表
     * @param query 查询文本
     * @param limit 返回条数上限（null 走知识库/全局默认）
     * @return 命中片段列表
     */
    default Mono<List<RetrievedChunk>> retrieve(List<String> kbIds, String query, Integer limit) {
        return retrieve(kbIds, query);
    }
}
