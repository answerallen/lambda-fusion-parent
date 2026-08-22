package com.lambda.fusion.ai.chat.runtime.model;

import java.util.List;

/**
 * AG-UI 引导事件批次。
 *
 * @param cursor 当前 JVM 内部事件游标
 * @param events 引导事件 JSON 列表
 * @param phaseClosed 当前阶段是否已关闭
 * @author Jin
 */
public record AguiBootstrap(long cursor, List<String> events, boolean phaseClosed) {}
