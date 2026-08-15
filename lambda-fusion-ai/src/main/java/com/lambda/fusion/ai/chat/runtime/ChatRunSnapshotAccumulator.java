package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.chat.runtime.model.ExecutionSnapshotDelta;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 执行快照累加器。
 *
 * <p>根据单个运行的 Agent 事件更新文本、推理和工具调用状态，并生成可持久化快照。
 *
 * @author Jin
 */
final class ChatRunSnapshotAccumulator {

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

    /**
     * 根据已有快照创建累加器。
     *
     * @param snapshot 已持久化的执行快照
     */
    ChatRunSnapshotAccumulator(ExecutionSnapshot snapshot) {
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

    /**
     * 开始新的执行阶段。
     *
     * @param nextAguiRunId 新阶段的 AG-UI 运行标识
     * @param nextPhaseNo 新阶段号
     */
    void beginPhase(String nextAguiRunId, int nextPhaseNo) {
        aguiRunId = nextAguiRunId;
        phaseNo = nextPhaseNo;
        pendingTools = List.of();
        closeActiveMessages();
    }

    /**
     * 追加回复文本。
     *
     * @param messageId 文本消息标识
     * @param delta 增量文本
     */
    private void appendText(String messageId, String delta) {
        closeReasoning();
        textMessageId = messageId;
        textOpen = true;
        text.append(delta);
    }

    /**
     * 追加推理文本。
     *
     * @param messageId 推理消息标识
     * @param delta 增量文本
     */
    private void appendReasoning(String messageId, String delta) {
        closeText();
        reasoningMessageId = messageId;
        reasoningOpen = true;
        reasoning.append(delta);
    }

    /** 关闭当前文本和推理消息；仅经 {@code beginPhase} 与 {@code apply} 的增量驱动。 */
    private void closeActiveMessages() {
        closeText();
        closeReasoning();
    }

    /** 关闭当前文本消息。 */
    private void closeText() {
        textOpen = false;
    }

    /** 关闭当前推理消息。 */
    private void closeReasoning() {
        reasoningOpen = false;
    }

    /**
     * 应用执行快照增量。
     *
     * @param delta 快照增量
     */
    void apply(ExecutionSnapshotDelta delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (delta.closeActiveMessages()) {
            closeActiveMessages();
        } else {
            if (delta.closeText()) {
                closeText();
            }
            if (delta.closeReasoning()) {
                closeReasoning();
            }
        }
        if (delta.reasoningDelta() != null) {
            appendReasoning(delta.reasoningMessageId(), delta.reasoningDelta());
        }
        if (delta.textDelta() != null) {
            appendText(delta.textMessageId(), delta.textDelta());
        }
        for (ExecutionSnapshotDelta.ToolDelta tool : delta.tools()) {
            applyTool(tool);
        }
        if (delta.awaitingTools() != null && !delta.awaitingTools().isEmpty()) {
            awaiting(delta.awaitingTools());
        }
    }

    private void applyTool(ExecutionSnapshotDelta.ToolDelta delta) {
        ExecutionSnapshot.Tool current = findTool(delta.toolCallId());
        String args = current == null ? "" : current.args();
        String result = current == null ? "" : current.result();
        String status = current == null ? null : current.status();
        if (delta.replaceArgs()) {
            args = safe(delta.argsDelta());
        } else if (delta.argsDelta() != null) {
            args += safe(delta.argsDelta());
        }
        if (delta.replaceResult()) {
            result = safe(delta.resultDelta());
        } else if (delta.resultDelta() != null) {
            result += safe(delta.resultDelta());
        }
        if (delta.status() != null) {
            status = delta.status();
        }
        upsertTool(delta.toolCallId(), delta.toolCallName(), args, result, status);
    }

    /**
     * 设置待确认工具调用。
     *
     * @param awaitingTools 待确认工具调用
     */
    private void awaiting(List<ExecutionSnapshot.Tool> awaitingTools) {
        pendingTools = List.copyOf(awaitingTools);
    }

    /**
     * 生成当前执行快照。
     *
     * @return 执行快照
     */
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
        String nextName = toolCallName == null && current != null ? current.toolCallName() : safe(toolCallName);
        String nextArgs = args == null && current != null ? current.args() : safe(args);
        String nextResult = result == null && current != null ? current.result() : safe(result);
        String nextStatus = status == null && current != null ? current.status() : status;
        ExecutionSnapshot.Tool updated =
                new ExecutionSnapshot.Tool(toolCallId, nextName, nextArgs, nextResult, nextStatus);
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
