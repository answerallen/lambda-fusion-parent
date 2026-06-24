package com.lambda.fusion.ai.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateEntity;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowTemplateVersionEntity;
import com.lambda.fusion.ai.workflow.service.WorkflowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai/workflow-templates")
@Tag(name = "AI 工作流模板管理")
@RequiredArgsConstructor
@Validated
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    @PostMapping
    @Operation(summary = "创建工作流模板")
    public WorkflowTemplateEntity createTemplate(@RequestBody @Valid WorkflowTemplateEntity template) {
        return workflowTemplateService.createTemplate(template);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新工作流模板")
    public WorkflowTemplateEntity updateTemplate(
            @PathVariable @NotNull String id, @RequestBody @Valid WorkflowTemplateEntity template) {
        return workflowTemplateService.updateTemplate(id, template);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作流模板")
    public void deleteTemplate(@PathVariable @NotNull String id) {
        workflowTemplateService.deleteTemplate(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取工作流模板详情")
    public WorkflowTemplateEntity getTemplate(@PathVariable @NotNull String id) {
        return workflowTemplateService.getTemplateById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码获取工作流模板")
    public WorkflowTemplateEntity getTemplateByCode(@PathVariable @NotBlank String code) {
        return workflowTemplateService.getTemplateByCode(code);
    }

    @GetMapping("/code/{code}/version/{version}")
    @Operation(summary = "根据编码和版本获取工作流模板")
    public WorkflowTemplateEntity getTemplateByCodeAndVersion(
            @PathVariable @NotBlank String code, @PathVariable @NotBlank String version) {
        return workflowTemplateService.getTemplateByCodeAndVersion(code, version);
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询工作流模板")
    public IPage<WorkflowTemplateEntity> listTemplates(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "租户ID") @RequestParam(required = false) String tenantId,
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {
        Page<WorkflowTemplateEntity> page = new Page<>(pageNum, pageSize);
        return workflowTemplateService.listTemplates(page, tenantId, category, status, keyword);
    }

    @GetMapping("/system")
    @Operation(summary = "获取系统内置模板")
    public List<WorkflowTemplateEntity> listSystemTemplates() {
        return workflowTemplateService.listSystemTemplates();
    }

    @GetMapping("/categories")
    @Operation(summary = "获取模板分类列表")
    public List<String> listCategories() {
        return workflowTemplateService.listSystemTemplates().stream()
                .map(WorkflowTemplateEntity::getCategory)
                .distinct()
                .filter(c -> c != null && !c.isEmpty())
                .toList();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布工作流模板")
    public WorkflowTemplateEntity publishTemplate(@PathVariable @NotNull String id) {
        return workflowTemplateService.publishTemplate(id);
    }

    @PostMapping("/{id}/deprecate")
    @Operation(summary = "废弃工作流模板")
    public WorkflowTemplateEntity deprecateTemplate(@PathVariable @NotNull String id) {
        return workflowTemplateService.deprecateTemplate(id);
    }

    @PostMapping("/{id}/copy")
    @Operation(summary = "复制工作流模板")
    public WorkflowTemplateEntity copyTemplate(
            @PathVariable @NotNull String id, @RequestBody Map<String, String> params) {
        String newCode = params.get("newCode");
        String newName = params.get("newName");
        return workflowTemplateService.copyTemplate(id, newCode, newName);
    }

    @GetMapping("/{templateId}/versions")
    @Operation(summary = "获取模板版本历史")
    public List<WorkflowTemplateVersionEntity> getTemplateVersions(@PathVariable @NotNull String templateId) {
        return workflowTemplateService.getTemplateVersions(templateId);
    }

    @PostMapping("/{templateId}/rollback")
    @Operation(summary = "回滚到指定版本")
    public WorkflowTemplateEntity rollbackToVersion(
            @PathVariable @NotNull String templateId, @RequestBody Map<String, String> params) {
        String version = params.get("version");
        return workflowTemplateService.rollbackToVersion(templateId, version);
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出工作流模板")
    public String exportTemplate(@PathVariable @NotNull String id) {
        return workflowTemplateService.exportTemplate(id);
    }

    @PostMapping("/import")
    @Operation(summary = "导入工作流模板")
    public WorkflowTemplateEntity importTemplate(@RequestBody Map<String, Object> params) {
        String json = (String) params.get("json");
        String tenantId =
                params.get("tenantId") != null ? params.get("tenantId").toString() : null;
        return workflowTemplateService.importTemplate(json, tenantId);
    }

    @PostMapping("/validate")
    @Operation(summary = "验证工作流模板定义")
    public Boolean validateTemplate(@RequestBody Map<String, String> params) {
        String definition = params.get("definition");
        return workflowTemplateService.validateTemplate(definition);
    }
}
