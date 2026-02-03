package com.lambda.fusion.ai.service;

import com.lambda.fusion.ai.model.dto.VectorSearchResultDTO;
import java.util.List;

/**
 * RAG核心服务接口
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
    List<VectorSearchResultDTO> retrieve(String query, Long kbId, Integer topK, Double minScore);

    /**
     * RAG问答
     *
     * @param query 用户问题
     * @param kbId  知识库ID
     * @return RAG执行结果
     */
    com.lambda.fusion.ai.model.dto.RagResult chat(String query, Long kbId);

    /**
     * 流式RAG问答
     *
     * @param query   用户问题
     * @param kbId    知识库ID
     * @param handler 流式响应处理器
     * @return 检索到的相关文档块
     */
    java.util.List<com.lambda.fusion.ai.model.dto.VectorSearchResultDTO> streamChat(
            String query,
            Long kbId,
            dev.langchain4j.model.StreamingResponseHandler<dev.langchain4j.data.message.AiMessage> handler);
}
