package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "工作流恢复执行请求DTO")
public class WorkflowResumeRequest {

    @Schema(description = "工作线程ID")
    private String threadId;

    @Schema(description = "指定恢复的checkpoint ID，不传时使用线程最新checkpoint")
    private String checkpointId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "知识库ID")
    private String kbId;

    @Schema(description = "LLM模型ID")
    private String llmModelId;

    @Schema(description = "是否启用追踪")
    private Boolean traceEnabled = true;

    @Schema(description = "执行前中断节点")
    private String interruptBefore;

    @Schema(description = "执行后中断节点")
    private String interruptAfter;

    @Schema(description = "恢复后是否释放线程")
    private Boolean releaseThread;

    @Schema(description = "最大迭代次数")
    private Integer maxIterations;

    @Schema(description = "用户补充输入内容")
    private String message;

    @Schema(description = "恢复时附加输入参数")
    private Map<String, Object> inputParams;
}
