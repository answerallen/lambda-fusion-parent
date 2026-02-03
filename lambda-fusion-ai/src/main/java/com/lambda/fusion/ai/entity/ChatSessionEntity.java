package com.lambda.fusion.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对话会话实体类
 *
 * @author Jin
 */
@Data
@TableName("ai_chat_session")
@Schema(description = "对话会话实体")
public class ChatSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String title;
    private Long kbId;
    private Long userId;
    private Long tenantId;
    private Long llmModelId;
    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer messageCount;
    private Integer totalTokens;
    private BigDecimal totalCost;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
}
