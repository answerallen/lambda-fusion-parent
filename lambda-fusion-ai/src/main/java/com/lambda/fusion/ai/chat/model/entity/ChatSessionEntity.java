package com.lambda.fusion.ai.chat.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.chat.model.ChatSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 对话会话实体类
 *
 * @author Jin
 */
@AutoConverter(target = ChatSession.class)
@Data
@TableName(value = "ai_chat_session", autoResultMap = true)
@Schema(description = "对话会话实体")
public class ChatSessionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String title;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> kbIds;
    private String userId;
    private String llmModelId;

    @Version
    private Long version;

    @Schema(description = "关联的机器人ID")
    private String robotId;


    private String systemPrompt;
    private BigDecimal temperature;
    private Integer maxTokens;

    @Schema(description = "检索TopK快照（创建时从 robot 钉住）")
    private Integer retrievalTopK;

    @Schema(description = "相似度阈值快照（创建时从 robot 钉住）")
    private BigDecimal similarityThreshold;

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
