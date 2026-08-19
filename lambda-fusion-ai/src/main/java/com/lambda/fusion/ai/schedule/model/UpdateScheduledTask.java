package com.lambda.fusion.ai.schedule.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 更新定时任务（增量，null/缺省字段不覆盖）。
 *
 * @author Jin
 */
@Data
@Schema(description = "更新定时任务")
public class UpdateScheduledTask {

    @Schema(description = "任务名(调度内唯一)")
    private String name;

    @Schema(description = "系统提示词")
    private String prompt;

    @Schema(description = "绑定模型ID")
    private String modelId;

    @Schema(description = "调度模式: NONE|CRON|FIXED_RATE")
    private String scheduleMode;

    @Schema(description = "cron 表达式")
    private String cronExpression;

    @Schema(description = "固定频率毫秒")
    private Long fixedRate;

    @Schema(description = "cron 时区")
    private String zoneId;

    @Schema(description = "首次执行前初始延迟毫秒")
    private Long initialDelay;

    @Schema(description = "定时触发初始输入")
    private String inputMsg;

    @Schema(description = "工具白名单")
    private List<String> toolsAllow;

    @Schema(description = "温度")
    private BigDecimal temperature;

    @Schema(description = "Top-P")
    private BigDecimal topP;

    @Schema(description = "最大 ReAct 迭代数")
    private Integer steps;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否启用调度")
    private Boolean scheduleEnabled;
}
