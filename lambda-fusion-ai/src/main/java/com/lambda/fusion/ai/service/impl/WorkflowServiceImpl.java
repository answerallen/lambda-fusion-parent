package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.agent.model.EdgeDefinition;
import com.lambda.fusion.ai.agent.model.GraphDefinition;
import com.lambda.fusion.ai.agent.model.NodeDefinition;
import com.lambda.fusion.ai.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.mapper.PromptTemplateMapper;
import com.lambda.fusion.ai.mapper.WorkflowMapper;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, WorkflowEntity> implements WorkflowService {

    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final AgentToolProvider agentToolProvider;
    private final AgentGraphFactory agentGraphFactory;
    private final LlmModelMapper llmModelMapper;
    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public boolean save(WorkflowEntity entity) {
        validateWorkflowEntity(entity, null);
        return super.save(entity);
    }

    @Override
    public boolean updateById(WorkflowEntity entity) {
        if (entity == null || entity.getId() == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "更新工作流时ID不能为空");
        }
        WorkflowEntity existing = getById(entity.getId());
        if (existing == null) {
            throw AiBusinessException.workflowNotFound(entity.getId());
        }
        validateWorkflowEntity(entity, existing);
        return super.updateById(entity);
    }

    private void validateWorkflowEntity(WorkflowEntity entity, WorkflowEntity existing) {
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流配置不能为空");
        }
        String workflowName =
                StringUtils.hasText(entity.getName()) ? entity.getName() : existing == null ? null : existing.getName();
        if (!StringUtils.hasText(workflowName)) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流名称不能为空");
        }
        String graphJson = StringUtils.hasText(entity.getGraphJson())
                ? entity.getGraphJson()
                : existing == null ? null : existing.getGraphJson();
        if (!StringUtils.hasText(graphJson)) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流图配置不能为空");
        }
        GraphDefinition definition = parseGraphDefinition(graphJson);
        validateGraphDefinition(definition);
        validateNodeRuntimeDependencies(definition);
        try {
            agentGraphFactory.buildFromDefinition(definition).precompile();
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, e, "工作流图编译失败");
        }
    }

    private GraphDefinition parseGraphDefinition(String graphJson) {
        try {
            return objectMapper.readValue(graphJson, GraphDefinition.class);
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, e, "工作流图JSON格式无效");
        }
    }

    private void validateGraphDefinition(GraphDefinition definition) {
        if (definition == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流图配置不能为空");
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流至少需要一个节点");
        }
        if (!StringUtils.hasText(definition.getEntryPoint())) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流入口节点不能为空");
        }

        Set<String> availableNodeTypes = applicationContext.getBeansOfType(AgentNode.class).values().stream()
                .map(AgentNode::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> availableConditionTypes =
                applicationContext.getBeansOfType(ConditionEvaluator.class).values().stream()
                        .map(ConditionEvaluator::getType)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> nodeIds = new LinkedHashSet<>();
        List<String> duplicateNodeIds = new ArrayList<>();
        for (NodeDefinition node : definition.getNodes()) {
            if (node == null) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流节点不能为空");
            }
            if (!StringUtils.hasText(node.getId())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流节点ID不能为空");
            }
            if (!nodeIds.add(node.getId())) {
                duplicateNodeIds.add(node.getId());
            }
            if (!StringUtils.hasText(node.getType())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "节点类型不能为空: " + node.getId());
            }
            if (!availableNodeTypes.contains(node.getType())) {
                throw new AiBusinessException(
                        AiErrorCode.WORKFLOW_CONFIG_INVALID, "节点类型不存在: " + node.getType() + ", 节点ID: " + node.getId());
            }
        }
        if (!duplicateNodeIds.isEmpty()) {
            throw new AiBusinessException(
                    AiErrorCode.WORKFLOW_CONFIG_INVALID, "存在重复的节点ID: " + String.join(", ", duplicateNodeIds));
        }
        if (!nodeIds.contains(definition.getEntryPoint())) {
            throw new AiBusinessException(
                    AiErrorCode.WORKFLOW_CONFIG_INVALID, "入口节点不存在: " + definition.getEntryPoint());
        }

        if (definition.getEdges() == null) {
            return;
        }
        for (EdgeDefinition edge : definition.getEdges()) {
            if (edge == null) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "工作流连线不能为空");
            }
            if (!StringUtils.hasText(edge.getSource())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "连线源节点不能为空");
            }
            if (!StringUtils.hasText(edge.getTarget())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "连线目标节点不能为空");
            }
            if (!nodeIds.contains(edge.getSource())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "连线源节点不存在: " + edge.getSource());
            }
            if (!nodeIds.contains(edge.getTarget()) && !"END".equals(edge.getTarget())) {
                throw new AiBusinessException(AiErrorCode.WORKFLOW_CONFIG_INVALID, "连线目标节点不存在: " + edge.getTarget());
            }
            if (StringUtils.hasText(edge.getConditionType())
                    && !availableConditionTypes.contains(edge.getConditionType())) {
                throw new AiBusinessException(
                        AiErrorCode.WORKFLOW_CONFIG_INVALID,
                        "条件类型不存在: " + edge.getConditionType() + ", 连线: " + edge.getSource() + " -> "
                                + edge.getTarget());
            }
        }
    }

    private void validateNodeRuntimeDependencies(GraphDefinition definition) {
        for (NodeDefinition node : definition.getNodes()) {
            Map<String, Object> properties = node.getProperties();
            if (properties == null || properties.isEmpty()) {
                continue;
            }
            validateModelReference(node, properties);
            validatePromptTemplateReference(node, properties);
            validateAllowedTools(node, properties);
        }
    }

    private void validateModelReference(NodeDefinition node, Map<String, Object> properties) {
        String modelId = resolveId(properties, "llmModelId", "modelId");
        if (modelId == null) {
            return;
        }
        LlmModelEntity model = llmModelMapper.selectById(modelId);
        if (model == null) {
            throw AiBusinessException.llmModelNotFound(modelId);
        }
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new AiBusinessException(
                    AiErrorCode.WORKFLOW_CONFIG_INVALID, "节点引用的模型未启用: " + modelId + ", 节点ID: " + node.getId());
        }
    }

    private void validatePromptTemplateReference(NodeDefinition node, Map<String, Object> properties) {
        String promptTemplateId = resolveId(properties, "promptTemplateId", "systemPromptTemplateId");
        if (promptTemplateId == null) {
            return;
        }
        PromptTemplateEntity template = promptTemplateMapper.selectById(promptTemplateId);
        if (template == null) {
            throw new AiBusinessException(
                    AiErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "节点引用的提示词模板不存在: " + promptTemplateId + ", 节点ID: " + node.getId());
        }
        if (Boolean.FALSE.equals(template.getEnabled())) {
            throw new AiBusinessException(
                    AiErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点引用的提示词模板未启用: " + promptTemplateId + ", 节点ID: " + node.getId());
        }
    }

    private void validateAllowedTools(NodeDefinition node, Map<String, Object> properties) {
        Set<String> toolNames = resolveToolNames(properties);
        for (String toolName : toolNames) {
            if (!agentToolProvider.hasTool(toolName)) {
                throw new AiBusinessException(
                        AiErrorCode.WORKFLOW_CONFIG_INVALID, "节点引用了不存在的工具: " + toolName + ", 节点ID: " + node.getId());
            }
        }
    }

    private String resolveId(Map<String, Object> properties, String... keys) {
        Object value = firstNonNull(properties, keys);
        if (value instanceof Number number) {
            return number.toString();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    private Set<String> resolveToolNames(Map<String, Object> properties) {
        Object value = firstNonNull(properties, "allowedTools", "toolNames", "tools");
        if (value == null) {
            return Set.of();
        }
        Set<String> toolNames = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    toolNames.add(item.toString().trim());
                }
            }
            return toolNames;
        }
        for (String item : value.toString().split(",")) {
            if (StringUtils.hasText(item)) {
                toolNames.add(item.trim());
            }
        }
        return toolNames;
    }

    private Object firstNonNull(Map<String, Object> properties, String... keys) {
        for (String key : keys) {
            if (properties.containsKey(key) && properties.get(key) != null) {
                return properties.get(key);
            }
        }
        return null;
    }
}
