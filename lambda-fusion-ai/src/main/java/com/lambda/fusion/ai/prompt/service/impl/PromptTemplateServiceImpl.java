package com.lambda.fusion.ai.prompt.service.impl;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.prompt.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.prompt.model.CreateTemplate;
import com.lambda.fusion.ai.prompt.model.PromptDefinition;
import com.lambda.fusion.ai.prompt.model.UpdateTemplate;
import com.lambda.fusion.ai.prompt.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.prompt.service.PromptTemplateService;
import com.lambda.fusion.core.service.AbstractCrudService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl
        extends AbstractCrudService<PromptTemplateEntity, PromptDefinition, PromptTemplateMapper>
        implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptDefinition createTemplate(CreateTemplate createTemplate) {
        PromptTemplateEntity entity = createTemplate.toEntity();
        entity.setEnabled(true);
        entity.setUsageCount(0L);
        promptTemplateMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public String renderTemplate(String templateId, Map<String, Object> variables) {
        // 验证输入参数
        if (templateId == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板ID不能为空");
        }

        PromptTemplateEntity entity = promptTemplateMapper.selectById(templateId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, templateId);
        }

        String templateContent = entity.getTemplateContent();

        // 使用 LangChain4j 的 PromptTemplate 渲染（语法：{{variable}}）
        try {
            PromptTemplate promptTemplate = PromptTemplate.from(templateContent);
            Prompt prompt = promptTemplate.apply(variables);
            return prompt.text();

        } catch (Exception e) {
            log.error("模板渲染失败，templateId: {}, error: {}", templateId, e.getMessage(), e);
            throw new AiBusinessException(AiErrorCode.TEMPLATE_RENDER_FAILED, "模板渲染失败: " + e.getMessage());
        }
    }

    @Override
    public List<PromptDefinition> listByCategory(String category) {
        return promptTemplateMapper.listByCategory(category).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromptDefinition> listSystemTemplates() {
        return promptTemplateMapper.listSystemTemplates().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromptDefinition> listAll() {
        return listForVO();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptDefinition updateTemplate(String id, UpdateTemplate updateTemplate) {
        // 查询现有模板
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, id);
        }

        // 系统模板不允许修改
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_TEMPLATE_NOT_EDITABLE);
        }

        entity = updateTemplate.toEntity();

        promptTemplateMapper.updateById(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(String id) {
        // 查询现有模板
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, id);
        }

        // 系统模板不允许删除
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_TEMPLATE_NOT_EDITABLE);
        }

        promptTemplateMapper.deleteById(id);
    }

    @Override
    public PromptDefinition getTemplateById(String id) {
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, id);
        }
        return toVO(entity);
    }
}
