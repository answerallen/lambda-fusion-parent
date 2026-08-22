package com.lambda.fusion.ai.chat.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 持久化的一次逻辑对话回合。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_run")
public class ChatRunEntity extends BaseEntity {

    @TableId("id")
    private String id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("session_id")
    private String sessionId;

    @TableField("client_request_id")
    private String clientRequestId;

    @TableField("request_hash")
    private String requestHash;

    @TableField("user_message_id")
    private Long userMessageId;

    @TableField("assistant_message_id")
    private Long assistantMessageId;

    @TableField("status")
    private String status;

    @TableField("finish_reason")
    private String finishReason;

    @TableField("phase_no")
    private Integer phaseNo;

    @TableField("agui_run_id")
    private String aguiRunId;

    @TableField("await_confirm_deadline_at")
    private LocalDateTime awaitConfirmDeadlineAt;

    @TableField("snapshot_seq")
    private Long snapshotSeq;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 当前执行该 Run 的节点标识（沿用物理列 owner_instance_id，NULL 表示本机/未指定）。 */
    @TableField("owner_instance_id")
    private String executorInstanceId;

    /** 执行节点最近一次心跳时间（数据库时间）；超时用于判定节点失效并收敛 Run，不构成接管/租约。 */
    @TableField("heartbeat_at")
    private LocalDateTime heartbeatAt;
}
