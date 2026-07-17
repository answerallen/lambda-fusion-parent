package com.lambda.fusion.ai.prompt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.prompt.model.CreateTemplate;
import com.lambda.fusion.ai.prompt.model.PromptDefinition;
import com.lambda.fusion.ai.prompt.model.UpdateTemplate;
import com.lambda.fusion.ai.prompt.model.entity.PromptTemplateEntity;
import java.util.List;
import java.util.Map;

public interface PromptTemplateService extends IService<PromptTemplateEntity> {
    PromptDefinition createTemplate(CreateTemplate dto);

    PromptDefinition updateTemplate(String id, UpdateTemplate dto);

    void deleteTemplate(String id);

    PromptDefinition getTemplateById(String id);

    String renderTemplate(String templateId, Map<String, Object> variables);

    List<PromptDefinition> listByCategory(String category);

    List<PromptDefinition> listSystemTemplates();

    List<PromptDefinition> listAll();
}
