package com.lambda.fusion.ai.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.oss.client.OssClient;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.cloud.oss.model.UploadObjectResult;
import com.lambda.fusion.ai.AiConstants.Enums.DocumentStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.knowledge.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.knowledge.mapper.DocumentMapper;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.mapper.VectorRepository;
import com.lambda.fusion.ai.knowledge.model.Document;
import com.lambda.fusion.ai.knowledge.model.DocumentChunk;
import com.lambda.fusion.ai.knowledge.model.DocumentChunkQuery;
import com.lambda.fusion.ai.knowledge.model.DocumentQuery;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentEntity;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.knowledge.processor.DocumentProcessor;
import com.lambda.fusion.ai.knowledge.service.DocumentService;
import com.lambda.fusion.ai.knowledge.vector.VectorDimensionProcessor;
import com.lambda.fusion.core.service.AbstractCrudService;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档 Service 实现类
 *
 * @author Jin
 */
@Slf4j
@Service
public class DocumentServiceImpl extends AbstractCrudService<DocumentEntity, Document, DocumentMapper>
        implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorRepository vectorRepository;
    private final DocumentProcessor documentProcessor;
    private final AiProperties aiProperties;
    private final OssClient ossClient;

    @Autowired
    public DocumentServiceImpl(
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            KnowledgeBaseMapper knowledgeBaseMapper,
            VectorRepository vectorRepository,
            DocumentProcessor documentProcessor,
            AiProperties aiProperties,
            OssClientManager ossClientManager) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.vectorRepository = vectorRepository;
        this.documentProcessor = documentProcessor;
        this.aiProperties = aiProperties;
        this.ossClient = ossClientManager.get(aiProperties.getDocument().getOssClientName());
    }

    @Override
    public Document uploadDocument(String kbId, MultipartFile file, String uploadedBy) {
        log.info("上传文档到知识库: kbId={}, fileName={}", kbId, file.getOriginalFilename());

        if (kbId == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库ID不能为空");
        }

        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw AiBusinessException.knowledgeBaseNotFound(kbId);
        }

        long maxFileSize = aiProperties.getDocumentChunk().getMaxFileSize();
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + maxFileSize + " bytes");
        }

        String originalFilename = file.getOriginalFilename();
        Assert.hasText(originalFilename, "文件名不能为空！");

        String fileHash;
        try {
            fileHash = DigestUtil.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "计算文件哈希失败", e);
        }

        DocumentEntity existingDoc = documentMapper.selectByFileHash(fileHash, kbId);
        if (existingDoc != null) {
            log.warn("文档已存在, fileHash={}, documentId={}", fileHash, existingDoc.getId());
            return toVO(existingDoc);
        }

        String fileExtension = FileUtil.extName(originalFilename);

        // 验证文件扩展名白名单
        validateFileExtension(fileExtension);

        String ossKey = "ai-documents/" + kbId + "/" + fileHash + "." + fileExtension;

        File tempFile = null;
        try {
            tempFile = File.createTempFile("upload-", "." + fileExtension);
            file.transferTo(tempFile);

            UploadObjectResult uploadResult = ossClient.upload(tempFile, ossKey);
            log.info("文件上传到OSS成功: {}", uploadResult.getKey());

            DocumentEntity entity = new DocumentEntity();
            entity.setKbId(kbId);
            entity.setFileName(originalFilename);
            entity.setFileType(StrUtil.toUpperCase(fileExtension));
            entity.setFileSize(file.getSize());
            entity.setFileHash(fileHash);
            entity.setStorageType("OSS");
            entity.setStoragePath(ossKey);
            entity.setStorageUrl(ossKey);
            entity.setChunkCount(0);
            entity.setVectorCount(0);
            entity.setProcessStatus(DocumentStatus.PENDING.name());
            entity.setProcessProgress(0);
            entity.setUploadedBy(uploadedBy);
            entity.setUploadedAt(LocalDateTime.now());
            entity.setTenantId(kb.getTenantId());

            // 在同一事务中保存文档
            documentMapper.insert(entity);
            log.info("文档上传成功, documentId={}", entity.getId());

            // 异步处理文档
            documentProcessor.processDocument(kb.getTenantId(), entity.getId());

            return toVO(entity);

        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "文件保存失败", e);
        } finally {
            // 确保临时文件被删除 - 使用更安全的方式
            if (tempFile != null && tempFile.exists()) {
                try {
                    if (!FileUtil.del(tempFile)) {
                        log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("删除临时文件异常: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    @Override
    public List<Document> listByKbId(String kbId, String status) {
        validateKnowledgeBaseExists(kbId);
        List<DocumentEntity> documentEntities = documentMapper.listByKbId(kbId, status);
        return toVO(documentEntities);
    }

    @Override
    public Document getDocumentById(String kbId, String id) {
        return toVO(getDocumentInKnowledgeBase(kbId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String kbId, String id) {
        log.info("删除文档, kbId={}, id={}", kbId, id);
        DocumentEntity entity = getDocumentInKnowledgeBase(kbId, id);

        // 1. 获取关联知识库信息以确定向量表
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(entity.getKbId());

        // 2. 删除向量数据（遍历所有维度分表）
        if (kb != null) {
            for (Integer dimension : VectorDimensionProcessor.SUPPORTED_DIMENSIONS) {
                vectorRepository.deleteByDocumentId(dimension, id, entity.getTenantId());
            }
        }

        // 3. 删除文档块数据
        documentChunkMapper.deleteByDocumentIds(Collections.singletonList(id));

        // 4. 软删除文档
        entity.setDeletedAt(LocalDateTime.now());
        documentMapper.updateById(entity);

        // 5. 删除OSS文件
        if ("OSS".equals(entity.getStorageType())) {
            try {
                ossClient.delete(entity.getStoragePath());
                log.info("OSS文件已删除: {}", entity.getStoragePath());
            } catch (Exception e) {
                log.error("OSS文件删除失败: {}", entity.getStoragePath(), e);
            }
        }

        // 6. 更新知识库统计信息
        if (kb != null) {
            updateKnowledgeBaseStatistics(kbId);
        }
    }

    @Override
    public String getProcessStatus(String kbId, String id) {
        return getDocumentInKnowledgeBase(kbId, id).getProcessStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProcessStatus(String id, String processStatus, Integer progress, String errorMessage) {
        documentMapper.updateProcessStatus(id, processStatus, progress, errorMessage);
        if (DocumentStatus.COMPLETED.name().equals(processStatus)) {
            DocumentEntity entity = documentMapper.selectById(id);
            if (entity != null) {
                entity.setProcessedAt(LocalDateTime.now());
                documentMapper.updateById(entity);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(String kbId, String documentId) {
        if (documentId == null) {
            throw new AiBusinessException(AiErrorCode.DOCUMENT_NOT_FOUND, "文档ID不能为空");
        }
        DocumentEntity doc = getDocumentInKnowledgeBase(kbId, documentId);

        log.info("重新处理文档: kbId={}, docId={}", kbId, documentId);

        // 删除所有维度分表中的向量数据
        for (Integer dimension : VectorDimensionProcessor.SUPPORTED_DIMENSIONS) {
            vectorRepository.deleteByDocumentId(dimension, documentId, doc.getTenantId());
        }
        documentChunkMapper.deleteByDocumentIds(Collections.singletonList(documentId));

        doc.setProcessStatus(DocumentStatus.PENDING.name());
        doc.setProcessProgress(0);
        doc.setErrorMessage(null);
        documentMapper.updateById(doc);

        documentProcessor.processDocument(doc.getTenantId(), documentId);
    }

    @Override
    public IPage<Document> pageDocuments(DocumentQuery documentQuery) {
        validateKnowledgeBaseExists(documentQuery.getKbId());
        return pageForVO(documentQuery.getPage(), documentQuery.getLambdaQueryWrapper());
    }

    @Override
    public IPage<DocumentChunk> pageChunks(DocumentChunkQuery documentChunkQuery) {
        getDocumentInKnowledgeBase(documentChunkQuery.getKbId(), documentChunkQuery.getDocumentId());
        Page<DocumentChunkEntity> documentChunkEntityPage = documentChunkMapper.selectPage(
                documentChunkQuery.getPage(), documentChunkQuery.getLambdaQueryWrapper());
        return documentChunkEntityPage.convert(ConvertUtils::convert);
    }

    /**
     * 验证文件扩展名是否在白名单中
     * 防止上传恶意文件
     */
    private void validateFileExtension(String fileExtension) {
        if (fileExtension == null || fileExtension.isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "文件扩展名不能为空");
        }

        // 允许的文件扩展名白名单
        String[] allowedExtensions = {
            "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "json", "xml", "csv"
        };

        String lowerExtension = fileExtension.toLowerCase();
        for (String allowed : allowedExtensions) {
            if (allowed.equals(lowerExtension)) {
                return;
            }
        }

        throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "不支持的文件类型: " + fileExtension);
    }

    private void validateKnowledgeBaseExists(String kbId) {
        if (kbId == null) {
            throw new AiBusinessException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库ID不能为空");
        }
        if (knowledgeBaseMapper.selectById(kbId) == null) {
            throw AiBusinessException.knowledgeBaseNotFound(kbId);
        }
    }

    private DocumentEntity getDocumentInKnowledgeBase(String kbId, String documentId) {
        validateKnowledgeBaseExists(kbId);
        if (documentId == null) {
            throw new AiBusinessException(AiErrorCode.DOCUMENT_NOT_FOUND, "文档ID不能为空");
        }
        DocumentEntity entity = documentMapper.selectById(documentId);
        if (entity == null || entity.getDeletedAt() != null) {
            throw AiBusinessException.documentNotFound(documentId);
        }
        if (!kbId.equals(entity.getKbId())) {
            throw AiBusinessException.documentNotFound(documentId);
        }
        return entity;
    }

    private void updateKnowledgeBaseStatistics(String kbId) {
        Integer documentCount = documentMapper.countByKbId(kbId);
        long totalSizeBytes = 0L;
        long vectorCount = 0L;

        List<DocumentEntity> documents = documentMapper.listByKbId(kbId, null);
        int chunkCount = 0;
        for (DocumentEntity doc : documents) {
            chunkCount += doc.getChunkCount() != null ? doc.getChunkCount() : 0;
            vectorCount += doc.getVectorCount() != null ? doc.getVectorCount() : 0;
            totalSizeBytes += doc.getFileSize() != null ? doc.getFileSize() : 0;
        }

        knowledgeBaseMapper.updateStatistics(kbId, documentCount, chunkCount, vectorCount, totalSizeBytes);
        log.info(
                "知识库统计信息已更新, kbId={}, documentCount={}, chunkCount={}, vectorCount={}, totalSizeBytes={}",
                kbId,
                documentCount,
                chunkCount,
                vectorCount,
                totalSizeBytes);
    }
}
