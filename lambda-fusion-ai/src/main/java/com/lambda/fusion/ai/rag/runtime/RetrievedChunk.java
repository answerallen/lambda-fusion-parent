package com.lambda.fusion.ai.rag.runtime;

/**
 * 检索命中的知识片段（防腐层自有返回类型，业务层不接触 AgentScope RAG 模型类）。
 *
 * @param content 片段文本
 * @param score 相似度分数
 * @param kbId 来源知识库ID
 * @param docId 来源文档ID
 * @author Jin
 */
public record RetrievedChunk(String content, double score, String kbId, String docId) {}
