package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.model.CreateTemplate;
import com.lambda.fusion.ai.model.PromptTemplate;
import com.lambda.fusion.ai.model.UpdateTemplate;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.service.PromptTemplateService;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beetl.core.GroupTemplate;
import org.beetl.core.Template;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplateEntity>
        implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;
    private final GroupTemplate groupTemplate;

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

        // 使用 Beetl 模板引擎渲染
        try {
            // 将 {{variable}} 格式转换为 ${variable} 格式（Beetl 默认语法）
            String beetlTemplate = convertToBeetlSyntax(templateContent);

            Template template = groupTemplate.getTemplate(beetlTemplate);

            // 绑定变量
            if (variables != null) {
                variables.forEach(template::binding);
            }

            // 渲染模板
            StringWriter writer = new StringWriter();
            template.renderTo(writer);
            return writer.toString();

        } catch (Exception e) {
            log.error("模板渲染失败，templateId: {}, error: {}", templateId, e.getMessage(), e);
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, "模板渲染失败: " + e.getMessage());
        }
    }

    /**
     * 将 {{variable}} 格式转换为 ${variable} 格式
     */
    private String convertToBeetlSyntax(String template) {
        if (template == null) {
            return "";
        }
        // 将 {{variable}} 替换为 ${variable}
        return template.replaceAll("\\{\\{(\\w+)\\}\\}", "\\${$1}");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplate updateTemplate(String id, UpdateTemplate dto) {
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
        return entityToVO(entity);
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
    public PromptTemplate getTemplateById(String id) {
        PromptTemplateEntity entity = promptTemplateMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND, id);
        }
        return entityToVO(entity);
    }

    private PromptTemplate entityToVO(PromptTemplateEntity entity) {
        PromptTemplate vo = new PromptTemplate();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
