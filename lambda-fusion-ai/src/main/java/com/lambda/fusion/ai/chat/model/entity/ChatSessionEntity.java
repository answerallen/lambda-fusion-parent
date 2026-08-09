package com.lambda.fusion.ai.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_chat_session")
@Schema(description = "对话会话")
public class ChatSessionEntity {

    @TableId("id")
    @Schema(description = "主键")
    private String id;

    @TableField("tenant_id")
    @Schema(description = "租户ID")
    private String tenantId;

    @TableField("app_id")
    @Schema(description = "应用ID")
    private String appId;

    @TableField("user_id")
    @Schema(description = "用户标识(用户名)")
    private String userId;

    @TableField("title")
    @Schema(description = "会话标题")
    private String title;

    @TableField("created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @TableField("last_message_at")
    @Schema(description = "最后消息时间")
    private LocalDateTime lastMessageAt;

    @TableField("pending_confirm")
    @Schema(description = "HITL 待确认工具调用 JSON(ASK 时写、回合结束清空;空=无待确认)")
    private String pendingConfirm;
}
