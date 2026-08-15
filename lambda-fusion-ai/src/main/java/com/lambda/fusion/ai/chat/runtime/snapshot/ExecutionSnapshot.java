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
public record ExecutionSnapshot(
        String runId,
        String aguiRunId,
        int phaseNo,
        String text,
        String reasoning,
        String textMessageId,
        String reasoningMessageId,
        boolean textOpen,
        boolean reasoningOpen,
        List<Tool> tools,
        List<Tool> pendingTools) {

    /** 归一化可空文本和工具调用集合。 */
    public ExecutionSnapshot {
        text = text == null ? "" : text;
        reasoning = reasoning == null ? "" : reasoning;
        tools = ExecutionSnapshotSanitizer.sanitizeTools(tools);
        pendingTools = ExecutionSnapshotSanitizer.sanitizePendingTools(pendingTools);
    }

    /**
     * 创建空执行快照。
     *
     * @param runId 运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param phaseNo 执行阶段号
     * @return 空执行快照
     */
    public static ExecutionSnapshot empty(String runId, String aguiRunId, int phaseNo) {
        return new ExecutionSnapshot(runId, aguiRunId, phaseNo, "", "", null, null, false, false, List.of(), List.of());
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
    public record Tool(String toolCallId, String toolCallName, String args, String result, String status) {}
}
