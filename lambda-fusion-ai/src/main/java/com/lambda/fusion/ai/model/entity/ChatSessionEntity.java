package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对话会话实体类
 *
 * @author Jin
 */
@Data
@TableName("ai_chat_session")
@Schema(description = "对话会话实体")
public class ChatSessionEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    @Version
    private Long version;

    private String sessionId;
    private String title;
    private String kbId;
    private String userId;
    private String llmModelId;

    @Schema(description = "关联的机器人ID")
    private String robotId;

    @Schema(description = "关联的主题工作流配置ID")
    private String workflowId;

    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer messageCount;
    private Integer totalTokens;
    private BigDecimal totalCost;
    private String status;

    @Schema(description = "租户隔离ID")
    private String tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
}
