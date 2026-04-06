package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

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
    List<DocumentEntity> listByKbId(@Param("kbId") String kbId, @Param("status") String status);

    /**
     * 根据文件哈希查询文档(去重检测)
     */
    @Select(
            "SELECT * FROM ai_document WHERE file_hash = #{fileHash} AND kb_id = #{kbId} AND deleted_at IS NULL LIMIT 1")
    DocumentEntity selectByFileHash(@Param("fileHash") String fileHash, @Param("kbId") String kbId);

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
            @Param("id") String id,
            @Param("processStatus") String processStatus,
            @Param("processProgress") Integer processProgress,
            @Param("errorMessage") String errorMessage);

    /**
     * 根据知识库ID和处理状态查询
     * @param kbId 知识库ID
     * @param processStatus 处理状态
     * @return 文档列表
     */
    List<DocumentEntity> selectByKbIdAndStatus(@Param("kbId") Long kbId, @Param("processStatus") String processStatus);

    /**
     * 统计知识库文档数
     * @param kbId 知识库ID
     * @return 文档数量
     */
    Integer countByKbId(@Param("kbId") Long kbId);

    /**
     * 按处理状态查询文档
     * @param processStatus 处理状态(PENDING/PROCESSING/COMPLETED/FAILED)
     * @param limit 限制数量
     * @return 文档列表
     */
    List<DocumentEntity> selectByProcessStatus(
            @Param("processStatus") String processStatus, @Param("limit") Integer limit);

    /**
     * 批量更新处理状态
     * @param documents 包含id、processStatus、processProgress、errorMessage的文档列表
     * @return 更新数量
     */
    int updateProcessStatusBatch(@Param("list") List<DocumentEntity> documents);

    /**
     * 批量删除知识库文档(软删除)
     * @param kbIds 知识库ID列表
     * @return 删除数量
     */
    int deleteByKbIdBatch(@Param("kbIds") List<String> kbIds);

    /**
     * 查询待处理文档
     * @param limit 限制数量
     * @return 待处理文档列表
     */
    List<DocumentEntity> selectPendingDocuments(@Param("limit") Integer limit);

    /**
     * 统计知识库各状态文档数
     * @param kbId 知识库ID
     * @return Map<status, count>
     */
    @MapKey("status")
    List<Map<String, Object>> countByKbIdGroupByStatus(@Param("kbId") Long kbId);
}
