package com.lambda.fusion.ai.chat.execution.event;

/** 当前可订阅的 Run 事件游标窗口。 */
public record ExecutionEventCursorWindow(long minSeq, long latestSeq) {}
