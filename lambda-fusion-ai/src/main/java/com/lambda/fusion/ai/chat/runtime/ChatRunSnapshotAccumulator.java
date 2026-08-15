package com.lambda.fusion.ai.chat.runtime;

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
    void appendText(String messageId, String delta) {
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
    void appendReasoning(String messageId, String delta) {
        closeText();
        reasoningMessageId = messageId;
        reasoningOpen = true;
        reasoning.append(delta);
    }

    /** 关闭当前文本和推理消息。 */
    void closeActiveMessages() {
        closeText();
        closeReasoning();
    }

    /** 关闭当前文本消息。 */
    void closeText() {
        textOpen = false;
    }

    /** 关闭当前推理消息。 */
    void closeReasoning() {
        reasoningOpen = false;
    }

    /**
     * 记录工具调用开始。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     */
    void startTool(String toolCallId, String toolCallName) {
        closeActiveMessages();
        upsertTool(toolCallId, toolCallName, null, null, "running");
    }

    /**
     * 追加工具参数。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     * @param delta 参数增量
     */
    void appendToolArgs(String toolCallId, String toolCallName, String delta) {
        ExecutionSnapshot.Tool current = findTool(toolCallId);
        upsertTool(toolCallId, toolCallName, (current == null ? "" : current.args()) + safe(delta), null, "running");
    }

    /**
     * 标记工具参数接收完成。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     */
    void finishToolArgs(String toolCallId, String toolCallName) {
        upsertTool(toolCallId, toolCallName, null, null, "running");
    }

    /**
     * 追加工具执行结果。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     * @param delta 结果增量
     */
    void appendToolResult(String toolCallId, String toolCallName, String delta) {
        ExecutionSnapshot.Tool current = findTool(toolCallId);
        upsertTool(toolCallId, toolCallName, null, (current == null ? "" : current.result()) + safe(delta), "running");
    }

    /**
     * 标记工具调用完成。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名称
     */
    void finishTool(String toolCallId, String toolCallName) {
        upsertTool(toolCallId, toolCallName, null, null, "complete");
    }

    /**
     * 设置待确认工具调用。
     *
     * @param awaitingTools 待确认工具调用
     */
    void awaiting(List<ExecutionSnapshot.Tool> awaitingTools) {
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
