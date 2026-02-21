package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@DS("#{@aiDataSourceProperties.defaultName}")
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModelEntity> implements LlmModelService {

    private final LlmModelMapper llmModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModel registerModel(RegisterModel dto) {
        LlmModelEntity entity = new LlmModelEntity();
        BeanUtils.copyProperties(dto, entity);
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
        llmModelMapper.updateById(entity);
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
    }

    private LlmModel entityToVO(LlmModelEntity entity) {
        LlmModel vo = new LlmModel();
        BeanUtils.copyProperties(entity, vo);
        vo.setApiKeyEncrypted(null); // 不返回加密的API Key
        return vo;
    }
}
