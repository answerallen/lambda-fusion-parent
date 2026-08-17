package com.lambda.fusion.ai.rag.runtime;

/**
 * 检索命中的知识片段（防腐层自有返回类型，业务层不接触 AgentScope RAG 模型类）。
 *
 * @param content 片段文本
 * @param score 相似度分数
 * @param kbId 来源知识库ID
 * @param docId 来源文档ID
 * @param fileName 来源文件名
 * @param chunkId 向量库切块ID
 * @param chunkIndex 文档内零基切块序号；旧数据可能为空
 * @param chunkCount 文档切块总数；旧数据可能为空
 * @param sectionPath 章节路径；无章节结构时为空
 * @author Jin
 */
public record RetrievedChunk(
        String content,
        double score,
        String kbId,
        String docId,
        String fileName,
        String chunkId,
        Integer chunkIndex,
        Integer chunkCount,
        String sectionPath) {}
