package com.lambda.fusion.ai.schedule.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.schedule.model.entity.ScheduledTaskLogEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务执行记录分页查询：固定按任务过滤（租户由插件拼接），按开始时间倒序。
 *
 * @author Jin
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "定时任务执行记录分页查询参数")
public class ScheduledTaskLogPage extends PageQuery<ScheduledTaskLogEntity> {

    @Schema(description = "定时任务ID")
    private String taskId;

    @Schema(description = "执行状态: SUCCESS|FAILED")
    private String status;

    @Override
    public LambdaQueryWrapper<ScheduledTaskLogEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<ScheduledTaskLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduledTaskLogEntity::getTaskId, taskId);
        wrapper.eq(status != null, ScheduledTaskLogEntity::getStatus, status);
        wrapper.orderByDesc(ScheduledTaskLogEntity::getStartedAt);
        return wrapper;
    }
}
