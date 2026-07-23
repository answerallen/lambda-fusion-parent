package com.lambda.fusion.ai.subagent.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "子代理分页查询参数")
public class SubAgentPage extends PageQuery<SubAgentEntity> {

    @Schema(description = "子代理名，支持模糊查询")
    private String name;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<SubAgentEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<SubAgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(name), SubAgentEntity::getName, name);
        wrapper.eq(enabled != null, SubAgentEntity::getEnabled, enabled);
        wrapper.orderByDesc(SubAgentEntity::getCreatedAt);
        return wrapper;
    }
}
