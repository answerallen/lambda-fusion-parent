package com.lambda.fusion.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "对话消息VO")
public class ChatMessageVO {
    private Long id;
    private String messageId;
    private Long sessionId;
    private String role;
    private String content;
    private Boolean isRagEnhanced;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer userFeedback;
    private LocalDateTime createdAt;
}
