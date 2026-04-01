package com.lambda.fusion.ai.model;

import dev.langchain4j.data.message.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@Schema(description = "工作流执行请求DTO")
public class WorkflowExecutionRequest {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "LLM模型ID")
    private Long llmModelId;

    @Schema(description = "初始消息列表")
    private List<ChatMessage> messages;

    @Schema(description = "输入参数")
    private Map<String, Object> inputParams;

    @Schema(description = "是否启用追踪")
    private Boolean traceEnabled = true;
}
