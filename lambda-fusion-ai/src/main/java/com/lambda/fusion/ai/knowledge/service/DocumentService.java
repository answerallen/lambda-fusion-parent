package com.lambda.fusion.ai.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.knowledge.model.Document;
import com.lambda.fusion.ai.knowledge.model.DocumentChunk;
import com.lambda.fusion.ai.knowledge.model.DocumentChunkQuery;
import com.lambda.fusion.ai.knowledge.model.DocumentQuery;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentEntity;
import jakarta.validation.Valid;
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
    Document uploadDocument(String kbId, MultipartFile file, String uploadedBy);

    /**
     * 根据知识库ID查询文档列表
     *
     * @param kbId   知识库ID
     * @param status 处理状态(可选)
     * @return 文档列表
     */
    List<Document> listByKbId(String kbId, String status);

    /**
     * 根据ID查询文档详情
     *
     * @param id 文档ID
     * @return 文档VO
     */
    Document getDocumentById(String kbId, String id);

    /**
     * 删除文档
     *
     * @param id 文档ID
     */
    void deleteDocument(String kbId, String id);

    /**
     * 查询文档处理状态
     *
     * @param id 文档ID
     * @return 处理状态信息
     */
    String getProcessStatus(String kbId, String id);

    /**
     * 更新处理状态
     *
     * @param id            文档ID
     * @param processStatus 处理状态
     * @param progress      处理进度
     * @param errorMessage  错误信息
     */
    void updateProcessStatus(String id, String processStatus, Integer progress, String errorMessage);
    /**
     * 重新处理文档 (重新切分和向量化)
     * @param kbId 知识库ID
     * @param documentId 文档ID
     */
    void reprocessDocument(String kbId, String documentId);

    /**
     * 分页查询文档列表
     *
     * @param documentQuery DocumentQuery
     * @return 分页结果
     */
    IPage<Document> pageDocuments(@Valid DocumentQuery documentQuery);

    /**
     * 分页查询文档列表
     *
     * @param documentChunkQuery DocumentChunkQuery
     * @return 分页结果
     */
    IPage<DocumentChunk> pageChunks(@Valid DocumentChunkQuery documentChunkQuery);
}
