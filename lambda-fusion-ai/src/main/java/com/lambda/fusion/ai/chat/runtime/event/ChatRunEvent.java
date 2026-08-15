package com.lambda.fusion.ai.chat.runtime.event;

/**
 * 对话执行事件。
 *
 * @param seq 事件序号
 * @param id 事件标识
 * @param data AG-UI 事件 JSON
 * @author Jin
 */
public record ChatRunEvent(long seq, String id, String data) {}
