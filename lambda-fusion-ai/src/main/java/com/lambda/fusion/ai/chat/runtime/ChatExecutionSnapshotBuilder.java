package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiConstants.ChatRunToolStatus;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import io.agentscope.core.agui.event.AguiEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, ChatRunSnapshot.ToolCall> tools = new LinkedHashMap<>();
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
        for (ChatRunSnapshot.ToolCall tool : snapshot.tools()) {
            tools.put(tool.toolCallId(), tool);
        }
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

    /**
     * 应用单个已标准化的 AG-UI 事件，按事件类型分派到文本、推理、工具调用或中断投影。
     *
     * <p>文本与推理互斥：任一侧开始即关闭另一侧的未闭合消息。生命周期、状态和自定义事件
     * 不属于浏览器恢复快照，直接忽略。
     *
     * @param event 已标准化的 AG-UI 事件
     */
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

    /** 追加工具调用参数增量：在已有参数尾部拼接 {@code delta}，状态保持 RUNNING。 */
    private void appendToolArgs(String toolCallId, String delta) {
        ChatRunSnapshot.ToolCall current = findTool(toolCallId);
        upsertTool(
                toolCallId,
                null,
                (current == null ? "" : current.args()) + safe(delta),
                null,
                ChatRunToolStatus.RUNNING.getCode());
    }

    /** 补写工具调用结果并置为 COMPLETE。 */
    private void setToolResult(String toolCallId, String result) {
        upsertTool(toolCallId, null, null, result, ChatRunToolStatus.COMPLETE.getCode());
    }

    /** 仅更新工具调用状态。 */
    private void setToolStatus(String toolCallId, String status) {
        upsertTool(toolCallId, null, null, null, status);
    }

    /**
     * 处理阶段结束事件中的中断结果：把待确认工具补写为 ASKING 投影并记录为 {@code pendingTools}，
     * 同时关闭未闭合的文本与推理消息，进入待确认状态。
     *
     * @param outcome 阶段结束结果；非中断结果（正常完成等）不改变投影
     */
    private void applyInterrupts(AguiEvent.RunFinishedOutcome outcome) {
        if (!(outcome instanceof AguiEvent.RunFinishedInterruptOutcome(List<AguiEvent.Interrupt> interrupts))) {
            return;
        }
        pendingTools = interrupts.stream().map(this::upsertAskingTool).toList();
        closeOpenMessages();
    }

    /** 以 ASKING 状态补写工具调用并返回其快照投影。 */
    private ChatRunSnapshot.ToolCall upsertAskingTool(AguiEvent.Interrupt interrupt) {
        String toolCallId = interrupt.toolCallId() == null ? interrupt.id() : interrupt.toolCallId();
        ChatRunSnapshot.ToolCall current = findTool(toolCallId);
        Object metadataName =
                interrupt.metadata() == null ? null : interrupt.metadata().get("toolName");
        String toolName =
                metadataName == null ? (current == null ? "" : current.toolCallName()) : String.valueOf(metadataName);
        upsertTool(toolCallId, toolName, null, null, ChatRunToolStatus.ASKING.getCode());
        return findTool(toolCallId);
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
                List.copyOf(tools.values()),
                pendingTools);
    }

    /** 判断当前是否处于待确认状态（存在待确认工具投影）。 */
    public boolean hasPendingConfirmation() {
        return !pendingTools.isEmpty();
    }

    /** 查找工具调用的当前投影；不存在时返回 {@code null}。 */
    private ChatRunSnapshot.ToolCall findTool(String toolCallId) {
        return tools.get(toolCallId);
    }

    /**
     * 按 {@code toolCallId} 插入或合并工具调用投影：传入 {@code null} 的字段沿用已有投影值，
     * 非空字段覆盖；没有已有投影时空字段落到空串（状态除外，仍可能为 {@code null}）。
     *
     * @param toolCallId 工具调用标识
     * @param toolCallName 工具名；{@code null} 表示保持不变
     * @param args 工具参数（全量）；{@code null} 表示保持不变
     * @param result 工具结果；{@code null} 表示保持不变
     * @param status 工具状态码；{@code null} 表示保持不变
     */
    private void upsertTool(String toolCallId, String toolCallName, String args, String result, String status) {
        ChatRunSnapshot.ToolCall current = tools.get(toolCallId);
        String nextName = toolCallName == null && current != null ? current.toolCallName() : safe(toolCallName);
        String nextArgs = args == null && current != null ? current.args() : safe(args);
        String nextResult = result == null && current != null ? current.result() : safe(result);
        String nextStatus = status == null && current != null ? current.status() : status;
        tools.put(toolCallId, new ChatRunSnapshot.ToolCall(toolCallId, nextName, nextArgs, nextResult, nextStatus));
    }

    /** 把 {@code null} 规整为空串，避免快照投影携带空值。 */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
