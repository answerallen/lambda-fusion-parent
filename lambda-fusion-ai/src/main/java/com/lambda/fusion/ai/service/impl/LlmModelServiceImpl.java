package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.commons.support.factory.ChatModelFactory;
import com.lambda.fusion.ai.commons.support.security.KeyEncryptionService;
import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.model.LlmModel;
import com.lambda.fusion.ai.model.RegisterModel;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.service.LlmModelService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@DS("@aiDataSourceProperties.defaultName")
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModelEntity> implements LlmModelService {

    private final LlmModelMapper llmModelMapper;
    private final @Lazy ChatModelFactory chatModelFactory;
    private final KeyEncryptionService keyEncryptionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModel registerModel(RegisterModel dto) {
        LlmModelEntity entity = new LlmModelEntity();
        BeanUtils.copyProperties(dto, entity);
        normalizeApiKey(entity);
        entity.setModelId(IdUtil.fastSimpleUUID());
        entity.setEnabled(true);
        entity.setIsDefault(false);
        entity.setTotalCalls(0L);
        entity.setTotalTokens(0L);
        llmModelMapper.insert(entity);
        return entityToVO(entity);
    }

    @Override
    public void updateModel(Long id, RegisterModel dto) {
        // 验证输入参数
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        BeanUtils.copyProperties(dto, entity);
        normalizeApiKey(entity);
        llmModelMapper.updateById(entity);
        chatModelFactory.invalidateModelCache(id);
    }

    @Override
    public LlmModel getModelById(Long id) {
        // 验证输入参数
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        return entityToVO(entity);
    }

    @Override
    public List<LlmModel> listAll() {
        return llmModelMapper.selectList(null).stream().map(this::entityToVO).collect(Collectors.toList());
    }

    @Override
    public void deleteModel(Long id) {
        // 验证输入参数
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.LLM_MODEL_NOT_FOUND, "模型ID不能为空");
        }

        LlmModelEntity entity = llmModelMapper.selectById(id);
        if (entity == null) {
            throw AiBusinessException.llmModelNotFound(id);
        }

        llmModelMapper.deleteById(id);
        chatModelFactory.invalidateModelCache(id);
    }

    private LlmModel entityToVO(LlmModelEntity entity) {
        LlmModel vo = new LlmModel();
        BeanUtils.copyProperties(entity, vo);
        vo.setApiKeyEncrypted(null); // 不返回加密的API Key
        return vo;
    }

    private void normalizeApiKey(LlmModelEntity entity) {
        if (!StringUtils.hasText(entity.getApiKeyEncrypted())) {
            return;
        }
        entity.setApiKeyEncrypted(keyEncryptionService.encrypt(entity.getApiKeyEncrypted()));
    }
}
