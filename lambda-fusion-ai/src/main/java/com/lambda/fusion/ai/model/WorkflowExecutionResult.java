package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "工作流执行结果DTO")
public class WorkflowExecutionResult {

    @Schema(description = "执行ID")
    private String id;

    @Schema(description = "是否完成")
    private Boolean finished;

    @Schema(description = "最终回答")
    private String answer;

    @Schema(description = "执行时长(ms)")
    private Long durationMs;

    @Schema(description = "提示Token数")
    private Integer promptTokens;

    @Schema(description = "完成Token数")
    private Integer completionTokens;

    @Schema(description = "总Token数")
    private Integer totalTokens;

    @Schema(description = "执行状态")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "执行追踪")
    private List<Map<String, Object>> executionTrace;

    @Schema(description = "执行统计")
    private Map<String, Object> executionStats;

    @Schema(description = "输出结果")
    private Map<String, Object> outputResult;
}
