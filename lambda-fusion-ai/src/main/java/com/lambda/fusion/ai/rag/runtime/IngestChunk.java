package com.lambda.fusion.ai.rag.runtime;

/**
 * 待入库的文档切块（防腐层自有类型，入库管线与向量库之间传递文本片段）。
 *
 * @param chunkId 切块ID
 * @param text 切块文本
 * @author Jin
 */
public record IngestChunk(String chunkId, String text) {}
