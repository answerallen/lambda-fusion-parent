package com.lambda.fusion.ai.chat.runtime.event;

/**
 * 可订阅的事件游标窗口。
 *
 * @param minSeq 当前保留的最小事件序号
 * @param latestSeq 当前最新事件序号
 * @author Jin
 */
public record ChatRunEventCursor(long minSeq, long latestSeq) {}
