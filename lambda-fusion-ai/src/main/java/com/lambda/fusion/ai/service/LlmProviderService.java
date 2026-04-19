package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateLlmProvider;
import com.lambda.fusion.ai.model.LlmProvider;
import com.lambda.fusion.ai.model.UpdateLlmProvider;
import com.lambda.fusion.ai.model.entity.LlmProviderEntity;
import java.util.List;

public interface LlmProviderService extends IService<LlmProviderEntity> {
    String create(CreateLlmProvider request);

    void update(String code, UpdateLlmProvider request);

    void delete(String code);

    List<LlmProvider> listAll();

    void validateProviderSupport(String providerCode, String modelType);
}
