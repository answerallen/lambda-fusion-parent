package com.lambda.fusion.ai.chat.execution.agui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * AG-UI 工具调用状态记录器。
 *
 * @author Jin
 */
final class AguiToolCallTracker {

    private final Set<String> startedToolCalls = new HashSet<>();
    private final Map<String, StringBuilder> resultBuffers = new HashMap<>();

    /**
     * 记录工具调用开始状态。
     *
     * @param toolCallId 工具调用标识
     * @return 首次记录时返回 {@code true}
     */
    boolean markStarted(String toolCallId) {
        resultBuffers.computeIfAbsent(toolCallId, ignored -> new StringBuilder());
        return startedToolCalls.add(toolCallId);
    }

    /**
     * 追加工具执行结果。
     *
     * @param toolCallId 工具调用标识
     * @param delta 增量结果
     */
    void appendResult(String toolCallId, String delta) {
        StringBuilder buffer = resultBuffers.get(toolCallId);
        if (buffer != null) {
            buffer.append(delta);
        }
    }

    /**
     * 获取工具执行结果。
     *
     * @param toolCallId 工具调用标识
     * @return 已累计的结果；不存在时返回空字符串
     */
    String result(String toolCallId) {
        StringBuilder buffer = resultBuffers.get(toolCallId);
        return buffer == null ? "" : buffer.toString();
    }
}
