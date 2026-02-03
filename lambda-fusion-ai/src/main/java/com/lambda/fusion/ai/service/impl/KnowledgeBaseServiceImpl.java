package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.service.KnowledgeBaseService;
import com.lambda.fusion.ai.support.resolver.VectorTableNameResolver;
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
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBaseEntity>
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

        // 生成kbId (UUID)
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
    public void updateKnowledgeBase(Long id, UpdateKnowledgeBase dto) {
        log.info("更新知识库, id: {}", id);

        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("知识库不存在, id: " + id);
        }

        // 复制属性(忽略null值)
        BeanUtils.copyProperties(dto, entity, getNullPropertyNames(dto));

        // 更新
        knowledgeBaseMapper.updateById(entity);

        log.info("知识库更新成功, id: {}", id);
    }

    @Override
    public Page<KnowledgeBase> pageKnowledgeBases(Integer pageNum, Integer pageSize, Long tenantId, String status) {
        log.info("分页查询知识库, tenantId: {}, pageNum: {}, pageSize: {}", tenantId, pageNum, pageSize);

        Page<KnowledgeBaseEntity> page = new Page<>(pageNum, pageSize);
        Page<KnowledgeBaseEntity> resultPage = knowledgeBaseMapper.pageByTenantId(page, tenantId, status);

        // 转换为VO
        Page<KnowledgeBase> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<KnowledgeBase> voList =
                resultPage.getRecords().stream().map(this::entityToVO).collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public KnowledgeBase getKnowledgeBaseById(Long id) {
        log.info("查询知识库详情, id: {}", id);

        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("知识库不存在, id: " + id);
        }

        return entityToVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        log.info("删除知识库, id: {}", id);

        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("知识库不存在, id: " + id);
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

    /**
     * 实体转VO
     */
    private KnowledgeBase entityToVO(KnowledgeBaseEntity entity) {
        KnowledgeBase vo = new KnowledgeBase();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 获取对象中为null的属性名
     */
    private String[] getNullPropertyNames(Object source) {
        final org.springframework.beans.BeanWrapper src = new org.springframework.beans.BeanWrapperImpl(source);
        java.beans.PropertyDescriptor[] pds = src.getPropertyDescriptors();

        java.util.Set<String> emptyNames = new java.util.HashSet<>();
        for (java.beans.PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null) {
                emptyNames.add(pd.getName());
            }
        }

        return emptyNames.toArray(new String[0]);
    }
}
