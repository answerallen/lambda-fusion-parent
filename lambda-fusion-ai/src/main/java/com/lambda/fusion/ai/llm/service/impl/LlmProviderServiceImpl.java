package com.lambda.fusion.ai.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.AiConstants.ModelType;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.mapper.LlmModelMapper;
import com.lambda.fusion.ai.llm.mapper.LlmModelTypeProviderMapper;
import com.lambda.fusion.ai.llm.mapper.LlmProviderMapper;
import com.lambda.fusion.ai.llm.model.CreateLlmProvider;
import com.lambda.fusion.ai.llm.model.LlmProvider;
import com.lambda.fusion.ai.llm.model.UpdateLlmProvider;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.model.entity.LlmModelTypeProviderEntity;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;
import com.lambda.fusion.ai.llm.service.LlmProviderService;
import com.lambda.fusion.core.utils.AuthUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl extends ServiceImpl<LlmProviderMapper, LlmProviderEntity>
        implements LlmProviderService {

    private final LlmProviderMapper llmProviderMapper;
    private final LlmModelTypeProviderMapper llmModelTypeProviderMapper;
    private final LlmModelMapper llmModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CreateLlmProvider request) {
        String tenantId = AuthUtils.getTenantId();
        String code = normalizeCode(request.getCode());
        validateModelTypes(request.getModelTypes());

        LlmProviderEntity existing = llmProviderMapper.selectVisibleByCode(code, tenantId);
        if (existing != null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "提供商编码已存在: " + code);
        }

        LlmProviderEntity entity = request.toEntity();
        entity.setCode(code);
        applyDefaults(entity);
        if (!StringUtils.hasText(entity.getTenantId())) {
            entity.setTenantId(tenantId);
        }
        llmProviderMapper.insert(entity);
        replaceModelTypes(entity.getCode(), entity.getTenantId(), request.getModelTypes());
        return entity.getCode();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String code, UpdateLlmProvider request) {
        LlmProviderEntity entity = getOrThrow(code);
        validateModelTypes(request.getModelTypes());

        if (request.getDisplayName() != null) {
            entity.setDisplayName(request.getDisplayName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }

        applyDefaults(entity);
        llmProviderMapper.updateById(entity);

        if (request.getModelTypes() != null) {
            replaceModelTypes(entity.getCode(), entity.getTenantId(), request.getModelTypes());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String code) {
        LlmProviderEntity entity = getOrThrow(code);

        Long modelCount = llmModelMapper.selectCount(
                new LambdaQueryWrapper<LlmModelEntity>().eq(LlmModelEntity::getProvider, entity.getCode()));
        if (modelCount != null && modelCount > 0) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "该提供商下仍存在模型配置，无法删除: " + entity.getCode());
        }

        llmModelTypeProviderMapper.delete(new LambdaQueryWrapper<LlmModelTypeProviderEntity>()
                .eq(LlmModelTypeProviderEntity::getProviderCode, entity.getCode())
                .and(wrapper -> buildTenantCondition(wrapper, entity.getTenantId())));
        llmProviderMapper.deleteById(entity.getCode());
    }

    @Override
    public List<LlmProvider> listAll() {
        String tenantId = AuthUtils.getTenantId();
        List<LlmProviderEntity> providers = llmProviderMapper.selectVisibleByTenantId(tenantId);
        List<LlmModelTypeProviderEntity> bindings = llmModelTypeProviderMapper.selectVisibleByTenantId(tenantId);
        Map<String, List<String>> providerModelTypes = bindings.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .collect(Collectors.groupingBy(
                        LlmModelTypeProviderEntity::getProviderCode,
                        LinkedHashMap::new,
                        Collectors.mapping(LlmModelTypeProviderEntity::getModelType, Collectors.toList())));

        return providers.stream()
                .map(provider -> toVo(provider, providerModelTypes.get(provider.getCode())))
                .toList();
    }

    @Override
    public void validateProviderSupport(String providerCode, String modelType) {
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(modelType)) {
            return;
        }
        LlmProviderEntity provider = getOrThrow(providerCode);
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "提供商未启用: " + providerCode);
        }
        boolean supported =
                llmModelTypeProviderMapper.selectVisibleByProviderCode(providerCode, AuthUtils.getTenantId()).stream()
                        .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                        .anyMatch(item -> modelType.equalsIgnoreCase(item.getModelType()));
        if (!supported) {
            throw new AiBusinessException(
                    AiErrorCode.INVALID_PARAMETER, "提供商不支持该模型类型: " + providerCode + " -> " + modelType);
        }
    }

    private LlmProviderEntity getOrThrow(String code) {
        String normalizedCode = normalizeCode(code);
        LlmProviderEntity entity = llmProviderMapper.selectVisibleByCode(normalizedCode, AuthUtils.getTenantId());
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "提供商不存在: " + normalizedCode);
        }
        validateTenantAccess(entity);
        return entity;
    }

    private LlmProvider toVo(LlmProviderEntity entity, List<String> modelTypes) {
        LlmProvider provider = ConvertUtils.convert(entity);
        provider.setModelTypes(modelTypes == null ? List.of() : modelTypes);
        return provider;
    }

    private void replaceModelTypes(String providerCode, String tenantId, List<String> modelTypes) {
        llmModelTypeProviderMapper.delete(new LambdaQueryWrapper<LlmModelTypeProviderEntity>()
                .eq(LlmModelTypeProviderEntity::getProviderCode, providerCode)
                .and(wrapper -> buildTenantCondition(wrapper, tenantId)));
        if (modelTypes == null || modelTypes.isEmpty()) {
            return;
        }
        int index = 0;
        for (String modelType : modelTypes) {
            LlmModelTypeProviderEntity entity = new LlmModelTypeProviderEntity();
            entity.setProviderCode(providerCode);
            entity.setModelType(modelType);
            entity.setEnabled(Boolean.TRUE);
            entity.setSort(index++);
            entity.setTenantId(tenantId);
            llmModelTypeProviderMapper.insert(entity);
        }
    }

    private void applyDefaults(LlmProviderEntity entity) {
        if (!StringUtils.hasText(entity.getDisplayName())) {
            entity.setDisplayName(entity.getCode());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
    }

    private void validateModelTypes(List<String> modelTypes) {
        if (modelTypes == null) {
            return;
        }
        List<String> invalid = new ArrayList<>();
        for (String modelType : modelTypes) {
            if (!StringUtils.hasText(modelType)) {
                continue;
            }
            try {
                ModelType.valueOf(modelType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                invalid.add(modelType);
            }
        }
        if (!invalid.isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "存在无效模型类型: " + invalid);
        }
    }

    private String normalizeCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase(Locale.ROOT) : code;
    }

    private void validateTenantAccess(LlmProviderEntity entity) {
        String currentTenantId = AuthUtils.getTenantId();
        if (!StringUtils.hasText(entity.getTenantId()) || !StringUtils.hasText(currentTenantId)) {
            return;
        }
        if (!currentTenantId.equals(entity.getTenantId())) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "提供商不存在: " + entity.getCode());
        }
    }

    private void buildTenantCondition(LambdaQueryWrapper<LlmModelTypeProviderEntity> wrapper, String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            wrapper.isNull(LlmModelTypeProviderEntity::getTenantId);
        } else {
            wrapper.eq(LlmModelTypeProviderEntity::getTenantId, tenantId);
        }
    }
}
