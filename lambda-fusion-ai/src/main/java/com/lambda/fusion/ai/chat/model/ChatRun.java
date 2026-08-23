package com.lambda.fusion.ai.chat.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.annotation.FieldMapping;
import com.lambda.cloud.core.shared.BaseVO;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 面向客户端的对话 Run 视图，不暴露内部快照 JSON。 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = ChatRunEntity.class, isReverse = true)
@FieldMapping(target = "pendingConfirm", ignore = true)
@FieldMapping(target = "pendingInputs", ignore = true)
@Schema(description = "对话运行")
public class ChatRun extends BaseVO<ChatRunEntity> {

    private String id;
    private String sessionId;
    private String status;
    private String finishReason;
    private Integer phaseNo;
    private String aguiRunId;
    private List<PendingTool> pendingConfirm;
    private List<PendingInput> pendingInputs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;

    public record PendingTool(String toolCallId, String toolName) {}

    /** 挂起等待用户输入的工具调用：question/inputKind 供渲染，responseSchemaJson 供表单校验。 */
    public record PendingInput(
            String toolCallId, String toolCallName, String question, String inputKind, String responseSchemaJson) {}
}
