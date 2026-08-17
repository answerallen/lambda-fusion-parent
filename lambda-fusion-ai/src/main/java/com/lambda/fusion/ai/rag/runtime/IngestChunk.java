package com.lambda.fusion.ai.rag.runtime;

/**
 * 待入库的文档切块（防腐层自有类型，入库管线与向量库之间传递文本片段）。
 *
 * @param chunkId 切块ID
 * @param text 切块文本
 * @param chunkIndex 文档内零基切块序号
 * @param sectionPath 所属章节路径；无章节结构时为空
 * @author Jin
 */
public record IngestChunk(String chunkId, String text, int chunkIndex, String sectionPath) {}
