package com.lambda.fusion.ai.chat.execution.model;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;

/**
 * 对话运行终结命令。
 *
 * @param targetStatus 目标状态
 * @param finishReason 结束原因
 * @param snapshot 最终执行快照
 * @param toolCallJson 工具调用记录 JSON
 * @param lastSeq 终态事件写入前的最新事件序号
 * @param errorCode 错误码
 * @param errorMessage 错误信息
 * @author Jin
 */
public record FinalizeCommand(
        ChatRunStatus targetStatus,
        String finishReason,
        ExecutionSnapshot snapshot,
        String toolCallJson,
        long lastSeq,
        String errorCode,
        String errorMessage) {}
