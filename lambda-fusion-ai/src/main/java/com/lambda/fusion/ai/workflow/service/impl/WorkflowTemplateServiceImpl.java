package com.lambda.fusion.ai.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.workflow.mapper.WorkflowTemplateMapper;
import com.lambda.fusion.ai.workflow.mapper.WorkflowTemplateVersionMapper;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateEntity;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateVersionEntity;
import com.lambda.fusion.ai.workflow.service.WorkflowTemplateService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 工作流模板服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl implements WorkflowTemplateService {

    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowTemplateVersionMapper versionMapper;
    private final AgentGraphFactory graphFactory;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowTemplateEntity createTemplate(WorkflowTemplateEntity template) {
        // 检查模板编码是否已存在
        if (template.getTemplateCode() != null && !template.getTemplateCode().isEmpty()) {
            int count = templateMapper.countByTemplateCode(template.getTemplateCode());
            if (count > 0) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_TEMPLATE_CODE_EXISTS, template.getTemplateCode());
            }
        } else {
            // 自动生成模板编码
            template.setTemplateCode(generateTemplateCode());
        }

        // 设置默认版本
        if (template.getVersion() == null || template.getVersion().isEmpty()) {
            template.setVersion("1.0.0");
        }

        // 设置默认状态
        if (template.getStatus() == null) {
            template.setStatus("draft");
        }

        // 设置默认启用状态
        if (template.getEnabled() == null) {
            template.setEnabled(true);
        }

        // 设置默认系统模板标识
        if (template.getSystemTemplate() == null) {
            template.setSystemTemplate(false);
        }

        templateMapper.insert(template);

        // 创建初始版本记录
        createVersionRecord(template);

        log.info("创建工作流模板: id={}, code={}, name={}", template.getId(), template.getTemplateCode(), template.getName());
        return template;
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity updateTemplate(String id, WorkflowTemplateEntity template) {
        WorkflowTemplateEntity existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        // 检查模板编码是否被修改且与其他模板冲突
        if (template.getTemplateCode() != null && !template.getTemplateCode().equals(existing.getTemplateCode())) {
            int count = templateMapper.countByTemplateCode(template.getTemplateCode());
            if (count > 0) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_TEMPLATE_CODE_EXISTS, template.getTemplateCode());
            }
        }

        template.setId(id);
        templateMapper.updateById(template);

        // 创建新版本记录
        WorkflowTemplateEntity updated = templateMapper.selectById(id);
        createVersionRecord(updated);

        log.info("更新工作流模板: id={}, code={}", id, updated.getTemplateCode());
        return updated;
    }

    @Override
    @Transactional
    public void deleteTemplate(String id) {
        WorkflowTemplateEntity existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        // 逻辑删除
        templateMapper.deleteById(id);

        // 删除版本历史
        versionMapper.deleteByTemplateId(id);

        log.info("删除工作流模板: id={}, code={}", id, existing.getTemplateCode());
    }

    @Override
    public WorkflowTemplateEntity getTemplateById(String id) {
        return templateMapper.selectById(id);
    }

    @Override
    public WorkflowTemplateEntity getTemplateByCode(String templateCode) {
        return templateMapper.selectByTemplateCode(templateCode);
    }

    @Override
    public WorkflowTemplateEntity getTemplateByCodeAndVersion(String templateCode, String version) {
        return templateMapper.selectByTemplateCodeAndVersion(templateCode, version);
    }

    @Override
    public IPage<WorkflowTemplateEntity> listTemplates(
            Page<WorkflowTemplateEntity> page, String tenantId, String category, String status, String keyword) {
        LambdaQueryWrapper<WorkflowTemplateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, WorkflowTemplateEntity::getTenantId, tenantId)
                .eq(StringUtils.hasText(category), WorkflowTemplateEntity::getCategory, category)
                .eq(StringUtils.hasText(status), WorkflowTemplateEntity::getStatus, status)
                .and(StringUtils.hasText(keyword), query -> query.like(WorkflowTemplateEntity::getName, keyword)
                        .or()
                        .like(WorkflowTemplateEntity::getTemplateCode, keyword)
                        .or()
                        .like(WorkflowTemplateEntity::getDescription, keyword))
                .orderByDesc(WorkflowTemplateEntity::getCreatedAt);
        return templateMapper.selectPage(page, wrapper);
    }

    @Override
    public List<WorkflowTemplateEntity> listSystemTemplates() {
        return templateMapper.selectSystemTemplates();
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity publishTemplate(String id) {
        WorkflowTemplateEntity template = templateMapper.selectById(id);
        if (template == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        // 验证模板定义
        if (!validateTemplate(template.getDefinition())) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID);
        }

        template.setStatus("published");
        templateMapper.updateById(template);

        log.info("发布工作流模板: id={}, code={}", id, template.getTemplateCode());
        return template;
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity deprecateTemplate(String id) {
        WorkflowTemplateEntity template = templateMapper.selectById(id);
        if (template == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        template.setStatus("deprecated");
        template.setEnabled(false);
        templateMapper.updateById(template);

        log.info("废弃工作流模板: id={}, code={}", id, template.getTemplateCode());
        return template;
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity copyTemplate(String id, String newCode, String newName) {
        WorkflowTemplateEntity source = templateMapper.selectById(id);
        if (source == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        // 检查新编码是否已存在
        int count = templateMapper.countByTemplateCode(newCode);
        if (count > 0) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_TEMPLATE_CODE_EXISTS, newCode);
        }

        // 创建新模板
        WorkflowTemplateEntity copy = new WorkflowTemplateEntity();
        copy.setTemplateCode(newCode);
        copy.setName(newName);
        copy.setDescription(source.getDescription());
        copy.setCategory(source.getCategory());
        copy.setVersion("1.0.0");
        copy.setDefinition(source.getDefinition());
        copy.setInputSchema(source.getInputSchema());
        copy.setOutputSchema(source.getOutputSchema());
        copy.setVariables(source.getVariables());
        copy.setTags(source.getTags());
        copy.setSystemTemplate(false);
        copy.setEnabled(true);
        copy.setStatus("draft");
        copy.setTenantId(source.getTenantId());

        templateMapper.insert(copy);
        createVersionRecord(copy);

        log.info("复制工作流模板: sourceId={}, newId={}, newCode={}", id, copy.getId(), newCode);
        return copy;
    }

    @Override
    public List<WorkflowTemplateVersionEntity> getTemplateVersions(String templateId) {
        return versionMapper.selectByTemplateId(templateId);
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity rollbackToVersion(String templateId, String version) {
        WorkflowTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, templateId);
        }

        WorkflowTemplateVersionEntity versionEntity = versionMapper.selectByTemplateIdAndVersion(templateId, version);
        if (versionEntity == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_TEMPLATE_VERSION_NOT_FOUND, version);
        }

        // 生成新版本号
        String newVersion = generateNextVersion(template.getVersion());

        // 回滚内容
        template.setDefinition(versionEntity.getDefinition());
        template.setInputSchema(versionEntity.getInputSchema());
        template.setOutputSchema(versionEntity.getOutputSchema());
        template.setVariables(versionEntity.getVariables());
        template.setVersion(newVersion);
        template.setStatus("draft");
        templateMapper.updateById(template);

        // 创建新版本记录
        createVersionRecord(template);

        log.info("回滚工作流模板: templateId={}, fromVersion={}, toVersion={}", templateId, version, newVersion);
        return template;
    }

    @Override
    public String exportTemplate(String id) {
        WorkflowTemplateEntity template = templateMapper.selectById(id);
        if (template == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, id);
        }

        try {
            // 构建导出对象
            java.util.Map<String, Object> exportObj = new java.util.HashMap<>();
            exportObj.put("templateCode", template.getTemplateCode());
            exportObj.put("name", template.getName());
            exportObj.put("description", template.getDescription());
            exportObj.put("category", template.getCategory());
            exportObj.put("version", template.getVersion());
            exportObj.put("definition", objectMapper.readTree(template.getDefinition()));
            exportObj.put(
                    "inputSchema",
                    template.getInputSchema() != null ? objectMapper.readTree(template.getInputSchema()) : null);
            exportObj.put(
                    "outputSchema",
                    template.getOutputSchema() != null ? objectMapper.readTree(template.getOutputSchema()) : null);
            exportObj.put("variables", template.getVariables());
            exportObj.put("tags", template.getTags());
            exportObj.put("exportTime", LocalDateTime.now().toString());

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportObj);
        } catch (Exception e) {
            log.error("导出模板失败", e);
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, e);
        }
    }

    @Override
    @Transactional
    public WorkflowTemplateEntity importTemplate(String json, String tenantId) {
        try {
            JsonNode root = objectMapper.readTree(json);

            WorkflowTemplateEntity template = new WorkflowTemplateEntity();
            template.setTemplateCode(root.path("templateCode").asString(generateTemplateCode()));
            template.setName(root.path("name").asString("导入模板"));
            template.setDescription(root.path("description").asString(null));
            template.setCategory(root.path("category").asString(null));
            template.setVersion("1.0.0");
            template.setDefinition(root.path("definition").toString());
            template.setInputSchema(
                    root.path("inputSchema").isMissingNode()
                            ? null
                            : root.path("inputSchema").toString());
            template.setOutputSchema(
                    root.path("outputSchema").isMissingNode()
                            ? null
                            : root.path("outputSchema").toString());
            template.setVariables(root.path("variables").asString(null));
            template.setTags(root.path("tags").asString(null));
            template.setSystemTemplate(false);
            template.setEnabled(true);
            template.setStatus("draft");
            template.setTenantId(tenantId);

            // 检查模板编码是否已存在
            int count = templateMapper.countByTemplateCode(template.getTemplateCode());
            if (count > 0) {
                // 生成新的编码
                template.setTemplateCode(template.getTemplateCode() + "_" + System.currentTimeMillis());
            }

            templateMapper.insert(template);
            createVersionRecord(template);

            log.info("导入工作流模板: id={}, code={}", template.getId(), template.getTemplateCode());
            return template;
        } catch (Exception e) {
            log.error("导入模板失败", e);
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, e);
        }
    }

    @Override
    public boolean validateTemplate(String definition) {
        if (definition == null || definition.isEmpty()) {
            return false;
        }

        try {
            // 尝试构建图，验证定义是否有效
            graphFactory.buildFromDefinition(definition);
            return true;
        } catch (Exception e) {
            log.warn("模板定义验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成模板编码
     */
    private String generateTemplateCode() {
        return "WF_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * 创建版本记录
     */
    private void createVersionRecord(WorkflowTemplateEntity template) {
        WorkflowTemplateVersionEntity version = new WorkflowTemplateVersionEntity();
        version.setTemplateId(template.getId());
        version.setVersion(template.getVersion());
        version.setDescription("版本 " + template.getVersion());
        version.setDefinition(template.getDefinition());
        version.setInputSchema(template.getInputSchema());
        version.setOutputSchema(template.getOutputSchema());
        version.setVariables(template.getVariables());
        versionMapper.insert(version);
    }

    /**
     * 生成下一个版本号
     */
    private String generateNextVersion(String currentVersion) {
        try {
            String[] parts = currentVersion.split("\\.");
            if (parts.length >= 3) {
                int patch = Integer.parseInt(parts[2]);
                return parts[0] + "." + parts[1] + "." + (patch + 1);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return currentVersion + ".1";
    }
}
