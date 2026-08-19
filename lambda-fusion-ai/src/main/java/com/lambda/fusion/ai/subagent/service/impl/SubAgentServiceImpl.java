package com.lambda.fusion.ai.subagent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.AiConstants.SubAgentCategory;
import com.lambda.fusion.ai.AiConstants.SubAgentWorkspaceMode;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.subagent.mapper.SubAgentMapper;
import com.lambda.fusion.ai.subagent.model.CreateSubAgent;
import com.lambda.fusion.ai.subagent.model.SubAgentPage;
import com.lambda.fusion.ai.subagent.model.UpdateSubAgent;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import com.lambda.fusion.ai.subagent.service.SubAgentService;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class SubAgentServiceImpl implements SubAgentService {

    private final SubAgentMapper subAgentMapper;
    private final LlmModelService llmModelService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Page<SubAgentEntity> page(SubAgentPage query) {
        return subAgentMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public SubAgentEntity get(String id) {
        return requireExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubAgentEntity create(CreateSubAgent dto) {
        ensureNameUnique(dto.getName(), null);
        validateModel(dto.getModelId());
        String workspaceMode = validateWorkspaceMode(dto.getWorkspaceMode());
        SubAgentEntity entity = new SubAgentEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setTenantId(AuthUtils.getTenantId());
        entity.setCategory(SubAgentCategory.SUB_AGENT.getCode());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setPrompt(dto.getPrompt());
        entity.setModelId(dto.getModelId());
        entity.setSteps(dto.getSteps());
        entity.setTemperature(dto.getTemperature());
        entity.setTopP(dto.getTopP());
        entity.setToolsAllow(dto.getToolsAllow());
        entity.setSkillsAllow(dto.getSkillsAllow());
        entity.setWorkspaceMode(workspaceMode);
        entity.setRemark(dto.getRemark());
        entity.setEnabled(dto.getEnabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.insert(entity);
        publishChanged(); // 声明构建期固化，变更需全量重建 agent
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateSubAgent dto) {
        SubAgentEntity entity = requireExists(id);
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(entity.getName())) {
            ensureNameUnique(dto.getName(), id);
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getPrompt() != null) {
            entity.setPrompt(dto.getPrompt());
        }
        if (dto.getModelId() != null && !dto.getModelId().equals(entity.getModelId())) {
            validateModel(dto.getModelId());
            entity.setModelId(dto.getModelId());
        }
        if (dto.getSteps() != null) {
            entity.setSteps(dto.getSteps());
        }
        if (dto.getTemperature() != null) {
            entity.setTemperature(dto.getTemperature());
        }
        if (dto.getTopP() != null) {
            entity.setTopP(dto.getTopP());
        }
        if (dto.getToolsAllow() != null) {
            entity.setToolsAllow(dto.getToolsAllow());
        }
        if (dto.getSkillsAllow() != null) {
            entity.setSkillsAllow(dto.getSkillsAllow());
        }
        if (StringUtils.isNotBlank(dto.getWorkspaceMode())) {
            entity.setWorkspaceMode(validateWorkspaceMode(dto.getWorkspaceMode()));
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        subAgentMapper.updateById(entity);
        publishChanged();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        requireExists(id);
        subAgentMapper.deleteById(id);
        publishChanged();
    }

    @Override
    public SubAgentEntity loadById(String id) {
        return requireExists(id);
    }

    @Override
    public List<SubAgentEntity> listEnabledByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // 路由污染防线：主 Agent 路由只取 SUB_AGENT 分类，定时任务（SCHEDULED_TASK）不参与委派
        Map<String, SubAgentEntity> byId = subAgentMapper.selectByIds(ids).stream()
                .filter(entity -> Boolean.TRUE.equals(entity.getEnabled()))
                .filter(entity -> isSubAgentCategory(entity.getCategory()))
                .collect(Collectors.toMap(SubAgentEntity::getId, Function.identity()));
        // 按入参 ids 顺序输出（声明顺序即主 agent 可见的子代理顺序）
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }

    /** category 为空（存量数据）按 SUB_AGENT 处理，向后兼容。 */
    private static boolean isSubAgentCategory(String category) {
        return category == null || SubAgentCategory.SUB_AGENT.getCode().equalsIgnoreCase(category);
    }

    // 子代理声明在 agent 构建期固化，保存/删除/启停后全量失效 agent 缓存触发重建
    private void publishChanged() {
        eventPublisher.publishEvent(ConfigChangedEvent.all());
    }

    // 绑定模型存在且启用（空 = 继承主 agent 模型，不校验）
    private void validateModel(String modelId) {
        if (StringUtils.isBlank(modelId)) {
            return;
        }
        LlmModelEntity model = llmModelService.loadById(modelId);
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.SUB_AGENT_MODEL_INVALID, modelId);
        }
    }

    private String validateWorkspaceMode(String workspaceMode) {
        String resolved = StringUtils.defaultIfBlank(workspaceMode, SubAgentWorkspaceMode.ISOLATED.getCode());
        if (SubAgentWorkspaceMode.of(resolved) == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "非法子代理工作区模式: " + workspaceMode);
        }
        return resolved;
    }

    private SubAgentEntity requireExists(String id) {
        SubAgentEntity entity =
                subAgentMapper.selectOne(new LambdaQueryWrapper<SubAgentEntity>().eq(SubAgentEntity::getId, id));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.SUB_AGENT_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String name, String excludeId) {
        boolean exists = subAgentMapper.exists(new LambdaQueryWrapper<SubAgentEntity>()
                .eq(SubAgentEntity::getTenantId, AuthUtils.getTenantId())
                .eq(SubAgentEntity::getName, name)
                .ne(excludeId != null, SubAgentEntity::getId, excludeId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.SUB_AGENT_NAME_EXISTS, name);
        }
    }
}
