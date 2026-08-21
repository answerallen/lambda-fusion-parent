package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotDelta;
import io.agentscope.core.agui.event.AguiEvent;
import java.util.List;

/**
 * AgentEvent 的解释结果；同一份结果同时提供给 AG-UI 输出和快照累加，避免两处重复理解 AgentScope 事件。
 *
 * @param events AG-UI 事件列表
 * @param snapshotDelta 快照增量
 * @author Jin
 */
public record AgentEventInterpretation(List<AguiEvent> events, ChatRunSnapshotDelta snapshotDelta) {}
