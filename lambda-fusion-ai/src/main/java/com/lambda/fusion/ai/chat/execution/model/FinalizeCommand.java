package com.lambda.fusion.ai.chat.execution.model;

import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;

/** 终结 Run 的一次提交意图：由执行器在最终快照与终态事件序号就绪后构造，一次终结对应一个命令。 */
public record FinalizeCommand(
        ChatRunStatus targetStatus,
        String finishReason,
        ExecutionSnapshot snapshot,
        String toolCallJson,
        long lastSeq,
        String errorCode,
        String errorMessage) {}
