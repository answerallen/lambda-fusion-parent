package com.lambda.fusion.ai.apps.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.AiConstants.SandboxBackend;
import com.lambda.fusion.ai.apps.mapper.AppMapper;
import com.lambda.fusion.ai.apps.model.AppPageQuery;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.runtime.event.AiConfigChangedEvent;
import com.lambda.fusion.ai.runtime.workspace.WorkspacePaths;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceScaffolder;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.time.LocalDateTime;
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
    private final WorkspaceScaffolder workspaceScaffolder;

    @Override
    public Page<AppEntity> page(AppPageQuery query) {
        return appMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public AppEntity get(String id) {
        return requireOwned(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppEntity create(CreateApp dto) {
        String tenantId = AuthUtils.getTenantId();
        // 校验模型存在且属于当前租户
        llmModelService.loadById(dto.getModelId());
        String appType = validateAppType(dto.getAppType());
        String sandboxBackend = validateSandboxBackend(dto.getSandboxBackend());
        ensureNameUnique(tenantId, dto.getName(), null);
        AppEntity entity = new AppEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setTenantId(tenantId);
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSystemPrompt(dto.getSystemPrompt());
        entity.setModelId(dto.getModelId());
        entity.setMaxIters(dto.getMaxIters());
        entity.setTemperature(dto.getTemperature());
        entity.setToolsAllow(dto.getToolsAllow());
        entity.setToolsDeny(dto.getToolsDeny());
        entity.setMcpServerIds(dto.getMcpServerIds());
        entity.setAppType(appType);
        entity.setSelfEvolve(dto.getSelfEvolve());
        entity.setSandboxBackend(sandboxBackend);
        entity.setEnabled(dto.getEnabled());
        entity.setCreatedBy(AuthUtils.getUser().getUsername());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        appMapper.insert(entity);
        if (AppType.WORKSPACE.getCode().equalsIgnoreCase(appType)) {
            try {
                workspaceScaffolder.scaffold(workspacePaths.resolveAppWorkspace(tenantId, entity.getId()), entity);
            } catch (IOException e) {
                throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, e);
            }
        }
        eventPublisher.publishEvent(AiConfigChangedEvent.app(entity.getId()));
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateApp dto) {
        AppEntity entity = requireOwned(id);
        if (StringUtils.isNotBlank(dto.getModelId()) && !dto.getModelId().equals(entity.getModelId())) {
            llmModelService.loadById(dto.getModelId());
            entity.setModelId(dto.getModelId());
        }
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(entity.getName())) {
            ensureNameUnique(entity.getTenantId(), dto.getName(), id);
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
        // appType 创建后不可变；selfEvolve / sandboxBackend 可调
        if (dto.getSelfEvolve() != null) {
            entity.setSelfEvolve(dto.getSelfEvolve());
        }
        if (StringUtils.isNotBlank(dto.getSandboxBackend())) {
            entity.setSandboxBackend(validateSandboxBackend(dto.getSandboxBackend()));
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(entity);
        eventPublisher.publishEvent(AiConfigChangedEvent.app(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        requireOwned(id);
        appMapper.deleteById(id);
        eventPublisher.publishEvent(AiConfigChangedEvent.app(id));
    }

    @Override
    public AppEntity loadById(String id) {
        return requireOwned(id);
    }

    private AppEntity requireOwned(String id) {
        AppEntity entity = appMapper.selectOne(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getId, id)
                .eq(AppEntity::getTenantId, AuthUtils.getTenantId()));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.APP_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String tenantId, String name, String excludeId) {
        boolean exists = appMapper.exists(new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getTenantId, tenantId)
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
