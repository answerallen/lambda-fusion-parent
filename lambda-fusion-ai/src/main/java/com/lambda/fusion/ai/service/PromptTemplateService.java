package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateTemplate;
import com.lambda.fusion.ai.model.PromptTemplate;
import com.lambda.fusion.ai.model.UpdateTemplate;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import java.util.List;
import java.util.Map;

public interface PromptTemplateService extends IService<PromptTemplateEntity> {
    PromptTemplate createTemplate(CreateTemplate dto);

    PromptTemplate updateTemplate(String id, UpdateTemplate dto);

    void deleteTemplate(String id);

    PromptTemplate getTemplateById(String id);

    String renderTemplate(String templateId, Map<String, Object> variables);

    List<PromptTemplate> listByCategory(String category);

    List<PromptTemplate> listSystemTemplates();
}
