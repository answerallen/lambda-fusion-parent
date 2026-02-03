package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文档 Mapper接口
 *
 * @author Jin
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /**
     * 根据知识库ID分页查询文档
     */
    @Select("<script>" + "SELECT * FROM ai_document "
            + "WHERE kb_id = #{kbId} "
            + "<if test='status != null and status != \"\"'> "
            + "AND process_status = #{status} "
            + "</if> "
            + "AND deleted_at IS NULL "
            + "ORDER BY uploaded_at DESC"
            + "</script>")
    Page<DocumentEntity> pageByKbId(
            Page<DocumentEntity> page, @Param("kbId") Long kbId, @Param("status") String status);

    /**
     * 根据知识库ID查询文档列表
     */
    @Select("<script>" + "SELECT * FROM ai_document "
            + "WHERE kb_id = #{kbId} "
            + "<if test='status != null and status != \"\"'> "
            + "AND process_status = #{status} "
            + "</if> "
            + "AND deleted_at IS NULL "
            + "ORDER BY uploaded_at DESC"
            + "</script>")
    List<DocumentEntity> listByKbId(@Param("kbId") Long kbId, @Param("status") String status);

    /**
     * 根据文件哈希查询文档(去重检测)
     */
    @Select(
            "SELECT * FROM ai_document WHERE file_hash = #{fileHash} AND kb_id = #{kbId} AND deleted_at IS NULL LIMIT 1")
    DocumentEntity selectByFileHash(@Param("fileHash") String fileHash, @Param("kbId") Long kbId);

    /**
     * 根据documentId查询文档
     */
    @Select("SELECT * FROM ai_document WHERE document_id = #{documentId} AND deleted_at IS NULL LIMIT 1")
    DocumentEntity selectByDocumentId(@Param("documentId") String documentId);

    /**
     * 更新处理状态
     */
    @Update("UPDATE ai_document SET process_status = #{processStatus}, process_progress = #{processProgress}, "
            + "error_message = #{errorMessage}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    void updateProcessStatus(
            @Param("id") Long id,
            @Param("processStatus") String processStatus,
            @Param("processProgress") Integer processProgress,
            @Param("errorMessage") String errorMessage);
}
