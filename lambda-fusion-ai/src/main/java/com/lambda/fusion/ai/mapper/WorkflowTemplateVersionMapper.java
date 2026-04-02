package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.WorkflowTemplateVersionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流模板版本历史 Mapper
 */
@Mapper
public interface WorkflowTemplateVersionMapper extends BaseMapper<WorkflowTemplateVersionEntity> {

    /**
     * 根据模板ID查询所有版本
     */
    @Select(
            "SELECT * FROM ai_workflow_template_version WHERE template_id = #{templateId} AND deleted = 0 ORDER BY create_time DESC")
    List<WorkflowTemplateVersionEntity> selectByTemplateId(@Param("templateId") Long templateId);

    /**
     * 查询指定版本的模板
     */
    @Select(
            "SELECT * FROM ai_workflow_template_version WHERE template_id = #{templateId} AND version = #{version} AND deleted = 0")
    WorkflowTemplateVersionEntity selectByTemplateIdAndVersion(
            @Param("templateId") Long templateId, @Param("version") String version);

    /**
     * 获取模板的最新版本号
     */
    @Select(
            "SELECT version FROM ai_workflow_template_version WHERE template_id = #{templateId} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    String selectLatestVersion(@Param("templateId") Long templateId);

    /**
     * 统计模板的版本数量
     */
    @Select("SELECT COUNT(*) FROM ai_workflow_template_version WHERE template_id = #{templateId} AND deleted = 0")
    int countByTemplateId(@Param("templateId") Long templateId);

    /**
     * 删除指定模板的所有版本历史
     */
    @Update("UPDATE ai_workflow_template_version SET deleted = 1 WHERE template_id = #{templateId}")
    void deleteByTemplateId(@Param("templateId") Long templateId);
}
