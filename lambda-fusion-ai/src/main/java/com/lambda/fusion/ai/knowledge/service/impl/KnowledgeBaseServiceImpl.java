package com.lambda.fusion.ai.knowledge.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.knowledge.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.knowledge.mapper.DocumentMapper;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.mapper.VectorRepository;
import com.lambda.fusion.ai.knowledge.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.knowledge.model.KnowledgeBase;
import com.lambda.fusion.ai.knowledge.model.KnowledgeBaseQuery;
import com.lambda.fusion.ai.knowledge.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentEntity;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.knowledge.service.KnowledgeBaseService;
import com.lambda.fusion.ai.knowledge.vector.VectorDimensionProcessor;
import com.lambda.fusion.core.service.AbstractCrudService;
import com.lambda.fusion.core.utils.AuthUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库Service实现类
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl
        extends AbstractCrudService<KnowledgeBaseEntity, KnowledgeBase, KnowledgeBaseMapper>
        implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorRepository vectorRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase createKnowledgeBase(CreateKnowledgeBase createKnowledgeBase) {
        log.info("创建知识库: {}", createKnowledgeBase.getName());

        // 创建实体
        KnowledgeBaseEntity entity = createKnowledgeBase.toEntity();

        entity.setTenantId(AuthUtils.getTenantId());
        entity.setOwnerUserId(AuthUtils.getUser().getName());

        // 设置默认值
        entity.setStatus("ACTIVE");
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setVectorCount(0L);
        entity.setTotalSizeBytes(0L);

        // 保存
        knowledgeBaseMapper.insert(entity);
        log.info("知识库创建成功, id: {}", entity.getId());

        return ConvertUtils.convert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(String id, UpdateKnowledgeBase updateKnowledgeBase) {
        log.info("更新知识库, id: {}", id);
        KnowledgeBaseEntity existing = knowledgeBaseMapper.selectById(id);
        if (existing == null) {
            throw AiBusinessException.knowledgeBaseNotFound(id);
        }
        KnowledgeBaseEntity entity = updateKnowledgeBase.toEntity();
        entity.setId(id);
        int updated = knowledgeBaseMapper.updateById(entity);
        log.info("知识库更新成功, id: {} updated: {}", id, updated);
    }

    @Override
    public KnowledgeBase getKnowledgeBaseById(String id) {
        KnowledgeBase knowledgeBase = getByIdForVO(id);
        if (knowledgeBase == null) {
            throw AiBusinessException.knowledgeBaseNotFound(id);
        }
        return knowledgeBase;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(String id) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.knowledgeBaseNotFound(id);
        }

        List<DocumentEntity> documents = documentMapper.listByKbId(id, null);
        if (!documents.isEmpty()) {
            List<String> documentIds =
                    documents.stream().map(DocumentEntity::getId).collect(Collectors.toList());

            // 删除所有维度分表中的向量数据
            for (Integer dimension : VectorDimensionProcessor.SUPPORTED_DIMENSIONS) {
                vectorRepository.deleteByKbId(dimension, id);
            }

            documentChunkMapper.deleteByDocumentIds(documentIds);

            documentMapper.deleteByKbIdBatch(List.of(id));
        }

        // 重置统计信息
        entity.setStatus("DELETED");
        entity.setDeletedAt(LocalDateTime.now());
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setVectorCount(0L);
        entity.setTotalSizeBytes(0L);
        knowledgeBaseMapper.updateById(entity);

        log.info("知识库删除成功, id: {}", id);
    }

    @Override
    public List<KnowledgeBase> listByTenantId(String tenantId, String status) {
        log.info("查询知识库列表, tenantId: {}, status: {}", tenantId, status);

        List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.listByTenantId(tenantId, status);

        return ConvertUtils.convertList(entities);
    }

    @Override
    public IPage<KnowledgeBase> pageKnowledgeBases(KnowledgeBaseQuery knowledgeBaseQuery) {
        return pageForVO(knowledgeBaseQuery.getPage(), knowledgeBaseQuery.getLambdaQueryWrapper());
    }
}
