package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/**
 * 文档块 Mapper接口
 */
@Mapper
@DS("#{@aiProperties.dataSource.name}")
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {

    /**
     * 根据文档ID查询文档块列表
     */
    @Select("SELECT * FROM ai_document_chunk WHERE document_id = #{documentId} ORDER BY chunk_index ASC")
    List<DocumentChunkEntity> listByDocumentId(@Param("documentId") Long documentId);

    /**
     * 根据知识库ID查询文档块列表
     */
    @Select("SELECT * FROM ai_document_chunk WHERE kb_id = #{kbId} ORDER BY created_at DESC")
    List<DocumentChunkEntity> listByKbId(@Param("kbId") Long kbId);

    /**
     * 分页查询知识库文档块
     */
    @Select("SELECT * FROM ai_document_chunk WHERE kb_id = #{kbId} ORDER BY chunk_index ASC")
    IPage<DocumentChunkEntity> selectPageByKbId(IPage<DocumentChunkEntity> page, @Param("kbId") Long kbId);

    /**
     * 根据vectorId查询文档块
     */
    @Select("SELECT * FROM ai_document_chunk WHERE vector_id = #{vectorId} LIMIT 1")
    DocumentChunkEntity selectByVectorId(@Param("vectorId") String vectorId);

    /**
     * 批量插入文档块
     */
    @Insert("<script>" + "INSERT INTO ai_document_chunk "
            + "(chunk_id, document_id, kb_id, content, content_hash, chunk_index, "
            + "start_offset, end_offset, page_number, vector_id, embedding_status, "
            + "metadata, char_count, token_count, created_at, updated_at) "
            + "VALUES "
            + "<foreach collection='list' item='item' separator=','> "
            + "(#{item.chunkId}, #{item.documentId}, #{item.kbId}, #{item.content}, "
            + "#{item.contentHash}, #{item.chunkIndex}, #{item.startOffset}, #{item.endOffset}, "
            + "#{item.pageNumber}, #{item.vectorId}, #{item.embeddingStatus}, #{item.metadata}, "
            + "#{item.charCount}, #{item.tokenCount}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("list") List<DocumentChunkEntity> chunkList);

    /**
     * 批量更新嵌入状态
     */
    @Update("<script>" + "UPDATE ai_document_chunk SET embedding_status = #{status} WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>"
            + "#{id}"
            + "</foreach>"
            + "</script>")
    int updateEmbeddingStatusBatch(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 根据文档ID批量删除
     */
    @Delete("<script>" + "DELETE FROM ai_document_chunk WHERE document_id IN "
            + "<foreach collection='documentIds' item='docId' open='(' separator=',' close=')'>"
            + "#{docId}"
            + "</foreach>"
            + "</script>")
    int deleteByDocumentIds(@Param("documentIds") List<String> documentIds);

    /**
     * 检查文档块是否存在 (通过 Hash)
     */
    @Select("SELECT id FROM ai_document_chunk WHERE document_id = #{documentId} AND content_hash = #{hash} LIMIT 1")
    Long findIdByHash(@Param("documentId") Long documentId, @Param("hash") String hash);

    /**
     * 按知识库ID和向量化状态查询
     * @param kbId 知识库ID
     * @param embeddingStatus 向量化状态
     * @return 文档块列表
     */
    List<DocumentChunkEntity> selectByKbIdAndEmbeddingStatus(
            @Param("kbId") Long kbId, @Param("embeddingStatus") String embeddingStatus);

    /**
     * 统计文档块数
     * @param documentId 文档ID
     * @return 块数量
     */
    Integer countByDocumentId(@Param("documentId") Long documentId);

    /**
     * 统计知识库块数
     * @param kbId 知识库ID
     * @return 块数量
     */
    Integer countByKbId(@Param("kbId") Long kbId);

    /**
     * 按文档ID和向量化状态查询
     * @param documentId 文档ID
     * @param embeddingStatus 向量化状态
     * @return 文档块列表
     */
    List<DocumentChunkEntity> selectByDocumentIdAndEmbeddingStatus(
            @Param("documentId") Long documentId, @Param("embeddingStatus") String embeddingStatus);

    /**
     * 按文档ID批量更新向量化状态
     * @param documentId 文档ID
     * @param embeddingStatus 新状态
     * @return 更新数量
     */
    int updateEmbeddingStatusByDocumentId(
            @Param("documentId") Long documentId, @Param("embeddingStatus") String embeddingStatus);

    /**
     * 按内容哈希查询(去重)
     * @param kbId 知识库ID
     * @param contentHash 内容哈希
     * @return 文档块
     */
    DocumentChunkEntity selectByContentHash(@Param("kbId") Long kbId, @Param("contentHash") String contentHash);

    /**
     * 查询待向量化块
     * @param limit 限制数量
     * @return 待向量化块列表
     */
    List<DocumentChunkEntity> selectPendingEmbeddingChunks(@Param("limit") Integer limit);

    /**
     * 统计知识库各向量化状态块数
     * @param kbId 知识库ID
     * @return Map<status, count>
     */
    @MapKey("status")
    List<Map<String, Object>> countByKbIdGroupByEmbeddingStatus(@Param("kbId") Long kbId);
}
