package com.lambda.fusion.ai.repository;

import com.lambda.fusion.ai.model.VectorSearchResult;
import java.util.List;
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
     * @param keyword 关键词
     * @param topK 返回数量
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
     * 批量删除向量
     */
    void deleteVectors(@Param("tableName") String tableName, @Param("vectorIds") List<String> vectorIds);
}
