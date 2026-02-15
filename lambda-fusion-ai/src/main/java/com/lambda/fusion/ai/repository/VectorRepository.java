package com.lambda.fusion.ai.repository;

import com.lambda.fusion.ai.model.VectorSearchResult;
import java.util.List;

import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 向量存储库
 * 处理动态向量表的CRUD操作
 *
 * @author Jin
 */
@Mapper
@Repository
public interface VectorRepository {

    /**
     * 插入向量数据
     *
     * @param tableName 动态表名 (如 ai_vector_store_768)
     * @param id        主键ID
     * @param vectorId  向量ID(UUID)
     * @param content   文本内容
     * @param metadata  元数据(JSON)
     * @param embedding 向量数据
     */
    void insertVector(
            @Param("tableName") String tableName,
            @Param("id") Long id,
            @Param("vectorId") String vectorId,
            @Param("content") String content,
            @Param("metadata") String metadata,
            @Param("embedding") List<Double> embedding);

    /**
     * 向量相似度搜索
     *
     * @param tableName   动态表名
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param minScore    最小相似度
     * @return 搜索结果
     */
    List<VectorSearchResult> searchSimilar(
            @Param("tableName") String tableName,
            @Param("queryVector") List<Double> queryVector,
            @Param("topK") Integer topK,
            @Param("minScore") Double minScore);

    /**
     * 关键词搜索 (Trigram Similarity)
     *
     * @param tableName 动态表名
     * @param keyword   关键词
     * @param topK      返回数量
     * @return 搜索结果
     */
    List<VectorSearchResult> searchKeyword(
            @Param("tableName") String tableName, @Param("keyword") String keyword, @Param("topK") Integer topK);

    /**
     * 删除向量
     *
     * @param tableName 动态表名
     * @param vectorId  向量ID
     */
    void deleteVector(@Param("tableName") String tableName, @Param("vectorId") String vectorId);

    /**
     * 批量插入向量数据
     */
    void batchInsertVectors(
            @Param("tableName") String tableName,
            @Param("list") List<com.lambda.fusion.ai.model.entity.DocumentChunkEntity> list);

    /**
     * 根据文档ID删除向量
     */
    void deleteByDocumentId(@Param("tableName") String tableName, @Param("documentId") Long documentId);

    /**
     * 根据知识库ID删除向量
     */
    void deleteByKbId(@Param("tableName") String tableName, @Param("kbId") Long kbId);

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

    List<VectorSearchResult> searchSimilarUnified(
            @Param("kbId") Long kbId,
            @Param("queryVector") List<Double> queryVector,
            @Param("topK") Integer topK,
            @Param("minScore") Double minScore,
            @Param("dimension") Integer dimension);

    List<VectorSearchResult> searchKeywordUnified(
            @Param("kbId") Long kbId,
            @Param("keyword") String keyword,
            @Param("topK") Integer topK);

    void deleteVectorUnified(@Param("vectorId") String vectorId);

    void batchInsertVectorsUnified(
            @Param("list") List<DocumentChunkEntity> documentChunkEntities,
            @Param("kbId") Long kbId,
            @Param("dimension") Integer dimension);

    void deleteByDocumentIdUnified(@Param("documentId") Long documentId);

    void deleteByKbIdUnified(@Param("kbId") Long kbId);
}
