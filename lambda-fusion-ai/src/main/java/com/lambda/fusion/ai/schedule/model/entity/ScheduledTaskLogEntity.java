package com.lambda.fusion.ai.schedule.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 定时任务执行记录：一次执行（定时触发或手动触发）的落库事实，独立于调度器状态（§20.3）。
 *
 * @author Jin
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_scheduled_task_log")
@Schema(description = "定时任务执行记录")
public class ScheduledTaskLogEntity extends BaseEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("tenant_id")
    @Schema(description = "租户ID")
    private String tenantId;

    @TableField("task_id")
    @Schema(description = "定时任务ID(ai_sub_agent.id)")
    private String taskId;

    @TableField("task_name")
    @Schema(description = "任务名快照(删任务后仍可读)")
    private String taskName;

    @TableField("trigger_type")
    @Schema(description = "触发方式: SCHEDULED(定时)|MANUAL(手动)")
    private String triggerType;

    @TableField("status")
    @Schema(description = "执行状态: SUCCESS|FAILED")
    private String status;

    @TableField("output")
    @Schema(description = "Agent 最终输出全文(定时路径暂为空)")
    private String output;

    @TableField("error_message")
    @Schema(description = "失败信息")
    private String errorMessage;

    @TableField("duration_ms")
    @Schema(description = "执行耗时毫秒")
    private Long durationMs;

    @TableField("started_at")
    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
