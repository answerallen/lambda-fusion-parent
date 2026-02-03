package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.LlmModel;
import com.lambda.fusion.ai.model.RegisterModel;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import java.util.List;

public interface LlmModelService extends IService<LlmModelEntity> {
    LlmModel registerModel(RegisterModel dto);

    void updateModel(Long id, RegisterModel dto);

    LlmModel getModelById(Long id);

    List<LlmModel> listAll();

    void deleteModel(Long id);
}
