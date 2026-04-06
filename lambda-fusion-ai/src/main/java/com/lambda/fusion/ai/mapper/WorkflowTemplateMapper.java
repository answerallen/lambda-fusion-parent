package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.WorkflowTemplateEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 工作流模板 Mapper
 */
@Mapper
@DS("#{@aiProperties.dataSource.name}")
public interface WorkflowTemplateMapper extends BaseMapper<WorkflowTemplateEntity> {

    /**
     * 根据模板编码查询
     */
    @Select("SELECT * FROM ai_workflow_template WHERE template_code = #{templateCode} AND deleted = 0")
    WorkflowTemplateEntity selectByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * 根据模板编码和版本查询
     */
    @Select(
            "SELECT * FROM ai_workflow_template WHERE template_code = #{templateCode} AND version = #{version} AND deleted = 0")
    WorkflowTemplateEntity selectByTemplateCodeAndVersion(
            @Param("templateCode") String templateCode, @Param("version") String version);

    /**
     * 分页查询模板列表
     */
    @Select("")
    IPage<WorkflowTemplateEntity> selectTemplatePage(
            Page<WorkflowTemplateEntity> page,
            @Param("tenantId") Long tenantId,
            @Param("category") String category,
            @Param("status") String status,
            @Param("keyword") String keyword);

    /**
     * 查询系统内置模板
     */
    @Select(
            "SELECT * FROM ai_workflow_template WHERE system_template = 1 AND enabled = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<WorkflowTemplateEntity> selectSystemTemplates();

    /**
     * 查询指定租户的模板
     */
    @Select(
            "SELECT * FROM ai_workflow_template WHERE tenant_id = #{tenantId} AND enabled = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<WorkflowTemplateEntity> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据分类查询模板
     */
    @Select(
            "SELECT * FROM ai_workflow_template WHERE category = #{category} AND enabled = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<WorkflowTemplateEntity> selectByCategory(@Param("category") String category);

    /**
     * 检查模板编码是否已存在
     */
    @Select("SELECT COUNT(*) FROM ai_workflow_template WHERE template_code = #{templateCode} AND deleted = 0")
    int countByTemplateCode(@Param("templateCode") String templateCode);
}
