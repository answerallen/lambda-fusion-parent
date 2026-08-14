package com.lambda.fusion.ai.chat.execution.event;

import java.util.List;

/**
 * 事件追加结果。
 *
 * @param events 已追加的事件
 * @param checkpointRequired 是否需要生成检查点并收缩缓冲区
 * @author Jin
 */
public record ExecutionEventOutcome(List<ExecutionEvent> events, boolean checkpointRequired) {}
