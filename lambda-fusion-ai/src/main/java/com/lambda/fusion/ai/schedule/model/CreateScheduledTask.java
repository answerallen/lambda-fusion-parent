package com.lambda.fusion.ai.schedule.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 创建定时任务。任务定义复用 {@code ai_sub_agent}（category=SCHEDULED_TASK），
 * 调度策略字段仅在该分类下生效。
 *
 * @author Jin
 */
@Data
@Schema(description = "创建定时任务")
public class CreateScheduledTask {

    @Schema(description = "任务名(调度内唯一,租户隔离)")
    @NotBlank(message = "任务名不能为空")
    private String name;

    @Schema(description = "系统提示词")
    @NotBlank(message = "系统提示词不能为空")
    private String prompt;

    @Schema(description = "绑定模型ID(必填,定时任务无宿主 Agent 可继承)")
    @NotBlank(message = "绑定模型不能为空")
    private String modelId;

    @Schema(description = "调度模式: CRON|FIXED_RATE(NONE 表示仅手动触发)")
    private String scheduleMode = "CRON";

    @Schema(description = "cron 表达式(CRON 模式必填,如 0 0 8 * * ?)")
    private String cronExpression;

    @Schema(description = "固定频率毫秒(FIXED_RATE 模式必填)")
    private Long fixedRate;

    @Schema(description = "cron 时区(如 Asia/Shanghai,空=系统默认)")
    private String zoneId;

    @Schema(description = "首次执行前初始延迟毫秒")
    private Long initialDelay;

    @Schema(description = "定时触发初始输入(可选,作为用户消息传给 Agent)")
    private String inputMsg;

    @Schema(description = "工具白名单(空=全部本地工具)")
    private List<String> toolsAllow;

    @Schema(description = "温度")
    private BigDecimal temperature;

    @Schema(description = "Top-P")
    private BigDecimal topP;

    @Schema(description = "最大 ReAct 迭代数(空=默认)")
    private Integer steps;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否启用调度(默认启用)")
    private Boolean scheduleEnabled = Boolean.TRUE;
}
