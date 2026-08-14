package com.lambda.fusion.ai.chat.execution.event;

/**
 * 对话执行事件。
 *
 * @param seq 事件序号
 * @param id 事件标识
 * @param data AG-UI 事件 JSON
 * @author Jin
 */
public record ExecutionEvent(long seq, String id, String data) {}
