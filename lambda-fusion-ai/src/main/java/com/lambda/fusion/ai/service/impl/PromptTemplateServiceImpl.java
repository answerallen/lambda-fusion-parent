package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.model.CreateTemplate;
import com.lambda.fusion.ai.model.PromptDefinition;
import com.lambda.fusion.ai.model.UpdateTemplate;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.service.PromptTemplateService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplateEntity>
        implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptDefinition createTemplate(CreateTemplate dto) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setTemplateId(IdUtil.fastSimpleUUID());
        entity.setEnabled(true);
        entity.setUsageCount(0L);
        promptTemplateMapper.insert(entity);
        return toPromptDefinition(entity);
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
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板渲染失败: " + e.getMessage());
        }
    }

    @Override
    public List<PromptDefinition> listByCategory(String category) {
        return promptTemplateMapper.listByCategory(category).stream()
                .map(this::toPromptDefinition)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromptDefinition> listSystemTemplates() {
        return promptTemplateMapper.listSystemTemplates().stream()
                .map(this::toPromptDefinition)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptDefinition updateTemplate(String id, UpdateTemplate dto) {
        // 查询现有模板
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, id);
        }

        // 系统模板不允许修改
        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new AiBusinessException(AiErrorCode.SYSTEM_TEMPLATE_NOT_EDITABLE);
        }

        // 更新字段
        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getCategory() != null) {
            entity.setCategory(dto.getCategory());
        }
        if (dto.getTemplateContent() != null) {
            entity.setTemplateContent(dto.getTemplateContent());
        }
        if (dto.getVariables() != null) {
            entity.setVariables(dto.getVariables());
        }
        if (dto.getIsPublic() != null) {
            entity.setIsPublic(dto.getIsPublic());
        }

        promptTemplateMapper.updateById(entity);
        return toPromptDefinition(entity);
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
        return toPromptDefinition(entity);
    }

    private PromptDefinition toPromptDefinition(PromptTemplateEntity entity) {
        return ConvertUtils.convert(entity);
    }
}
