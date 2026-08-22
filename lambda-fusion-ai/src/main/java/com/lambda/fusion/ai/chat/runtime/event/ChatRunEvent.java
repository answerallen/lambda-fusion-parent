package com.lambda.fusion.ai.chat.runtime.event;

/**
 * 对话执行事件。
 *
 * @param cursor 当前 JVM 缓冲区内的游标
 * @param type 事件类型
 * @param data AG-UI 事件 JSON
 * @author Jin
 */
public record ChatRunEvent(long cursor, String type, String data) {}
