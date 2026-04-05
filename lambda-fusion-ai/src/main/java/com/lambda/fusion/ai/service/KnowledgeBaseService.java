package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateKnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.KnowledgeBaseQuery;
import com.lambda.fusion.ai.model.UpdateKnowledgeBase;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 知识库 Service 接口
 *
 * @author Jin
 */
public interface KnowledgeBaseService extends IService<KnowledgeBaseEntity> {

    /**
     * 创建知识库
     *
     * @param dto 创建知识库 DTO
     * @return 知识库 VO
     */
    KnowledgeBase createKnowledgeBase(CreateKnowledgeBase dto);

    /**
     * 更新知识库
     *
     * @param id  知识库 ID
     * @param dto 更新知识库 DTO
     */
    void updateKnowledgeBase(String id, UpdateKnowledgeBase dto);

    /**
     * 根据 ID 查询知识库
     *
     * @param id 知识库 ID
     * @return 知识库 VO
     */
    KnowledgeBase getKnowledgeBaseById(String id);

    /**
     * 删除知识库(软删除)
     *
     * @param id 知识库 ID
     */
    void deleteKnowledgeBase(String id);

    /**
     * 根据租户 ID 查询知识库列表
     *
     * @param tenantId 租户 ID
     * @param status   状态(可选)
     * @return 知识库列表
     */
    List<KnowledgeBase> listByTenantId(String tenantId, String status);

    /**
     * 分页查询知识库
     *
     * @param knowledgeBaseQuery KnowledgeBaseQuery
     * @return 知识库分页列表
     */
    IPage<KnowledgeBase> pageKnowledgeBases(@Valid KnowledgeBaseQuery knowledgeBaseQuery);
}
