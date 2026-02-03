package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.LlmModelEntity;
import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.model.dto.RegisterModelDTO;
import com.lambda.fusion.ai.model.vo.LlmModelVO;
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
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModelEntity> implements LlmModelService {

    private final LlmModelMapper llmModelMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmModelVO registerModel(RegisterModelDTO dto) {
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
    public void updateModel(Long id, RegisterModelDTO dto) {
        LlmModelEntity entity = llmModelMapper.selectById(id);
        BeanUtils.copyProperties(dto, entity);
        llmModelMapper.updateById(entity);
    }

    @Override
    public LlmModelVO getModelById(Long id) {
        return entityToVO(llmModelMapper.selectById(id));
    }

    @Override
    public List<LlmModelVO> listAll() {
        return llmModelMapper.selectList(null).stream().map(this::entityToVO).collect(Collectors.toList());
    }

    @Override
    public void deleteModel(Long id) {
        llmModelMapper.deleteById(id);
    }

    private LlmModelVO entityToVO(LlmModelEntity entity) {
        LlmModelVO vo = new LlmModelVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setApiKeyEncrypted(null); // 不返回加密的API Key
        return vo;
    }
}
