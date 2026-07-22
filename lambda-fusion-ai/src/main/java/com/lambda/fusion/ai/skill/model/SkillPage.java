package com.lambda.fusion.ai.skill.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.skill.model.entity.SkillEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "平台技能分页查询参数")
public class SkillPage extends PageQuery<SkillEntity> {

    @Schema(description = "技能名，支持模糊查询")
    private String name;

    @Schema(description = "描述，支持模糊查询")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<SkillEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<SkillEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(name), SkillEntity::getName, name);
        wrapper.like(StringUtils.isNotBlank(description), SkillEntity::getDescription, description);
        wrapper.eq(enabled != null, SkillEntity::getEnabled, enabled);
        wrapper.orderByDesc(SkillEntity::getCreatedAt);
        return wrapper;
    }
}
