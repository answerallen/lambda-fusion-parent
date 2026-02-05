package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBaseQuery;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.service.KnowledgeBaseService;
import com.lambda.fusion.ai.support.resolver.VectorTableNameResolver;
import com.lambda.fusion.core.service.AbstractCrudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库Service实现类
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends AbstractCrudService<KnowledgeBaseEntity, KnowledgeBase, KnowledgeBaseMapper>
        implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorTableNameResolver vectorTableNameResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase createKnowledgeBase(CreateKnowledgeBase dto) {
        log.info("创建知识库: {}", dto.getName());

        // 创建实体
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        BeanUtils.copyProperties(dto, entity);

        // 生成kbId(UUID)
        entity.setKbId(IdUtil.fastSimpleUUID());

        // 根据embedding_dimension确定vector_table_name
        String vectorTableName = vectorTableNameResolver.resolve(dto.getEmbeddingDimension());
        entity.setVectorTableName(vectorTableName);

        // 设置默认值
        entity.setStatus("ACTIVE");
        entity.setDocumentCount(0);
        entity.setChunkCount(0);
        entity.setVectorCount(0L);
        entity.setTotalSizeBytes(0L);

        // 保存
        knowledgeBaseMapper.insert(entity);

        log.info("知识库创建成功, kbId: {}, id: {}", entity.getKbId(), entity.getId());

        // 转换为VO返回
        return entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, UpdateKnowledgeBase updateKnowledgeBase) {
        log.info("更新知识库, id: {}", id);
        KnowledgeBaseEntity entity = updateKnowledgeBase.toEntity();
        entity.setId(id);
        int updated = knowledgeBaseMapper.updateById(entity);
        log.info("知识库更新成功, id: {} updated: {}", id,updated);
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

        // 软删除
        entity.setStatus("DELETED");
        entity.setDeletedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(entity);

        log.info("知识库删除成功, id: {}", id);
    }

    @Override
    public List<KnowledgeBase> listByTenantId(Long tenantId, String status) {
        log.info("查询知识库列表, tenantId: {}, status: {}", tenantId, status);

        List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.listByTenantId(tenantId, status);

        return entities.stream().map(this::entityToVO).collect(Collectors.toList());
    }

    @Override
    public IPage<KnowledgeBase> pageKnowledgeBases(KnowledgeBaseQuery knowledgeBaseQuery) {
        return pageForVO(knowledgeBaseQuery.getPage(), knowledgeBaseQuery.getLambdaQueryWrapper());
    }

    /**
     * 实体转VO
     */
    private KnowledgeBase entityToVO(KnowledgeBaseEntity entity) {
        KnowledgeBase vo = new KnowledgeBase();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
