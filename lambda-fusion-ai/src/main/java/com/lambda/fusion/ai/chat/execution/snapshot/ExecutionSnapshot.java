package com.lambda.fusion.ai.chat.execution.snapshot;

import java.util.List;

/** 可持久化的 Run 展示快照。 */
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

    public ExecutionSnapshot {
        text = text == null ? "" : text;
        reasoning = reasoning == null ? "" : reasoning;
        tools = ExecutionSnapshotSanitizer.sanitizeTools(tools);
        pendingTools = ExecutionSnapshotSanitizer.sanitizePendingTools(pendingTools);
    }

    public static ExecutionSnapshot empty(String runId, String aguiRunId, int phaseNo) {
        return new ExecutionSnapshot(runId, aguiRunId, phaseNo, "", "", null, null, false, false, List.of(), List.of());
    }

    /** 持久化工具调用状态。 */
    public record Tool(String toolCallId, String toolCallName, String args, String result, String status) {}
}
