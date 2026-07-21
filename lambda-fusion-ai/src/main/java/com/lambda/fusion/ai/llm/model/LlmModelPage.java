package com.lambda.fusion.ai.llm.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.AiConstants.ModelType;
import com.lambda.fusion.ai.llm.model.entity.LlmModelEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import com.lambda.fusion.core.utils.AuthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "LLM 模型分页查询参数")
public class LlmModelPage extends PageQuery<LlmModelEntity> {

    @Schema(description = "所属提供方ID")
    private String providerId;

    @Schema(description = "模型名称，支持模糊查询")
    private String name;

    @Schema(description = "模型类型: 1=CHAT, 2=EMBEDDING")
    private ModelType modelType;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<LlmModelEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<LlmModelEntity> wrapper = new LambdaQueryWrapper<>();
        String tenantId = AuthUtils.getTenantId();
        wrapper.eq(StringUtils.isNotBlank(tenantId), LlmModelEntity::getTenantId, tenantId);
        wrapper.eq(StringUtils.isNotBlank(providerId), LlmModelEntity::getProviderId, providerId);
        wrapper.like(StringUtils.isNotBlank(name), LlmModelEntity::getName, name);
        wrapper.eq(modelType != null, LlmModelEntity::getModelType, modelType);
        wrapper.eq(enabled != null, LlmModelEntity::getEnabled, enabled);
        wrapper.orderByDesc(LlmModelEntity::getCreatedAt);
        return wrapper;
    }
}
