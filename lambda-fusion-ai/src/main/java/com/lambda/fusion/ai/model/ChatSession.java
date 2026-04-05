package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "对话会话VO")
public class ChatSession {
    private String id;
    private String sessionId;
    private String title;
    private String kbId;
    private String userId;
    private String llmModelId;
    private String robotId;
    private String workflowId;
    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer messageCount;
    private Integer totalTokens;
    private BigDecimal totalCost;
    private String status;
    private String tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
