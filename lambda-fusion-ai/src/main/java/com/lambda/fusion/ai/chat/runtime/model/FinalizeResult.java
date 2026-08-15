package com.lambda.fusion.ai.chat.runtime.model;

/**
 * 对话运行终结结果。
 *
 * @param committed 是否由本次调用提交终态
 * @param status 最终状态
 * @param finishReason 结束原因
 * @param errorCode 错误码
 * @param errorMessage 错误信息
 * @author Jin
 */
public record FinalizeResult(
        boolean committed, String status, String finishReason, String errorCode, String errorMessage) {}
