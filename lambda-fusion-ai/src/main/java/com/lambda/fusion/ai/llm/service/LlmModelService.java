package com.lambda.fusion.ai.llm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.llm.model.CreateLlmModel;
import com.lambda.fusion.ai.llm.model.LlmModelPage;
import com.lambda.fusion.ai.llm.model.UpdateLlmModel;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;

/**
 * @author Jin
 */
public interface LlmModelService {

    Page<LlmModelEntity> page(LlmModelPage query);

    LlmModelEntity get(String id);

    LlmModelEntity create(CreateLlmModel dto);

    void update(String id, UpdateLlmModel dto);

    void delete(String id);

    /**
     * 按主键加载（含禁用记录），限定当前租户；不存在抛出业务异常。供运行时解析器使用。
     */
    LlmModelEntity loadById(String id);
}
