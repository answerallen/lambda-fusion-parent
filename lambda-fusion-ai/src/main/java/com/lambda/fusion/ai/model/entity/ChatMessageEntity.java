package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.ChatHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对话消息实体类
 *
 * @author Jin
 */
@AutoConverter(target = ChatHistory.class)
@Data
@TableName("ai_chat_message")
@Schema(description = "对话消息实体")
public class ChatMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
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

    @Schema(description = "租户隔离ID")
    private String tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
