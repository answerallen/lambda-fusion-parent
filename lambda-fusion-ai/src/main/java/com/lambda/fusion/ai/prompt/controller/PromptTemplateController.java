package com.lambda.fusion.ai.prompt.controller;

import com.lambda.fusion.ai.prompt.model.CreateTemplate;
import com.lambda.fusion.ai.prompt.model.PromptDefinition;
import com.lambda.fusion.ai.prompt.model.UpdateTemplate;
import com.lambda.fusion.ai.prompt.service.PromptTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/prompt-templates")
@Tag(name = "提示词模板管理")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @PostMapping
    @Operation(summary = "创建模板")
    public PromptDefinition create(@Valid @RequestBody CreateTemplate dto) {
        return promptTemplateService.createTemplate(dto);
    }

    @GetMapping
    @Operation(summary = "查询所有模板")
    public List<PromptDefinition> list(@RequestParam(required = false) String category) {
        if (category != null) {
            return promptTemplateService.listByCategory(category);
        }
        return promptTemplateService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询模板")
    public PromptDefinition getById(@PathVariable String id) {
        return promptTemplateService.getTemplateById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模板")
    public PromptDefinition update(@PathVariable String id, @Valid @RequestBody UpdateTemplate dto) {
        return promptTemplateService.updateTemplate(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板")
    public void delete(@PathVariable String id) {
        promptTemplateService.deleteTemplate(id);
    }

    @GetMapping("/system")
    @Operation(summary = "查询系统模板")
    public List<PromptDefinition> listSystem() {
        return promptTemplateService.listSystemTemplates();
    }

    @PostMapping("/{templateId}/render")
    @Operation(summary = "渲染模板")
    public String render(@PathVariable String templateId, @RequestBody Map<String, Object> variables) {
        return promptTemplateService.renderTemplate(templateId, variables);
    }
}
