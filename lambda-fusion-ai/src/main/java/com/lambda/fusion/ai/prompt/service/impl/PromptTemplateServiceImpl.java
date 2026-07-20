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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** 模板变量占位符 {@code {{variable}}}。 */
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

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

        // 模板渲染：{{variable}} -> variables.get(variable)
        try {
            return render(templateContent, variables);
        } catch (Exception e) {
            log.error("模板渲染失败，templateId: {}, error: {}", templateId, e.getMessage(), e);
            throw new AiBusinessException(AiErrorCode.TEMPLATE_RENDER_FAILED, "模板渲染失败: " + e.getMessage());
        }
    }

    /**
     * {@code {{variable}}} 渲染：按 variables Map 替换占位符；未提供变量的占位符保留原样（容错可选变量）。
     */
    private String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = variables.get(key);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : matcher.group(0);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
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
