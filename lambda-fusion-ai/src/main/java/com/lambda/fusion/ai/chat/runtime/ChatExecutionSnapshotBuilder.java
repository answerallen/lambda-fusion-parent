package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunToolStatus;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import io.agentscope.core.agui.event.AguiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 运行中快照投影器：消费已经标准化的 AG-UI 事件，生成浏览器恢复所需的最小投影。
 *
 * @author Jin
 */
public final class ChatExecutionSnapshotBuilder {

    private final String runId;
    private String aguiRunId;
    private int phaseNo;
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private String textMessageId;
    private String reasoningMessageId;
    private boolean textOpen;
    private boolean reasoningOpen;
    private final List<ChatRunSnapshot.ToolCall> tools = new ArrayList<>();
    private List<ChatRunSnapshot.ToolCall> pendingTools;

    /**
     * 根据已有快照创建累加器。
     *
     * @param snapshot 已持久化的执行快照
     */
    public ChatExecutionSnapshotBuilder(ChatRunSnapshot snapshot) {
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
    public void beginPhase(String nextAguiRunId, int nextPhaseNo) {
        aguiRunId = nextAguiRunId;
        phaseNo = nextPhaseNo;
        pendingTools = List.of();
        closeOpenMessages();
    }

    /**
     * 追加回复文本。
     *
     * @param messageId 文本消息标识
     * @param delta 增量文本
     */
    private void appendText(String messageId, String delta) {
        textMessageId = messageId;
        textOpen = true;
        text.append(safe(delta));
    }

    /**
     * 追加推理文本。
     *
     * @param messageId 推理消息标识
     * @param delta 增量文本
     */
    private void appendReasoning(String messageId, String delta) {
        reasoningMessageId = messageId;
        reasoningOpen = true;
        reasoning.append(safe(delta));
    }

    /** 关闭当前文本和推理消息；仅经 {@code beginPhase} 与 {@code apply} 的增量驱动。 */
    public void closeOpenMessages() {
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

    /** 应用一批已标准化的 AG-UI 事件。 */
    public void apply(List<AguiEvent> events) {
        if (events != null) {
            events.forEach(this::apply);
        }
    }

    private void apply(AguiEvent event) {
        switch (event) {
            case AguiEvent.TextMessageStart start -> {
                closeReasoning();
                textMessageId = start.messageId();
                textOpen = true;
            }
            case AguiEvent.TextMessageContent content -> appendText(content.messageId(), content.delta());
            case AguiEvent.TextMessageEnd ignored -> closeText();
            case AguiEvent.ReasoningMessageStart start -> {
                closeText();
                reasoningMessageId = start.messageId();
                reasoningOpen = true;
            }
            case AguiEvent.ReasoningMessageContent content -> appendReasoning(content.messageId(), content.delta());
            case AguiEvent.ReasoningMessageEnd ignored -> closeReasoning();
            case AguiEvent.ReasoningEnd ignored -> closeReasoning();
            case AguiEvent.ToolCallStart start ->
                upsertTool(start.toolCallId(), start.toolCallName(), null, null, ChatRunToolStatus.RUNNING.getCode());
            case AguiEvent.ToolCallArgs args -> appendToolArgs(args.toolCallId(), args.delta());
            case AguiEvent.ToolCallEnd end -> setToolStatus(end.toolCallId(), ChatRunToolStatus.COMPLETE.getCode());
            case AguiEvent.ToolCallResult result -> setToolResult(result.toolCallId(), result.content());
            case AguiEvent.RunFinished finished -> applyInterrupts(finished.outcome());
            default -> {
                // 生命周期、状态和自定义事件不属于浏览器恢复快照。
            }
        }
    }

    private void appendToolArgs(String toolCallId, String delta) {
        ChatRunSnapshot.ToolCall current = findTool(toolCallId);
        upsertTool(
                toolCallId,
                null,
                (current == null ? "" : current.args()) + safe(delta),
                null,
                ChatRunToolStatus.RUNNING.getCode());
    }

    private void setToolResult(String toolCallId, String result) {
        upsertTool(toolCallId, null, null, result, ChatRunToolStatus.COMPLETE.getCode());
    }

    private void setToolStatus(String toolCallId, String status) {
        upsertTool(toolCallId, null, null, null, status);
    }

    private void applyInterrupts(AguiEvent.RunFinishedOutcome outcome) {
        if (!(outcome instanceof AguiEvent.RunFinishedInterruptOutcome(List<AguiEvent.Interrupt> interrupts))) {
            return;
        }
        List<ChatRunSnapshot.ToolCall> awaitingTools =
                interrupts.stream().map(this::toPendingTool).toList();
        awaiting(awaitingTools);
        closeOpenMessages();
    }

    private ChatRunSnapshot.ToolCall toPendingTool(AguiEvent.Interrupt interrupt) {
        String toolCallId = interrupt.toolCallId() == null ? interrupt.id() : interrupt.toolCallId();
        ChatRunSnapshot.ToolCall current = findTool(toolCallId);
        Object metadataName =
                interrupt.metadata() == null ? null : interrupt.metadata().get("toolName");
        String toolName =
                metadataName == null ? (current == null ? "" : current.toolCallName()) : String.valueOf(metadataName);
        upsertTool(toolCallId, toolName, null, null, ChatRunToolStatus.ASKING.getCode());
        ChatRunSnapshot.ToolCall updated = findTool(toolCallId);
        return updated == null
                ? new ChatRunSnapshot.ToolCall(toolCallId, toolName, "", "", ChatRunToolStatus.ASKING.getCode())
                : updated;
    }

    /**
     * 设置待确认工具调用。
     *
     * @param awaitingTools 待确认工具调用
     */
    private void awaiting(List<ChatRunSnapshot.ToolCall> awaitingTools) {
        pendingTools = List.copyOf(awaitingTools);
    }

    /**
     * 生成当前执行快照。
     *
     * @return 执行快照
     */
    public ChatRunSnapshot buildSnapshot() {
        return new ChatRunSnapshot(
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

    private ChatRunSnapshot.ToolCall findTool(String toolCallId) {
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
        ChatRunSnapshot.ToolCall current = index < 0 ? null : tools.get(index);
        String nextName = toolCallName == null && current != null ? current.toolCallName() : safe(toolCallName);
        String nextArgs = args == null && current != null ? current.args() : safe(args);
        String nextResult = result == null && current != null ? current.result() : safe(result);
        String nextStatus = status == null && current != null ? current.status() : status;
        ChatRunSnapshot.ToolCall updated =
                new ChatRunSnapshot.ToolCall(toolCallId, nextName, nextArgs, nextResult, nextStatus);
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
