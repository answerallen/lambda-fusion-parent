package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.entity.LlmModelEntity;
import com.lambda.fusion.ai.model.dto.RegisterModelDTO;
import com.lambda.fusion.ai.model.vo.LlmModelVO;

import java.util.List;

public interface LlmModelService extends IService<LlmModelEntity> {
    LlmModelVO registerModel(RegisterModelDTO dto);

    void updateModel(Long id, RegisterModelDTO dto);

    LlmModelVO getModelById(Long id);

    List<LlmModelVO> listAll();

    void deleteModel(Long id);
}
