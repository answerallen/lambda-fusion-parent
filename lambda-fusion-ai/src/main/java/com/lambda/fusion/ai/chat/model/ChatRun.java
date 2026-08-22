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
@Schema(description = "对话运行")
public class ChatRun extends BaseVO<ChatRunEntity> {

    private String id;
    private String sessionId;
    private String status;
    private String finishReason;
    private Integer phaseNo;
    private String aguiRunId;
    private List<PendingTool> pendingConfirm;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;

    public record PendingTool(String toolCallId, String toolName) {}
}
