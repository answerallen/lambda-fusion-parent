package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.Document;
import com.lambda.fusion.ai.model.DocumentChunk;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档Service接口
 *
 * @author Jin
 */
public interface DocumentService extends IService<DocumentEntity> {

    /**
     * 上传文档
     *
     * @param kbId       知识库ID
     * @param file       文件
     * @param uploadedBy 上传用户ID
     * @return 文档VO
     */
    Document uploadDocument(Long kbId, MultipartFile file, Long uploadedBy);

    /**
     * 分页查询文档列表
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param kbId     知识库ID
     * @param status   处理状态(可选)
     * @return 分页结果
     */
    Page<Document> pageDocuments(Integer pageNum, Integer pageSize, Long kbId, String status);

    /**
     * 根据知识库ID查询文档列表
     *
     * @param kbId   知识库ID
     * @param status 处理状态(可选)
     * @return 文档列表
     */
    List<Document> listByKbId(Long kbId, String status);

    /**
     * 根据ID查询文档详情
     *
     * @param id 文档ID
     * @return 文档VO
     */
    Document getDocumentById(Long id);

    /**
     * 删除文档
     *
     * @param id 文档ID
     */
    void deleteDocument(Long id);

    /**
     * 查询文档处理状态
     *
     * @param id 文档ID
     * @return 处理状态信息
     */
    Document getProcessStatus(Long id);

    /**
     * 更新处理状态
     *
     * @param id            文档ID
     * @param processStatus 处理状态
     * @param progress      处理进度
     * @param errorMessage  错误信息
     */
    void updateProcessStatus(Long id, String processStatus, Integer progress, String errorMessage);

    /**
     * 分页查询文档块
     */
    Page<DocumentChunk> pageChunks(Long docId, Integer pageNum, Integer pageSize);

    /**
     * 重新处理文档 (重新切分和向量化)
     */
    void reprocessDocument(Long docId);
}
