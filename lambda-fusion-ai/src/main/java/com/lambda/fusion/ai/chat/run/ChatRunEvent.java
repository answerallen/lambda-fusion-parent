package com.lambda.fusion.ai.chat.run;

/** 已编号、可重放的单个 AG-UI 出站事件。 */
public record ChatRunEvent(long seq, String id, String data) {}
