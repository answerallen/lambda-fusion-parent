package com.lambda.fusion.ai.apps.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.AiConstants.SandboxBackend;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.mapper.AppMapper;
import com.lambda.fusion.ai.apps.model.AppPageQuery;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.runtime.workspace.WorkspacePaths;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
public class AppServiceImpl implements AppService {

    private final AppMapper appMapper;
    private final LlmModelService llmModelService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspacePaths workspacePaths;
    private final AiProperties aiProperties;

    @Override
    public Page<AppEntity> page(AppPageQuery query) {
        return appMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public AppEntity get(String id) {
        return requireExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppEntity create(CreateApp dto) {
        llmModelService.loadById(dto.getModelId());
        String appType = validateAppType(dto.getAppType());
        String sandboxBackend = validateSandboxBackend(dto.getSandboxBackend());
        ensureNameUnique(dto.getName(), null);
        AppEntity entity = getAppEntity(dto, appType, sandboxBackend);
        appMapper.insert(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.app(entity.getId())); // 按应用粒度过期 Agent 缓存
        return entity;
    }

    private static AppEntity getAppEntity(CreateApp dto, String appType, String sandboxBackend) {
        AppEntity entity = new AppEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSystemPrompt(dto.getSystemPrompt());
        entity.setModelId(dto.getModelId());
        entity.setMaxIters(dto.getMaxIters());
        entity.setTemperature(dto.getTemperature());
        entity.setToolsAllow(dto.getToolsAllow());
        entity.setToolsDeny(dto.getToolsDeny());
        entity.setMcpServerIds(dto.getMcpServerIds());
        entity.setKnowledgeBaseIds(dto.getKnowledgeBaseIds());
        entity.setSkillsAllow(dto.getSkillsAllow());
        entity.setSkillsDeny(dto.getSkillsDeny());
        entity.setAppType(appType);
        entity.setSelfEvolve(dto.getSelfEvolve());
        entity.setSandboxBackend(sandboxBackend);
        entity.setAudience(StringUtils.defaultIfBlank(dto.getAudience(), "ALL"));
        entity.setEnabled(dto.getEnabled());
        entity.setCreatedBy(AuthUtils.getUser().getUsername());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateApp dto) {
        AppEntity entity = requireExists(id);
        if (StringUtils.isNotBlank(dto.getModelId()) && !dto.getModelId().equals(entity.getModelId())) {
            llmModelService.loadById(dto.getModelId());
            entity.setModelId(dto.getModelId());
        }
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(entity.getName())) {
            ensureNameUnique(dto.getName(), id);
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getSystemPrompt() != null) {
            entity.setSystemPrompt(dto.getSystemPrompt());
        }
        if (dto.getMaxIters() != null) {
            entity.setMaxIters(dto.getMaxIters());
        }
        if (dto.getTemperature() != null) {
            entity.setTemperature(dto.getTemperature());
        }
        if (dto.getToolsAllow() != null) {
            entity.setToolsAllow(dto.getToolsAllow());
        }
        if (dto.getToolsDeny() != null) {
            entity.setToolsDeny(dto.getToolsDeny());
        }
        if (dto.getMcpServerIds() != null) {
            entity.setMcpServerIds(dto.getMcpServerIds());
        }
        if (dto.getKnowledgeBaseIds() != null) {
            entity.setKnowledgeBaseIds(dto.getKnowledgeBaseIds());
        }
        if (dto.getSkillsAllow() != null) {
            entity.setSkillsAllow(dto.getSkillsAllow());
        }
        if (dto.getSkillsDeny() != null) {
            entity.setSkillsDeny(dto.getSkillsDeny());
        }
        // appType 创建后不可变；selfEvolve / sandboxBackend / audience 可调
        if (dto.getSelfEvolve() != null) {
            entity.setSelfEvolve(dto.getSelfEvolve());
        }
        if (StringUtils.isNotBlank(dto.getSandboxBackend())) {
            entity.setSandboxBackend(validateSandboxBackend(dto.getSandboxBackend()));
        }
        if (dto.getAudience() != null) {
            entity.setAudience(dto.getAudience());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.app(id)); // 按应用粒度过期 Agent 缓存
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AppEntity app = requireExists(id);
        if (AppType.WORKSPACE.getCode().equalsIgnoreCase(app.getAppType())) {
            workspacePaths.deleteAppWorkspaces(id);
        }
        appMapper.deleteById(id);
        eventPublisher.publishEvent(ConfigChangedEvent.app(id)); // 按应用粒度过期 Agent 缓存
    }

    @Override
    public AppEntity loadById(String id) {
        return requireExists(id);
    }

    @Override
    public List<AppEntity> listAvailable() {
        var user = AuthUtils.getUser();
        String userId = user.getUsername();
        Set<String> roles = user.getRoles();
        List<AppEntity> candidates = appMapper.selectList(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getEnabled, Boolean.TRUE)
                .and(w -> w.isNull(AppEntity::getOwnerId).or().eq(AppEntity::getOwnerId, userId)));
        return candidates.stream()
                .filter(app -> isAppVisible(app, roles, userId))
                .toList();
    }

    @Override
    public AppEntity loadAvailable(String appId) {
        AppEntity app = requireExists(appId);
        if (!Boolean.TRUE.equals(app.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.APP_DISABLED, appId);
        }
        var user = AuthUtils.getUser();
        if (!isAppVisible(app, user.getRoles(), user.getUsername())) {
            throw new AiBusinessException(AiErrorCode.APP_NOT_FOUND, appId);
        }
        return app;
    }

    /**
     * 应用可见性：独立应用仅所有者可见；平台应用按 audience + 角色（ALL=所有登录用户）。
     */
    private boolean isAppVisible(AppEntity app, Set<String> roles, String userId) {
        if (StringUtils.isNotBlank(app.getOwnerId())) {
            return app.getOwnerId().equals(userId);
        }
        String audience = StringUtils.defaultIfBlank(app.getAudience(), "ALL");
        if ("ALL".equalsIgnoreCase(audience)) {
            return true;
        }
        List<String> requiredRoles = "B".equalsIgnoreCase(audience)
                ? aiProperties.getAudience().getBRoles()
                : aiProperties.getAudience().getCRoles();
        return roles != null && requiredRoles != null && requiredRoles.stream().anyMatch(roles::contains);
    }

    private AppEntity requireExists(String id) {
        AppEntity entity = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>().eq(AppEntity::getId, id));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.APP_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String name, String excludeId) {
        boolean exists = appMapper.exists(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getName, name)
                .ne(excludeId != null, AppEntity::getId, excludeId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.APP_NAME_EXISTS, name);
        }
    }

    private String validateAppType(String appType) {
        String resolved = StringUtils.defaultIfBlank(appType, AppType.CHAT.getCode());
        if (AppType.of(resolved) == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "非法应用类型: " + appType);
        }
        return resolved;
    }

    private String validateSandboxBackend(String sandboxBackend) {
        String resolved = StringUtils.defaultIfBlank(sandboxBackend, SandboxBackend.HOST.getCode());
        if (SandboxBackend.of(resolved) == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "非法沙箱后端: " + sandboxBackend);
        }
        return resolved;
    }
}
