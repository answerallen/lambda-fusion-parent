package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.model.CreateTemplate;
import com.lambda.fusion.ai.model.PromptTemplate;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.service.PromptTemplateService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplateEntity>
        implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptTemplate createTemplate(CreateTemplate dto) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setTemplateId(IdUtil.fastSimpleUUID());
        entity.setEnabled(true);
        entity.setUsageCount(0L);
        promptTemplateMapper.insert(entity);
        return entityToVO(entity);
    }

    @Override
    public String renderTemplate(Long templateId, Map<String, Object> variables) {
        // 验证输入参数
        if (templateId == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板ID不能为空");
        }

        PromptTemplateEntity entity = promptTemplateMapper.selectById(templateId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, templateId);
        }

        String template = entity.getTemplateContent();
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }
        return template;
    }

    @Override
    public List<PromptTemplate> listByCategory(String category) {
        return promptTemplateMapper.listByCategory(category).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromptTemplate> listSystemTemplates() {
        return promptTemplateMapper.listSystemTemplates().stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    private PromptTemplate entityToVO(PromptTemplateEntity entity) {
        PromptTemplate vo = new PromptTemplate();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
