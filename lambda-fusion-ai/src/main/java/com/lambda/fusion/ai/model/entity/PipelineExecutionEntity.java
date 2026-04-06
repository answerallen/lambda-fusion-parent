package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_pipeline_execution")
@Schema(description = "工作流执行记录实体")
public class PipelineExecutionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @Schema(description = "执行唯一标识")
    private String executionId;

    @Schema(description = "工作流定义ID")
    private String pipelineId;

    @Schema(description = "工作流版本")
    private Integer pipelineVersion;

    @Schema(description = "执行用户ID")
    private String userId;

    @Schema(description = "输入参数(JSON)")
    private String inputParams;

    @Schema(description = "输出结果(JSON)")
    private String outputResult;

    @Schema(description = "执行状态: RUNNING, COMPLETED, FAILED")
    private String status;

    @Schema(description = "执行进度(0-100)")
    private Integer progress;

    @Schema(description = "当前执行步骤")
    private String currentStep;

    @Schema(description = "错误代码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "错误堆栈")
    private String errorStack;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间")
    private LocalDateTime completedAt;

    @Schema(description = "执行时长(ms)")
    private Integer durationMs;

    @Schema(description = "租户隔离ID")
    private String tenantId;

    @Schema(description = "执行日志(JSON)")
    private String executionLog;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
