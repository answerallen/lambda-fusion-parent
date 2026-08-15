package com.lambda.fusion.ai.chat.runtime.model;

import java.util.List;

/**
 * AG-UI 引导事件批次。
 *
 * @param highWatermark 事件序号上界
 * @param events 引导事件 JSON 列表
 * @param phaseClosed 当前阶段是否已关闭
 * @author Jin
 */
public record AguiBootstrap(long highWatermark, List<String> events, boolean phaseClosed) {}
