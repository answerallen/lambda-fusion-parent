package com.lambda.fusion.ai.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.llm.model.CreateLlmProvider;
import com.lambda.fusion.ai.llm.model.LlmProvider;
import com.lambda.fusion.ai.llm.model.UpdateLlmProvider;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;
import java.util.List;

public interface LlmProviderService extends IService<LlmProviderEntity> {
    String create(CreateLlmProvider request);

    void update(String code, UpdateLlmProvider request);

    void delete(String code);

    List<LlmProvider> listAll();

    void validateProviderSupport(String providerCode, String modelType);
}
