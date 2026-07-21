package com.lambda.fusion.ai.llm.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.llm.model.entity.LlmProviderEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import com.lambda.fusion.core.utils.AuthUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "LLM 提供方分页查询参数")
public class LlmProviderPageQuery extends PageQuery<LlmProviderEntity> {

    @Schema(description = "提供方名称，支持模糊查询")
    private String name;

    @Schema(description = "提供方类型")
    private String providerType;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<LlmProviderEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<LlmProviderEntity> wrapper = new LambdaQueryWrapper<>();
        String tenantId = AuthUtils.getTenantId();
        wrapper.eq(StringUtils.isNotBlank(tenantId), LlmProviderEntity::getTenantId, tenantId);
        wrapper.like(StringUtils.isNotBlank(name), LlmProviderEntity::getName, name);
        wrapper.eq(StringUtils.isNotBlank(providerType), LlmProviderEntity::getProviderType, providerType);
        wrapper.eq(enabled != null, LlmProviderEntity::getEnabled, enabled);
        wrapper.orderByDesc(LlmProviderEntity::getCreatedAt);
        return wrapper;
    }
}
