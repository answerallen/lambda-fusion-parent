package com.lambda.fusion.ai.service;

import com.lambda.fusion.ai.model.RagResult;
import com.lambda.fusion.ai.model.VectorSearchResult;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;

/**
 * RAG服务接口
 * 负责向量检索和增强生成
 *
 * @author Jin
 */
public interface RagService {

    /**
     * 检索相关文档块
     *
     * @param query    查询文本
     * @param kbId     知识库ID
     * @param topK     返回TopK
     * @param minScore 最小相似度
     * @return 搜索结果列表
     */
    List<VectorSearchResult> retrieve(String query, Long kbId, Integer topK, Double minScore);

    /**
     * RAG问答
     *
     * @param query      用户问题
     * @param kbId       知识库ID
     * @param llmModelId 指定的LLM模型ID
     * @return RAG执行结果
     */
    RagResult chat(String query, Long kbId, Long llmModelId);

    /**
     * 流式对话 (RAG)
     *
     * @param query           用户问题
     * @param kbId            知识库ID
     * @param retrievedChunks 预先检索到的文档块 (可选，若为null则内部重新检索)
     * @param llmModelId      指定的LLM模型ID
     * @param handler         流式响应处理器
     * @return 最终召回的文档块
     */
    List<VectorSearchResult> streamChat(
            String query,
            Long kbId,
            List<VectorSearchResult> retrievedChunks,
            Long llmModelId,
            StreamingChatResponseHandler handler);
}
