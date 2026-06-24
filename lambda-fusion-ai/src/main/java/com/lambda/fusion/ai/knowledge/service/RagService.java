package com.lambda.fusion.ai.knowledge.service;

import com.lambda.fusion.ai.knowledge.model.RagResult;
import com.lambda.fusion.ai.knowledge.model.VectorSearchResult;
import dev.langchain4j.data.message.ChatMessage;
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
    List<VectorSearchResult> retrieve(String query, String kbId, Integer topK, Double minScore);

    /**
     * RAG问答
     *
     * @param query      用户问题
     * @param kbId       知识库ID
     * @param llmModelId 指定的LLM模型ID
     * @param history    历史对话上下文
     * @return RAG执行结果
     */
    RagResult chat(
            String query, String kbId, String llmModelId, List<dev.langchain4j.data.message.ChatMessage> history);

    /**
     * 流式对话 (RAG)
     *
     * @param query           用户问题
     * @param kbId            知识库ID
     * @param retrievedChunks 预先检索到的文档块 (可选，若为null则内部重新检索)
     * @param llmModelId      指定的LLM模型ID
     * @param history         历史对话上下文
     * @param handler         流式响应处理器
     */
    void streamChat(
            String query,
            String kbId,
            List<VectorSearchResult> retrievedChunks,
            String llmModelId,
            List<ChatMessage> history,
            StreamingChatResponseHandler handler);
}
