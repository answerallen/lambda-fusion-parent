package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库 Mapper接口
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {

    /**
     * 根据租户ID分页查询知识库
     */
    @Select("<script>" + "SELECT * FROM ai_knowledge_base "
            + "WHERE tenant_id = #{tenantId} "
            + "<if test='status != null and status != \"\"'> "
            + "AND status = #{status} "
            + "</if> "
            + "AND deleted_at IS NULL "
            + "ORDER BY created_at DESC"
            + "</script>")
    Page<KnowledgeBaseEntity> pageByTenantId(
            Page<KnowledgeBaseEntity> page, @Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 根据kbId查询知识库
     */
    @Select("SELECT * FROM ai_knowledge_base WHERE kb_id = #{kbId} AND deleted_at IS NULL LIMIT 1")
    KnowledgeBaseEntity selectByKbId(@Param("kbId") String kbId);

    /**
     * 根据租户ID查询知识库列表
     */
    @Select("<script>" + "SELECT * FROM ai_knowledge_base "
            + "WHERE tenant_id = #{tenantId} "
            + "<if test='status != null and status != \"\"'> "
            + "AND status = #{status} "
            + "</if> "
            + "AND deleted_at IS NULL "
            + "ORDER BY created_at DESC"
            + "</script>")
    List<KnowledgeBaseEntity> listByTenantId(@Param("tenantId") Long tenantId, @Param("status") String status);
}
