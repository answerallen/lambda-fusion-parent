package com.lambda.fusion.ai.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.agent.runtime.KnowledgeFactory;
import com.lambda.fusion.ai.agent.runtime.ModelClientFactory;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.llm.mapper.LlmModelMapper;
import com.lambda.fusion.ai.llm.model.LlmModel;
import com.lambda.fusion.ai.llm.model.RegisterModel;
import com.lambda.fusion.ai.llm.model.UpdateModel;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.llm.security.KeyEncryptionService;
import com.lambda.fusion.ai.llm.service.LlmModelService;
import com.lambda.fusion.ai.llm.service.LlmProviderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModelEntity> implements LlmModelService {

    private final LlmModelMapper llmModelMapper;
    private final @Lazy ModelClientFactory modelClientFactory;
    private final KeyEncryptionService keyEncryptionService;
    private final LlmProviderService llmProviderService;
    private final @Lazy KnowledgeFactory knowledgeFactory;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModel registerModel(RegisterModel registerModel) {
        LlmModelEntity entity = registerModel.toEntity();
        llmProviderService.validateProviderSupport(entity.getProvider(), entity.getModelType());
        normalizeApiKey(entity);
        entity.setEnabled(true);
        entity.setIsDefault(false);
        entity.setTotalCalls(0L);
        entity.setTotalTokens(0L);
        entity.setTotalCost(BigDecimal.ZERO);
        llmModelMapper.insert(entity);
        return toLlmModel(entity);
    }

    @Override
    public void updateModel(String id, UpdateModel updateModel) {
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }
        LlmModelEntity existing = llmModelMapper.selectById(id);
        if (existing == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        String targetProvider =
                StringUtils.hasText(updateModel.getProvider()) ? updateModel.getProvider() : existing.getProvider();
        String targetModelType =
                StringUtils.hasText(updateModel.getModelType()) ? updateModel.getModelType() : existing.getModelType();
        llmProviderService.validateProviderSupport(targetProvider, targetModelType);

        LlmModelEntity entity = updateModel.toEntity();
        entity.setId(id);
        normalizeApiKey(entity);
        llmModelMapper.updateById(entity);
        clearRuntimeCache(existing.getModelType(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultModel(String id) {
        if (!StringUtils.hasText(id)) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity target = llmModelMapper.selectById(id);
        if (target == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_DISABLED, id);
        }

        List<LlmModelEntity> previousDefaults = llmModelMapper.selectList(new LambdaQueryWrapper<LlmModelEntity>()
                .eq(LlmModelEntity::getModelType, target.getModelType())
                .eq(LlmModelEntity::getIsDefault, true)
                .eq(StringUtils.hasText(target.getTenantId()), LlmModelEntity::getTenantId, target.getTenantId())
                .isNull(!StringUtils.hasText(target.getTenantId()), LlmModelEntity::getTenantId));

        llmModelMapper.update(
                null,
                new LambdaUpdateWrapper<LlmModelEntity>()
                        .eq(LlmModelEntity::getModelType, target.getModelType())
                        .eq(
                                StringUtils.hasText(target.getTenantId()),
                                LlmModelEntity::getTenantId,
                                target.getTenantId())
                        .isNull(!StringUtils.hasText(target.getTenantId()), LlmModelEntity::getTenantId)
                        .set(LlmModelEntity::getIsDefault, false));

        llmModelMapper.update(
                null,
                new LambdaUpdateWrapper<LlmModelEntity>()
                        .eq(LlmModelEntity::getId, id)
                        .set(LlmModelEntity::getIsDefault, true));

        previousDefaults.stream()
                .map(LlmModelEntity::getId)
                .filter(previousId -> !id.equals(previousId))
                .forEach(previousId -> clearRuntimeCache(target.getModelType(), previousId));
        clearRuntimeCache(target.getModelType(), id);
    }

    @Override
    public LlmModel getModelById(String id) {
        // 验证输入参数
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        return toLlmModel(entity);
    }

    @Override
    public List<LlmModel> listAll() {
        return llmModelMapper.selectList(null).stream().map(this::toLlmModel).collect(Collectors.toList());
    }

    @Override
    public void deleteModel(String id) {
        // 验证输入参数
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        llmModelMapper.deleteById(id);
        clearRuntimeCache(entity.getModelType(), id);
    }

    private LlmModel toLlmModel(LlmModelEntity entity) {
        LlmModel llmModel = ConvertUtils.convert(entity);
        llmModel.setApiKeyEncrypted(null);
        return llmModel;
    }

    private void normalizeApiKey(LlmModelEntity entity) {
        if (!StringUtils.hasText(entity.getApiKeyEncrypted())) {
            return;
        }
        entity.setApiKeyEncrypted(keyEncryptionService.encrypt(entity.getApiKeyEncrypted()));
    }

    private void clearRuntimeCache(String modelType, String modelId) {
        modelClientFactory.invalidateModelCache(modelId);
        if ("EMBEDDING".equalsIgnoreCase(modelType)) {
            // embedding 模型配置变更：失效所有 KB 的 SimpleKnowledge 缓存（含旧 EmbeddingModel），下次访问重建
            knowledgeFactory.invalidateAll();
        }
    }
}
