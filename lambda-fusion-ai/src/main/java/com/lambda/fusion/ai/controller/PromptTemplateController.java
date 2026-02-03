package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.CreateTemplate;
import com.lambda.fusion.ai.model.PromptTemplate;
import com.lambda.fusion.ai.service.PromptTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prompt-templates")
@Tag(name = "提示词模板管理")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @PostMapping
    @Operation(summary = "创建模板")
    public PromptTemplate create(@Valid @RequestBody CreateTemplate dto) {
        return promptTemplateService.createTemplate(dto);
    }

    @GetMapping
    @Operation(summary = "查询所有模板")
    public List<PromptTemplate> list(@RequestParam(required = false) String category) {
        if (category != null) {
            return promptTemplateService.listByCategory(category);
        }
        promptTemplateService.list();
        return null;
    }

    @GetMapping("/system")
    @Operation(summary = "查询系统模板")
    public List<PromptTemplate> listSystem() {
        return promptTemplateService.listSystemTemplates();
    }

    @PostMapping("/{templateId}/render")
    @Operation(summary = "渲染模板")
    public String render(@PathVariable Long templateId, @RequestBody Map<String, Object> variables) {
        return promptTemplateService.renderTemplate(templateId, variables);
    }
}
