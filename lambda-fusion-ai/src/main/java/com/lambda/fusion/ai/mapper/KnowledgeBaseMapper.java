package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.MapKey;
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
    List<KnowledgeBaseEntity> listByTenantId(@Param("tenantId") String tenantId, @Param("status") String status);

    /**
     * 统计租户知识库数
     * @param tenantId 租户ID
     * @return 知识库数量
     */
    Integer countByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 按租户ID和状态查询(优化版)
     * @param tenantId 租户ID
     * @param status 知识库状态
     * @return 知识库列表
     */
    List<KnowledgeBaseEntity> selectByTenantIdAndStatus(
            @Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 批量更新知识库状态
     * @param kbIds 知识库ID列表
     * @param status 新状态
     * @return 更新数量
     */
    int updateStatusBatch(@Param("kbIds") List<Long> kbIds, @Param("status") String status);

    /**
     * 查询过期知识库(超过90天未更新)
     * @return 过期知识库列表
     */
    List<KnowledgeBaseEntity> selectExpiredKnowledgeBases();

    /**
     * 统计租户各状态知识库数
     * @param tenantId 租户ID
     * @return Map<status, count>
     */
    @MapKey("status")
    List<Map<String, Object>> countByTenantIdGroupByStatus(@Param("tenantId") Long tenantId);

    /**
     * 按所有者查询知识库
     * @param ownerUserId 所有者用户ID
     * @return 知识库列表
     */
    List<KnowledgeBaseEntity> selectByOwnerUserId(@Param("ownerUserId") Long ownerUserId);
}
