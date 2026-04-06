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
 * 使用分表存储方案：ai_vector_store_768、ai_vector_store_1536、ai_vector_store_4096
 * 根据向量维度自动路由到对应的表
 *
 * @author Jin
 */
@Mapper
@Repository
@DS("@aiProperties.dataSource.name")
public interface VectorRepository {

    /**
     * 插入向量数据到分表
     * 根据维度自动选择表：ai_vector_store_{dimension}
     *
     * @param dimension      向量维度（用于选择表）
     * @param id             主键ID
     * @param vectorId       向量ID(UUID)
     * @param kbId           知识库ID
     * @param documentId     文档ID
     * @param chunkId        文档块ID
     * @param collectionName 集合名称
     * @param content        文本内容
     * @param metadata       元数据(JSON)
     * @param embedding      向量数据
     */
    void insertVector(
            @Param("dimension") Integer dimension,
            @Param("id") Long id,
            @Param("vectorId") String vectorId,
            @Param("kbId") Long kbId,
            @Param("documentId") Long documentId,
            @Param("chunkId") Long chunkId,
            @Param("collectionName") String collectionName,
            @Param("content") String content,
            @Param("metadata") String metadata,
            @Param("embedding") List<Double> embedding);

    /**
     * 批量插入向量数据到分表
     *
     * @param dimension  向量维度
     * @param list       文档块实体列表
     * @param kbId       知识库ID
     * @param collectionName 集合名称
     */
    void batchInsertVectors(
            @Param("dimension") Integer dimension,
            @Param("list") List<DocumentChunkEntity> list,
            @Param("kbId") String kbId,
            @Param("collectionName") String collectionName);

    /**
     * 向量相似度搜索（分表）
     *
     * @param dimension   向量维度（用于选择表）
     * @param kbId        知识库ID
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param minScore    最小相似度
     * @return 搜索结果
     */
    List<VectorSearchResult> searchSimilar(
            @Param("dimension") Integer dimension,
            @Param("kbId") String kbId,
            @Param("queryVector") List<Double> queryVector,
            @Param("topK") Integer topK,
            @Param("minScore") Double minScore);

    /**
     * 关键词搜索（分表）
     *
     * @param dimension 向量维度（用于选择表）
     * @param kbId      知识库ID
     * @param keyword   关键词
     * @param topK      返回数量
     * @return 搜索结果
     */
    List<VectorSearchResult> searchKeyword(
            @Param("dimension") Integer dimension,
            @Param("kbId") String kbId,
            @Param("keyword") String keyword,
            @Param("topK") Integer topK);

    /**
     * 删除向量（分表）
     *
     * @param dimension 向量维度（用于选择表）
     * @param vectorId  向量ID
     */
    void deleteVector(@Param("dimension") Integer dimension, @Param("vectorId") String vectorId);

    /**
     * 批量删除向量（分表）
     *
     * @param dimension 向量维度（用于选择表）
     * @param vectorIds 向量ID列表
     */
    void deleteVectors(@Param("dimension") Integer dimension, @Param("vectorIds") List<String> vectorIds);

    /**
     * 根据文档ID删除向量（分表）
     *
     * @param dimension  向量维度（用于选择表）
     * @param documentId 文档ID
     */
    void deleteByDocumentId(@Param("dimension") Integer dimension, @Param("documentId") String documentId);

    /**
     * 根据知识库ID删除向量（分表）
     *
     * @param dimension 向量维度（用于选择表）
     * @param kbId      知识库ID
     */
    void deleteByKbId(@Param("dimension") Integer dimension, @Param("kbId") String kbId);

    /**
     * 根据集合名称删除向量（分表）
     *
     * @param dimension      向量维度（用于选择表）
     * @param collectionName 集合名称
     */
    void deleteByCollectionName(@Param("dimension") Integer dimension, @Param("collectionName") String collectionName);
}
