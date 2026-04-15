package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "工作流执行状态DTO")
public class WorkflowExecutionStatus {

    @Schema(description = "工作线程ID")
    private String threadId;

    @Schema(description = "当前checkpoint ID")
    private String checkpointId;

    @Schema(description = "关联执行ID")
    private String executionId;

    @Schema(description = "当前所在节点ID")
    private String currentNodeId;

    @Schema(description = "下一跳节点ID")
    private String nextNode;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否已完成")
    private Boolean finished;

    @Schema(description = "是否处于中断状态")
    private Boolean interrupted;

    @Schema(description = "是否等待用户输入")
    private Boolean waitingForInput;

    @Schema(description = "最近一次回答")
    private String answer;

    @Schema(description = "执行追踪")
    private List<Map<String, Object>> executionTrace;

    @Schema(description = "执行统计")
    private Map<String, Object> executionStats;
}
