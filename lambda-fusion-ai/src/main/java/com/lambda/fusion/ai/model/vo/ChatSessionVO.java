package com.lambda.fusion.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "对话会话VO")
public class ChatSessionVO {
    private Long id;
    private String sessionId;
    private String title;
    private Long kbId;
    private Long userId;
    private Long llmModelId;
    private Integer messageCount;
    private Integer totalTokens;
    private BigDecimal totalCost;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
}
