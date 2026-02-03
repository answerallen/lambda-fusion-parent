package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
