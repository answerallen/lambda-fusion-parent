package com.lambda.fusion.ai.apps.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能应用分页查询参数")
public class AppPageQuery extends PageQuery<AppEntity> {

    @Schema(description = "应用名称，支持模糊查询")
    private String name;

    @Schema(description = "绑定模型ID")
    private String modelId;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<AppEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(name), AppEntity::getName, name);
        wrapper.eq(StringUtils.isNotBlank(modelId), AppEntity::getModelId, modelId);
        wrapper.eq(enabled != null, AppEntity::getEnabled, enabled);
        wrapper.orderByDesc(AppEntity::getCreatedAt);
        return wrapper;
    }
}
