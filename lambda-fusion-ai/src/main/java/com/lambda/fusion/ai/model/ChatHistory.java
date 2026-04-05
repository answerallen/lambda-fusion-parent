package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "对话消息VO")
public class ChatHistory {
    private String id;
    private String messageId;
    private String sessionId;
    private String role;
    private String content;
    private Boolean isRagEnhanced;
    private String retrievedChunks;
    private String ragContext;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal qualityScore;
    private Integer userFeedback;
    private Integer latencyMs;
    private String metadata;
    private String tenantId;
    private LocalDateTime createdAt;
}
