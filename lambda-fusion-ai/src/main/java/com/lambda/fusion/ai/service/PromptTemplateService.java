package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.model.dto.CreateTemplateDTO;
import com.lambda.fusion.ai.model.vo.PromptTemplateVO;
import java.util.List;
import java.util.Map;

public interface PromptTemplateService extends IService<PromptTemplateEntity> {
    PromptTemplateVO createTemplate(CreateTemplateDTO dto);

    String renderTemplate(Long templateId, Map<String, Object> variables);

    List<PromptTemplateVO> listByCategory(String category);

    List<PromptTemplateVO> listSystemTemplates();
}
