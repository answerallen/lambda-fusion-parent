package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.Document;
import com.lambda.fusion.ai.model.DocumentChunk;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.repository.VectorRepository;
import com.lambda.fusion.ai.service.DocumentService;
import com.lambda.fusion.ai.support.enums.DocumentStatus;
import com.lambda.fusion.ai.support.processor.DocumentProcessor;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档Service实现类
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, DocumentEntity> implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorRepository vectorRepository;
    private final DocumentProcessor documentProcessor;

    @Value("${lambda.fusion.ai.document.base-path:/data/ai-documents}")
    private String basePath;

    @Value("${lambda.fusion.ai.document.max-file-size:10485760}")
    private Long maxFileSize;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document uploadDocument(Long kbId, MultipartFile file, Long uploadedBy) {
        log.info("上传文档到知识库: kbId={}, fileName={}", kbId, file.getOriginalFilename());

        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + maxFileSize + " bytes");
        }

        String fileHash;
        try {
            fileHash = DigestUtil.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("计算文件哈希失败", e);
        }

        DocumentEntity existingDoc = documentMapper.selectByFileHash(fileHash, kbId);
        if (existingDoc != null) {
            log.warn("文档已存在, fileHash={}, documentId={}", fileHash, existingDoc.getDocumentId());
            return entityToVO(existingDoc);
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = FileUtil.extName(originalFilename);
        String relativePath = kbId + "/" + IdUtil.fastSimpleUUID() + "." + fileExtension;
        String fullPath = basePath + "/" + relativePath;

        File destFile = new File(fullPath);
        FileUtil.mkParentDirs(destFile);

        try {
            file.transferTo(destFile);
            log.info("文件保存成功: {}", fullPath);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setKbId(kbId);
        entity.setDocumentId(IdUtil.fastSimpleUUID());
        entity.setFileName(originalFilename);
        entity.setFileType(fileExtension.toUpperCase());
        entity.setFileSize(file.getSize());
        entity.setFileHash(fileHash);
        entity.setStorageType("LOCAL");
        entity.setStoragePath(relativePath);
        entity.setStorageUrl(fullPath);
        entity.setChunkCount(0);
        entity.setVectorCount(0);
        entity.setProcessStatus(DocumentStatus.PENDING.name());
        entity.setProcessProgress(0);
        entity.setUploadedBy(uploadedBy);

        documentMapper.insert(entity);

        log.info("文档上传成功, documentId={}, id={}", entity.getDocumentId(), entity.getId());

        // 触发异步处理
        documentProcessor.processDocument(entity.getId());

        return entityToVO(entity);
    }

    @Override
    public Page<Document> pageDocuments(Integer pageNum, Integer pageSize, Long kbId, String status) {
        Page<DocumentEntity> page = new Page<>(pageNum, pageSize);
        Page<DocumentEntity> resultPage = documentMapper.pageByKbId(page, kbId, status);

        Page<Document> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::entityToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<Document> listByKbId(Long kbId, String status) {
        return documentMapper.listByKbId(kbId, status).stream().map(this::entityToVO).collect(Collectors.toList());
    }

    @Override
    public Document getDocumentById(Long id) {
        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在, id: " + id);
        }
        return entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        log.info("删除文档, id={}", id);
        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在, id: " + id);
        }

        // 1. 获取关联知识库信息以确定向量表
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(entity.getKbId());

        // 2. 删除向量数据
        if (kb != null && kb.getVectorTableName() != null) {
            vectorRepository.deleteByDocumentId(kb.getVectorTableName(), id);
        }

        // 3. 删除文档块数据
        documentChunkMapper.deleteByDocumentIds(Collections.singletonList(id));

        // 4. 软删除文档
        entity.setDeletedAt(LocalDateTime.now());
        documentMapper.updateById(entity);

        // 5. 删除物理文件
        if ("LOCAL".equals(entity.getStorageType())) {
            File file = new File(entity.getStorageUrl());
            if (file.exists()) {
                FileUtil.del(file);
                log.info("物理文件已删除: {}", entity.getStorageUrl());
            }
        }
    }

    @Override
    public Document getProcessStatus(Long id) {
        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在, id: " + id);
        }
        return entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProcessStatus(Long id, String processStatus, Integer progress, String errorMessage) {
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
    public Page<DocumentChunk> pageChunks(Long docId, Integer pageNum, Integer pageSize) {
        Page<DocumentChunkEntity> page = new Page<>(pageNum, pageSize);
        Page<DocumentChunkEntity> resultPage = documentChunkMapper.selectPage(page,
                new LambdaQueryWrapper<DocumentChunkEntity>()
                        .eq(DocumentChunkEntity::getDocumentId, docId)
                        .orderByAsc(DocumentChunkEntity::getChunkIndex));

        Page<DocumentChunk> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::chunkEntityToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public void reprocessDocument(Long id) {
        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在");
        }

        // 清理旧数据
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(entity.getKbId());
        if (kb != null && kb.getVectorTableName() != null) {
            vectorRepository.deleteByDocumentId(kb.getVectorTableName(), id);
        }
        documentChunkMapper.deleteByDocumentIds(Collections.singletonList(id));

        // 重新触发
        documentProcessor.processDocument(id);
    }

    private Document entityToVO(DocumentEntity entity) {
        Document vo = new Document();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private DocumentChunk chunkEntityToVO(DocumentChunkEntity entity) {
        DocumentChunk vo = new DocumentChunk();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
