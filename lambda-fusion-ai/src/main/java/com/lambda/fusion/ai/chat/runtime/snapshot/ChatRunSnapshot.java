package com.lambda.fusion.ai.chat.runtime.snapshot;

import java.util.List;

/**
 * 对话执行快照。
 *
 * @param runId 运行标识
 * @param aguiRunId AG-UI 运行标识
 * @param phaseNo 执行阶段号
 * @param text 已生成的回复文本
 * @param reasoning 已生成的推理文本
 * @param textMessageId 文本消息标识
 * @param reasoningMessageId 推理消息标识
 * @param textOpen 文本消息是否处于打开状态
 * @param reasoningOpen 推理消息是否处于打开状态
 * @param tools 工具调用状态
 * @param pendingTools 待确认工具调用
 * @author Jin
 */
public record ChatRunSnapshot(
        String runId,
        String aguiRunId,
        int phaseNo,
        String text,
        String reasoning,
        String textMessageId,
        String reasoningMessageId,
        boolean textOpen,
        boolean reasoningOpen,
        List<ToolCall> tools,
        List<ToolCall> pendingTools) {

    /** 归一化可空文本和工具调用集合。 */
    public ChatRunSnapshot {
        text = text == null ? "" : text;
        reasoning = reasoning == null ? "" : reasoning;
        tools = ChatRunSnapshotSanitizer.sanitizeTools(tools);
        pendingTools = ChatRunSnapshotSanitizer.sanitizePendingTools(pendingTools);
    }

    /**
     * 创建空执行快照。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param phaseNo 执行阶段号
     * @return 空执行快照
     */
    public static ChatRunSnapshot empty(String runId, String aguiRunId, int phaseNo) {
        return new ChatRunSnapshot(runId, aguiRunId, phaseNo, "", "", null, null, false, false, List.of(), List.of());
    }

    /**
     * 生成下一确认阶段的持久化快照：保留累计内容与工具历史，关闭消息并清除待确认投影。
     *
     * @param nextAguiRunId 下一阶段 AG-UI 运行标识
     * @param nextPhaseNo 下一阶段号
     * @return 下一阶段快照
     */
    public ChatRunSnapshot beginPhase(String nextAguiRunId, int nextPhaseNo) {
        return new ChatRunSnapshot(
                runId,
                nextAguiRunId,
                nextPhaseNo,
                text,
                reasoning,
                textMessageId,
                reasoningMessageId,
                false,
                false,
                tools,
                List.of());
    }

    /**
     * 工具调用快照。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     * @param args 工具参数 JSON
     * @param result 工具结果 JSON
     * @param status 工具调用状态
     */
    public record ToolCall(String toolCallId, String toolCallName, String args, String result, String status) {}
}
