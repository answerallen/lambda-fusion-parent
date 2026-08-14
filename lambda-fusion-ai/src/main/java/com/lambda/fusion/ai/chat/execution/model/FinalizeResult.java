package com.lambda.fusion.ai.chat.execution.model;

/** 终结 Run 的提交结果：{@code committed=false} 表示已终态幂等返回，携带既有终态信息。 */
public record FinalizeResult(
        boolean committed,
        Long assistantMessageId,
        String status,
        String finishReason,
        String errorCode,
        String errorMessage) {}
