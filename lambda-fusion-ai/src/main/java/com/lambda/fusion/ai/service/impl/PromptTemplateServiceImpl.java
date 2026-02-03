package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.model.dto.CreateTemplateDTO;
import com.lambda.fusion.ai.model.vo.PromptTemplateVO;
import com.lambda.fusion.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplateEntity>
        implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptTemplateVO createTemplate(CreateTemplateDTO dto) {
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
        PromptTemplateEntity entity = promptTemplateMapper.selectById(templateId);
        String template = entity.getTemplateContent();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return template;
    }

    @Override
    public List<PromptTemplateVO> listByCategory(String category) {
        return promptTemplateMapper.listByCategory(category).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PromptTemplateVO> listSystemTemplates() {
        return promptTemplateMapper.listSystemTemplates().stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    private PromptTemplateVO entityToVO(PromptTemplateEntity entity) {
        PromptTemplateVO vo = new PromptTemplateVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
