package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBaseQuery;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 知识库Service接口
 *
 * @author Jin
 */
public interface KnowledgeBaseService extends IService<KnowledgeBaseEntity> {

    /**
     * 创建知识库
     *
     * @param dto 创建知识库DTO
     * @return 知识库VO
     */
    KnowledgeBase createKnowledgeBase(CreateKnowledgeBase dto);

    /**
     * 更新知识库
     *
     * @param id  知识库ID
     * @param dto 更新知识库DTO
     */
    void updateKnowledgeBase(Long id, UpdateKnowledgeBase dto);

    /**
     * 分页查询知识库
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param tenantId 租户ID
     * @param status   状态(可选)
     * @return 分页结果
     */
    Page<KnowledgeBase> pageKnowledgeBases(Integer pageNum, Integer pageSize, Long tenantId, String status);

    /**
     * 根据ID查询知识库
     *
     * @param id 知识库ID
     * @return 知识库VO
     */
    KnowledgeBase getKnowledgeBaseById(Long id);

    /**
     * 删除知识库(软删除)
     *
     * @param id 知识库ID
     */
    void deleteKnowledgeBase(Long id);

    /**
     * 根据租户ID查询知识库列表
     *
     * @param tenantId 租户ID
     * @param status   状态(可选)
     * @return 知识库列表
     */
    List<KnowledgeBase> listByTenantId(Long tenantId, String status);

    IPage<KnowledgeBase> pageKnowledgeBases(@Valid KnowledgeBaseQuery knowledgeBaseQuery);
}
