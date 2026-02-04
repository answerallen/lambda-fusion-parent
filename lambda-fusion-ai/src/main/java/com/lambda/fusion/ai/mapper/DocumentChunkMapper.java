package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文档块 Mapper接口
 */
@Mapper
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
    int deleteByDocumentIds(@Param("documentIds") List<Long> documentIds);

    /**
     * 检查文档块是否存在 (通过 Hash)
     */
    @Select("SELECT id FROM ai_document_chunk WHERE document_id = #{documentId} AND content_hash = #{hash} LIMIT 1")
    Long findIdByHash(@Param("documentId") Long documentId, @Param("hash") String hash);
}
