package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.support.vector.VectorDimensionService;
import com.lambda.fusion.ai.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.mapper.VectorRepository;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBaseQuery;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.service.KnowledgeBaseService;
import com.lambda.fusion.core.service.AbstractCrudService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
@DS("@aiDataSourceProperties.defaultName")
public class KnowledgeBaseServiceImpl
        extends AbstractCrudService<KnowledgeBaseEntity, KnowledgeBase, KnowledgeBaseMapper>
        implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final VectorRepository vectorRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase createKnowledgeBase(CreateKnowledgeBase dto) {
        log.info("创建知识库: {}", dto.getName());

        // 创建实体
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        BeanUtils.copyProperties(dto, entity);

        // 生成kbId(UUID)
        entity.setKbId(IdUtil.fastSimpleUUID());

        // 设置默认值
        entity.setStatus("ACTIVE");
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setVectorCount(0L);
        entity.setTotalSizeBytes(0L);

        // 保存
        knowledgeBaseMapper.insert(entity);

        log.info("知识库创建成功, kbId: {}, id: {}", entity.getKbId(), entity.getId());

        return ConvertUtils.convert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, UpdateKnowledgeBase updateKnowledgeBase) {
        log.info("更新知识库, id: {}", id);
        KnowledgeBaseEntity entity = updateKnowledgeBase.toEntity();
        entity.setId(id);
        int updated = knowledgeBaseMapper.updateById(entity);
        log.info("知识库更新成功, id: {} updated: {}", id, updated);
    }

    @Override
    public KnowledgeBase getKnowledgeBaseById(Long id) {
        KnowledgeBase knowledgeBase = getByIdForVO(id);
        if (knowledgeBase == null) {
            throw AiBusinessException.knowledgeBaseNotFound(id);
        }

        return knowledgeBase;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.knowledgeBaseNotFound(id);
        }

        List<DocumentEntity> documents = documentMapper.listByKbId(id, null);
        if (!documents.isEmpty()) {
            List<Long> documentIds =
                    documents.stream().map(DocumentEntity::getId).collect(Collectors.toList());

            // 删除所有维度分表中的向量数据
            for (Integer dimension : VectorDimensionService.SUPPORTED_DIMENSIONS) {
                vectorRepository.deleteByKbId(dimension, id);
            }

            documentChunkMapper.deleteByDocumentIds(documentIds);

            documentMapper.deleteByKbIdBatch(List.of(id));
        }

        entity.setStatus("DELETED");
        entity.setDeletedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);

        log.info("知识库删除成功, id: {}", id);
    }

    @Override
    public List<KnowledgeBase> listByTenantId(Long tenantId, String status) {
        log.info("查询知识库列表, tenantId: {}, status: {}", tenantId, status);

        List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.listByTenantId(tenantId, status);

        return ConvertUtils.convertList(entities);
    }

    @Override
    public IPage<KnowledgeBase> pageKnowledgeBases(KnowledgeBaseQuery knowledgeBaseQuery) {
        return pageForVO(knowledgeBaseQuery.getPage(), knowledgeBaseQuery.getLambdaQueryWrapper());
    }
}
