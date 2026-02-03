package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.DocumentEntity;
import com.lambda.fusion.ai.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.enums.DocumentStatus;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.vo.DocumentVO;
import com.lambda.fusion.ai.service.DocumentService;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
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
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Value("${lambda.fusion.ai.document.base-path:/data/ai-documents}")
    private String basePath;

    @Value("${lambda.fusion.ai.document.max-file-size:10485760}")
    private Long maxFileSize;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO uploadDocument(Long kbId, MultipartFile file, Long uploadedBy) {
        log.info("上传文档到知识库: kbId={}, fileName={}", kbId, file.getOriginalFilename());

        // 验证知识库存在
        KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在, kbId: " + kbId);
        }

        // 验证文件大小
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + maxFileSize + " bytes");
        }

        // 计算文件哈希
        String fileHash;
        try {
            fileHash = DigestUtil.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("计算文件哈希失败", e);
        }

        // 检查是否重复
        DocumentEntity existingDoc = documentMapper.selectByFileHash(fileHash, kbId);
        if (existingDoc != null) {
            log.warn("文档已存在, fileHash={}, documentId={}", fileHash, existingDoc.getDocumentId());
            return entityToVO(existingDoc);
        }

        // 保存文件到本地
        String fileName = file.getOriginalFilename();
        String fileExtension = FileUtil.extName(fileName);
        String relativePath = kbId + "/" + IdUtil.fastSimpleUUID() + "." + fileExtension;
        String fullPath = basePath + "/" + relativePath;

        try {
            File destFile = new File(fullPath);
            FileUtil.mkParentDirs(destFile);
            file.transferTo(destFile);
            log.info("文件保存成功: {}", fullPath);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }

        // 创建文档实体
        DocumentEntity entity = new DocumentEntity();
        entity.setKbId(kbId);
        entity.setDocumentId(IdUtil.fastSimpleUUID());
        entity.setFileName(fileName);
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

        // 保存到数据库
        documentMapper.insert(entity);

        log.info("文档上传成功, documentId={}, id={}", entity.getDocumentId(), entity.getId());

        // TODO: 发送异步任务进行文档解析(后续实现)

        return entityToVO(entity);
    }

    @Override
    public Page<DocumentVO> pageDocuments(Integer pageNum, Integer pageSize, Long kbId, String status) {
        log.info("分页查询文档, kbId={}, pageNum={}, pageSize={}", kbId, pageNum, pageSize);

        Page<DocumentEntity> page = new Page<>(pageNum, pageSize);
        Page<DocumentEntity> resultPage = documentMapper.pageByKbId(page, kbId, status);

        // 转换为VO
        Page<DocumentVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<DocumentVO> voList =
                resultPage.getRecords().stream().map(this::entityToVO).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public List<DocumentVO> listByKbId(Long kbId, String status) {
        log.info("查询文档列表, kbId={}, status={}", kbId, status);

        List<DocumentEntity> entities = documentMapper.listByKbId(kbId, status);

        return entities.stream().map(this::entityToVO).collect(Collectors.toList());
    }

    @Override
    public DocumentVO getDocumentById(Long id) {
        log.info("查询文档详情, id={}", id);

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

        // 软删除
        entity.setDeletedAt(LocalDateTime.now());
        documentMapper.updateById(entity);

        // 删除物理文件
        if ("LOCAL".equals(entity.getStorageType())) {
            File file = new File(entity.getStorageUrl());
            if (file.exists()) {
                FileUtil.del(file);
                log.info("物理文件已删除: {}", entity.getStorageUrl());
            }
        }

        log.info("文档删除成功, id={}", id);
    }

    @Override
    public DocumentVO getProcessStatus(Long id) {
        log.info("查询文档处理状态, id={}", id);

        DocumentEntity entity = documentMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("文档不存在, id: " + id);
        }

        return entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProcessStatus(Long id, String processStatus, Integer progress, String errorMessage) {
        log.info("更新文档处理状态, id={}, status={}, progress={}", id, processStatus, progress);

        documentMapper.updateProcessStatus(id, processStatus, progress, errorMessage);

        // 如果处理完成，更新处理完成时间
        if (DocumentStatus.COMPLETED.name().equals(processStatus)) {
            DocumentEntity entity = documentMapper.selectById(id);
            if (entity != null) {
                entity.setProcessedAt(LocalDateTime.now());
                documentMapper.updateById(entity);
            }
        }
    }

    /**
     * 实体转VO
     */
    private DocumentVO entityToVO(DocumentEntity entity) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
