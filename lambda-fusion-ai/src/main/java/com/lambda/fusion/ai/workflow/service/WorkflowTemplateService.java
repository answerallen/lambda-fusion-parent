package com.lambda.fusion.ai.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateEntity;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateVersionEntity;
import java.util.List;

/**
 * 工作流模板服务接口
 */
public interface WorkflowTemplateService {

    WorkflowTemplateEntity createTemplate(WorkflowTemplateEntity template);

    WorkflowTemplateEntity updateTemplate(String id, WorkflowTemplateEntity template);

    void deleteTemplate(String id);

    WorkflowTemplateEntity getTemplateById(String id);

    WorkflowTemplateEntity getTemplateByCode(String templateCode);

    WorkflowTemplateEntity getTemplateByCodeAndVersion(String templateCode, String version);

    IPage<WorkflowTemplateEntity> listTemplates(
            Page<WorkflowTemplateEntity> page, String tenantId, String category, String status, String keyword);

    List<WorkflowTemplateEntity> listSystemTemplates();

    WorkflowTemplateEntity publishTemplate(String id);

    WorkflowTemplateEntity deprecateTemplate(String id);

    WorkflowTemplateEntity copyTemplate(String id, String newCode, String newName);

    List<WorkflowTemplateVersionEntity> getTemplateVersions(String templateId);

    WorkflowTemplateEntity rollbackToVersion(String templateId, String version);

    String exportTemplate(String id);

    WorkflowTemplateEntity importTemplate(String json, String tenantId);

    boolean validateTemplate(String definition);
}
