package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库 Mapper接口
 *
 * @author Jin
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {

    /**
     * 根据租户ID分页查询知识库
     *
     * @param page     分页对象
     * @param tenantId 租户ID
     * @param status   状态(可选)
     * @return 分页结果
     */
    Page<KnowledgeBaseEntity> pageByTenantId(
            Page<KnowledgeBaseEntity> page, @Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 根据kbId查询知识库
     *
     * @param kbId 知识库唯一标识
     * @return 知识库实体
     */
    KnowledgeBaseEntity selectByKbId(@Param("kbId") String kbId);

    /**
     * 根据租户ID查询知识库列表
     *
     * @param tenantId 租户ID
     * @param status   状态(可选)
     * @return 知识库列表
     */
    List<KnowledgeBaseEntity> listByTenantId(@Param("tenantId") Long tenantId, @Param("status") String status);
}
