package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.entity.DocumentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档 Mapper接口
 *
 * @author Jin
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /**
     * 根据知识库ID分页查询文档
     *
     * @param page   分页对象
     * @param kbId   知识库ID
     * @param status 处理状态(可选)
     * @return 分页结果
     */
    Page<DocumentEntity> pageByKbId(
            Page<DocumentEntity> page, @Param("kbId") Long kbId, @Param("status") String status);

    /**
     * 根据知识库ID查询文档列表
     *
     * @param kbId   知识库ID
     * @param status 处理状态(可选)
     * @return 文档列表
     */
    List<DocumentEntity> listByKbId(@Param("kbId") Long kbId, @Param("status") String status);

    /**
     * 根据文件哈希查询文档(去重检测)
     *
     * @param fileHash 文件哈希
     * @param kbId     知识库ID
     * @return 文档实体
     */
    DocumentEntity selectByFileHash(@Param("fileHash") String fileHash, @Param("kbId") Long kbId);

    /**
     * 根据documentId查询文档
     *
     * @param documentId 文档唯一标识
     * @return 文档实体
     */
    DocumentEntity selectByDocumentId(@Param("documentId") String documentId);

    /**
     * 更新处理状态
     *
     * @param id              文档ID
     * @param processStatus   处理状态
     * @param processProgress 处理进度
     * @param errorMessage    错误信息
     */
    void updateProcessStatus(
            @Param("id") Long id,
            @Param("processStatus") String processStatus,
            @Param("processProgress") Integer processProgress,
            @Param("errorMessage") String errorMessage);
}
