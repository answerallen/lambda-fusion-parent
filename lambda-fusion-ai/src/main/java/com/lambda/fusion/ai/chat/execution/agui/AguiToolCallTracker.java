package com.lambda.fusion.ai.chat.execution.agui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 维护 AG-UI 工具事件配对所需的进程内状态。 */
final class AguiToolCallTracker {

    private final Set<String> startedToolCalls = new HashSet<>();
    private final Map<String, StringBuilder> resultBuffers = new HashMap<>();

    boolean markStarted(String toolCallId) {
        resultBuffers.computeIfAbsent(toolCallId, ignored -> new StringBuilder());
        return startedToolCalls.add(toolCallId);
    }

    void appendResult(String toolCallId, String delta) {
        StringBuilder buffer = resultBuffers.get(toolCallId);
        if (buffer != null) {
            buffer.append(delta);
        }
    }

    String result(String toolCallId) {
        StringBuilder buffer = resultBuffers.get(toolCallId);
        return buffer == null ? "" : buffer.toString();
    }
}
