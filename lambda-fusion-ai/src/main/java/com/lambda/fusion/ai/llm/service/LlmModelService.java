package com.lambda.fusion.ai.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.llm.model.LlmModel;
import com.lambda.fusion.ai.llm.model.RegisterModel;
import com.lambda.fusion.ai.llm.model.UpdateModel;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import java.util.List;

public interface LlmModelService extends IService<LlmModelEntity> {
    LlmModel registerModel(RegisterModel dto);

    void updateModel(String id, UpdateModel dto);

    void setDefaultModel(String id);

    LlmModel getModelById(String id);

    List<LlmModel> listAll();

    void deleteModel(String id);
}
