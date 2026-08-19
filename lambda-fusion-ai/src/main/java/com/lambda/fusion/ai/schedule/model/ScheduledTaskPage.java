package com.lambda.fusion.ai.schedule.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.AiConstants.SubAgentCategory;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务分页查询：固定过滤 category=SCHEDULED_TASK，与子代理路由记录隔离。
 *
 * @author Jin
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "定时任务分页查询参数")
public class ScheduledTaskPage extends PageQuery<SubAgentEntity> {

    @Schema(description = "任务名，支持模糊查询")
    private String name;

    @Schema(description = "调度是否启用")
    private Boolean scheduleEnabled;

    @Override
    public LambdaQueryWrapper<SubAgentEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<SubAgentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubAgentEntity::getCategory, SubAgentCategory.SCHEDULED_TASK.getCode());
        wrapper.like(StringUtils.isNotBlank(name), SubAgentEntity::getName, name);
        wrapper.eq(scheduleEnabled != null, SubAgentEntity::getScheduleEnabled, scheduleEnabled);
        wrapper.orderByDesc(SubAgentEntity::getCreatedAt);
        return wrapper;
    }
}
