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
    private String userId;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "知识库ID")
    private String kbId;

    @Schema(description = "LLM模型ID")
    private String llmModelId;

    @Schema(description = "初始消息列表")
    private List<ChatMessage> messages;

    @Schema(description = "输入参数")
    private Map<String, Object> inputParams;

    @Schema(description = "是否启用追踪")
    private Boolean traceEnabled = true;

    private String threadId;

    private Boolean checkpointEnabled = false;

    private String interruptBefore;

    private String interruptAfter;

    private Boolean releaseThread;

    private Integer maxIterations;

    /**
     * 是否由聊天消息层触发（内部字段，前端无需传入）。
     * <p>当为 {@code true} 时，工作流执行服务跳过会话和模型统计结算，
     * 由 {@code ChatMessageServiceImpl.persistStreamMessages} 统一负责；
     * 当为 {@code false}（默认）时，工作流执行服务在完成后自行结算。</p>
     */
    @Schema(description = "是否由聊天层触发（内部使用）", hidden = true)
    private Boolean calledFromChat = false;
}
