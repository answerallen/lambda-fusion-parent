package com.lambda.fusion.ai.chat.execution.runtime;

import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 单个 Run 内由串行 Agent Flux 回调推进的可变展示状态。 */
final class ExecutionSnapshotAccumulator {

    private final String runId;
    private String aguiRunId;
    private int phaseNo;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private String textMessageId;
    private String reasoningMessageId;
    private boolean textOpen;
    private boolean reasoningOpen;
    private final List<ExecutionSnapshot.Tool> tools = new ArrayList<>();
    private List<ExecutionSnapshot.Tool> pendingTools = List.of();

    ExecutionSnapshotAccumulator(ExecutionSnapshot snapshot) {
        runId = snapshot.runId();
        aguiRunId = snapshot.aguiRunId();
        phaseNo = snapshot.phaseNo();
        text.append(snapshot.text());
        reasoning.append(snapshot.reasoning());
        textMessageId = snapshot.textMessageId();
        reasoningMessageId = snapshot.reasoningMessageId();
        textOpen = snapshot.textOpen();
        reasoningOpen = snapshot.reasoningOpen();
        tools.addAll(snapshot.tools());
        pendingTools = snapshot.pendingTools();
    }

    void beginPhase(String nextAguiRunId, int nextPhaseNo) {
        aguiRunId = nextAguiRunId;
        phaseNo = nextPhaseNo;
        pendingTools = List.of();
        closeActiveMessages();
    }

    void appendText(String messageId, String delta) {
        closeReasoning();
        textMessageId = messageId;
        textOpen = true;
        text.append(delta);
    }

    void appendReasoning(String messageId, String delta) {
        closeText();
        reasoningMessageId = messageId;
        reasoningOpen = true;
        reasoning.append(delta);
    }

    void closeActiveMessages() {
        closeText();
        closeReasoning();
    }

    void closeText() {
        textOpen = false;
    }

    void closeReasoning() {
        reasoningOpen = false;
    }

    void startTool(String toolCallId, String toolCallName) {
        closeActiveMessages();
        upsertTool(toolCallId, toolCallName, null, null, "running");
    }

    void appendToolArgs(String toolCallId, String toolCallName, String delta) {
        ExecutionSnapshot.Tool current = findTool(toolCallId);
        upsertTool(toolCallId, toolCallName, (current == null ? "" : current.args()) + safe(delta), null, "running");
    }

    void finishToolArgs(String toolCallId, String toolCallName) {
        upsertTool(toolCallId, toolCallName, null, null, "running");
    }

    void appendToolResult(String toolCallId, String toolCallName, String delta) {
        ExecutionSnapshot.Tool current = findTool(toolCallId);
        upsertTool(toolCallId, toolCallName, null, (current == null ? "" : current.result()) + safe(delta), "running");
    }

    void finishTool(String toolCallId, String toolCallName) {
        upsertTool(toolCallId, toolCallName, null, null, "complete");
    }

    void awaiting(List<ExecutionSnapshot.Tool> awaitingTools) {
        pendingTools = List.copyOf(awaitingTools);
    }

    ExecutionSnapshot snapshot() {
        return new ExecutionSnapshot(
                runId,
                aguiRunId,
                phaseNo,
                text.toString(),
                reasoning.toString(),
                textMessageId,
                reasoningMessageId,
                textOpen,
                reasoningOpen,
                tools,
                pendingTools);
    }

    private ExecutionSnapshot.Tool findTool(String toolCallId) {
        return tools.stream()
                .filter(tool -> Objects.equals(tool.toolCallId(), toolCallId))
                .findFirst()
                .orElse(null);
    }

    private void upsertTool(String toolCallId, String toolCallName, String args, String result, String status) {
        int index = -1;
        for (int i = 0; i < tools.size(); i++) {
            if (Objects.equals(tools.get(i).toolCallId(), toolCallId)) {
                index = i;
                break;
            }
        }
        ExecutionSnapshot.Tool current = index < 0 ? null : tools.get(index);
        ExecutionSnapshot.Tool updated = new ExecutionSnapshot.Tool(
                toolCallId,
                toolCallName == null && current != null ? current.toolCallName() : safe(toolCallName),
                args == null && current != null ? current.args() : safe(args),
                result == null && current != null ? current.result() : safe(result),
                status);
        if (index < 0) {
            tools.add(updated);
        } else {
            tools.set(index, updated);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
