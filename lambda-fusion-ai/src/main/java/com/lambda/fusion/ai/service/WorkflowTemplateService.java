package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.entity.WorkflowTemplateEntity;
import com.lambda.fusion.ai.model.entity.WorkflowTemplateVersionEntity;
import java.util.List;

/**
 * 工作流模板服务接口
 */
public interface WorkflowTemplateService {

    /**
     * 创建模板
     */
    WorkflowTemplateEntity createTemplate(WorkflowTemplateEntity template);

    /**
     * 更新模板
     */
    WorkflowTemplateEntity updateTemplate(Long id, WorkflowTemplateEntity template);

    /**
     * 删除模板
     */
    void deleteTemplate(Long id);

    /**
     * 根据ID查询模板
     */
    WorkflowTemplateEntity getTemplateById(Long id);

    /**
     * 根据模板编码查询
     */
    WorkflowTemplateEntity getTemplateByCode(String templateCode);

    /**
     * 根据模板编码和版本查询
     */
    WorkflowTemplateEntity getTemplateByCodeAndVersion(String templateCode, String version);

    /**
     * 分页查询模板
     */
    IPage<WorkflowTemplateEntity> listTemplates(
            Page<WorkflowTemplateEntity> page, Long tenantId, String category, String status, String keyword);

    /**
     * 查询系统内置模板
     */
    List<WorkflowTemplateEntity> listSystemTemplates();

    /**
     * 发布模板
     */
    WorkflowTemplateEntity publishTemplate(Long id);

    /**
     * 废弃模板
     */
    WorkflowTemplateEntity deprecateTemplate(Long id);

    /**
     * 复制模板
     */
    WorkflowTemplateEntity copyTemplate(Long id, String newCode, String newName);

    /**
     * 获取模板的所有版本
     */
    List<WorkflowTemplateVersionEntity> getTemplateVersions(Long templateId);

    /**
     * 回滚到指定版本
     */
    WorkflowTemplateEntity rollbackToVersion(Long templateId, String version);

    /**
     * 导出模板为JSON
     */
    String exportTemplate(Long id);

    /**
     * 从JSON导入模板
     */
    WorkflowTemplateEntity importTemplate(String json, Long tenantId);

    /**
     * 验证模板定义是否有效
     */
    boolean validateTemplate(String definition);
}
