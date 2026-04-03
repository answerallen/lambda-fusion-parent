package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.lambda.fusion.ai.model.VectorSearchResult;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 向量存储库
 * 统一使用 ai_vector_store 表进行向量存储和检索
 *
 * @author Jin
 */
@Mapper
@Repository
@DS("#{@aiDataSourceProperties.vectorName}")
public interface VectorRepository {

    // ==================== 统一表操作（推荐） ====================

    /**
     * 插入向量数据到统一表
     *
     * @param id        主键ID
     * @param vectorId  向量ID(UUID)
     * @param kbId      知识库ID
     * @param documentId 文档ID
     * @param chunkId   文档块ID
     * @param content   文本内容
     * @param metadata  元数据(JSON)
     * @param embedding 向量数据
     * @param dimension 向量维度
     */
    void insertVectorUnified(
            @Param("id") Long id,
            @Param("vectorId") String vectorId,
            @Param("kbId") Long kbId,
            @Param("documentId") Long documentId,
            @Param("chunkId") Long chunkId,
            @Param("content") String content,
            @Param("metadata") String metadata,
            @Param("embedding") List<Double> embedding,
            @Param("dimension") Integer dimension);

    /**
     * 向量相似度搜索（统一表）
     *
     * @param kbId        知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param minScore    最小相似度
     * @param dimension   向量维度
     * @return 搜索结果
     */
    List<VectorSearchResult> searchSimilarUnified(
            @Param("kbId") Long kbId,
            @Param("queryVector") List<Double> queryVector,
            @Param("topK") Integer topK,
            @Param("minScore") Double minScore,
            @Param("dimension") Integer dimension);

    /**
     * 关键词搜索（统一表）
     *
     * @param kbId    知识库ID
     * @param keyword 关键词
     * @param topK    返回数量
     * @return 搜索结果
     */
    List<VectorSearchResult> searchKeywordUnified(
            @Param("kbId") Long kbId, @Param("keyword") String keyword, @Param("topK") Integer topK);

    /**
     * 删除向量（统一表）
     *
     * @param vectorId 向量ID
     */
    void deleteVectorUnified(@Param("vectorId") String vectorId);

    /**
     * 批量删除向量（统一表）
     *
     * @param vectorIds 向量ID列表
     */
    void deleteVectorsUnified(@Param("vectorIds") List<String> vectorIds);

    /**
     * 批量插入向量数据（统一表）
     *
     * @param documentChunkEntities 文档块实体列表
     * @param kbId                  知识库ID
     */
    void batchInsertVectorsUnified(
            @Param("list") List<DocumentChunkEntity> documentChunkEntities, @Param("kbId") Long kbId);

    /**
     * 根据文档ID删除向量（统一表）
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentIdUnified(@Param("documentId") Long documentId);

    /**
     * 根据知识库ID删除向量（统一表）
     *
     * @param kbId 知识库ID
     */
    void deleteByKbIdUnified(@Param("kbId") Long kbId);
}
