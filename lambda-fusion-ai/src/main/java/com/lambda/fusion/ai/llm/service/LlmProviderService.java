package com.lambda.fusion.ai.llm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.llm.model.CreateLlmProvider;
import com.lambda.fusion.ai.llm.model.LlmProviderPageQuery;
import com.lambda.fusion.ai.llm.model.UpdateLlmProvider;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;

/**
 * @author Jin
 */
public interface LlmProviderService {

    Page<LlmProviderEntity> page(LlmProviderPageQuery query);

    LlmProviderEntity get(String id);

    LlmProviderEntity create(CreateLlmProvider dto);

    void update(String id, UpdateLlmProvider dto);

    void delete(String id);

    /**
     * 按主键加载（含禁用记录），限定当前租户；不存在抛出业务异常。供运行时解析器使用。
     */
    LlmProviderEntity loadById(String id);
}
