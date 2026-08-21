package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.AiConstants.ChatRunToolStatus;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotDelta;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * AgentScope 事件到 AG-UI 事件和快照增量的解释器；实例按运行阶段创建，负责维护文本、推理和工具调用事件的配对状态，
 * 并向 EventStore 与快照累加器输出一致结果。
 *
 * @author Jin
 */
public class AgentEventInterpreter {

    private final String threadId;
    private final String runId;
    private final boolean enableReasoning;
    private final AguiEventEncoder encoder;

    private String textMessageId;
    private String reasoningMessageId;
    private boolean reasoningStarted;
    private String reasoningGroupId;

    private static final String TOOL_RUNNING = ChatRunToolStatus.RUNNING.getCode();
    private static final String TOOL_COMPLETE = ChatRunToolStatus.COMPLETE.getCode();
    private static final String TOOL_ASKING = ChatRunToolStatus.ASKING.getCode();

    private final AguiToolCallTracker toolCallTracker = new AguiToolCallTracker();

    /**
     * 创建事件解释器。
     *
     * @param threadId AG-UI 线程标识
     * @param runId AG-UI 运行标识
     * @param enableReasoning 是否输出推理事件
     */
    public AgentEventInterpreter(String threadId, String runId, boolean enableReasoning) {
        this.threadId = threadId;
        this.runId = runId;
        this.enableReasoning = enableReasoning;
        this.encoder = new AguiEventEncoder();
    }

    /**
     * 将单个 AgentScope 事件解释为 AG-UI 事件和快照增量。
     *
     * @param event AgentScope 事件
     * @return 事件解释结果
     */
    public AgentEventInterpretation interpret(AgentEvent event) {
        List<AguiEvent> events = new ArrayList<>();
        SnapshotDeltaBuilder delta = new SnapshotDeltaBuilder();
        AgentEventType type = event.getType();
        switch (type) {
            case AGENT_START -> mapAgentStart(event, events);
            case TEXT_BLOCK_DELTA -> mapTextDelta(event, events, delta);
            case THINKING_BLOCK_DELTA -> mapThinkingDelta(event, events, delta);
            case TOOL_CALL_START -> mapToolCallStart(event, events, delta);
            case TOOL_CALL_DELTA -> mapToolCallDelta(event, events, delta);
            case TOOL_CALL_END -> mapToolCallEnd(event, events, delta);
            case TOOL_RESULT_START -> mapToolResultStart(event, events, delta);
            case TOOL_RESULT_TEXT_DELTA -> mapToolResultTextDelta(event, delta);
            case TOOL_RESULT_END -> mapToolResultEnd(event, events, delta);
            case REQUIRE_USER_CONFIRM -> mapRequireUserConfirm(event, events, delta);
            default -> {}
        }
        return new AgentEventInterpretation(List.copyOf(events), delta.build());
    }

    /**
     * 将 AG-UI 事件编码为 JSON。
     *
     * @param event AG-UI 事件
     * @return 事件 JSON
     */
    public String encodeToJson(AguiEvent event) {
        return encoder.encodeToJson(event);
    }

    /** 仅根 Agent 的启动才发 RunStarted；子 Agent 的 AGENT_START（带 source）不产生重复 RunStarted。 */
    private void mapAgentStart(AgentEvent event, List<AguiEvent> out) {
        if (event.getSource() == null) {
            out.add(new AguiEvent.RunStarted(threadId, runId, null, null));
        }
    }

    private void mapTextDelta(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof TextBlockDeltaEvent textDelta)) {
            return;
        }
        String msgId = resolveId(textDelta.getReplyId());
        if (!msgId.equals(textMessageId)) {
            closeReasoningMessage(out, delta);
            closeTextMessage(out, delta);
            textMessageId = msgId;
            out.add(new AguiEvent.TextMessageStart(threadId, runId, msgId, "assistant"));
        }
        out.add(new AguiEvent.TextMessageContent(threadId, runId, msgId, textDelta.getDelta()));
        delta.appendText(msgId, textDelta.getDelta());
    }

    private void mapThinkingDelta(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!enableReasoning || !(event instanceof ThinkingBlockDeltaEvent thinkingDelta)) {
            return;
        }
        String msgId = resolveId(thinkingDelta.getReplyId());
        if (!msgId.equals(reasoningMessageId)) {
            closeTextMessage(out, delta);
            if (reasoningMessageId != null) {
                out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
            }
            if (!reasoningStarted) {
                out.add(new AguiEvent.ReasoningStart(threadId, runId, msgId, null));
                reasoningStarted = true;
                reasoningGroupId = msgId;
            }
            reasoningMessageId = msgId;
            out.add(new AguiEvent.ReasoningMessageStart(threadId, runId, msgId, "reasoning"));
        }
        out.add(new AguiEvent.ReasoningMessageContent(threadId, runId, msgId, thinkingDelta.getDelta()));
        delta.appendReasoning(msgId, thinkingDelta.getDelta());
    }

    private void mapToolCallStart(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolCallStartEvent tool)) {
            return;
        }
        closeActiveMessage(out, delta);
        if (toolCallTracker.markStarted(tool.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tool.getToolCallId(), tool.getToolCallName()));
        }
        delta.upsertTool(tool.getToolCallId(), tool.getToolCallName(), null, null, TOOL_RUNNING, false, false);
    }

    private void mapToolCallDelta(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolCallDeltaEvent tool)) {
            return;
        }
        out.add(new AguiEvent.ToolCallArgs(threadId, runId, tool.getToolCallId(), tool.getDelta()));
        delta.upsertTool(
                tool.getToolCallId(), tool.getToolCallName(), tool.getDelta(), null, TOOL_RUNNING, false, false);
    }

    /**
     * 模型产出工具调用块结束；AgentScope 对同一工具先发 TOOL_CALL_END 再发 TOOL_RESULT_END，此处只推进快照状态，
     * 因 {@code ToolCallEnd} 统一由 result 齐全的 {@code TOOL_RESULT_END} 单一出口发出，避免同一「工具结束」产生两次事件。
     */
    private void mapToolCallEnd(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolCallEndEvent tool)) {
            return;
        }
        delta.upsertTool(tool.getToolCallId(), tool.getToolCallName(), null, null, TOOL_RUNNING, false, false);
    }

    private void mapToolResultStart(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolResultStartEvent tool)) {
            return;
        }
        closeActiveMessage(out, delta);
        if (toolCallTracker.markStarted(tool.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tool.getToolCallId(), tool.getToolCallName()));
        }
        delta.upsertTool(tool.getToolCallId(), tool.getToolCallName(), null, null, TOOL_RUNNING, false, false);
    }

    private void mapToolResultTextDelta(AgentEvent event, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolResultTextDeltaEvent tool)) {
            return;
        }
        toolCallTracker.appendResult(tool.getToolCallId(), tool.getDelta());
        delta.upsertTool(
                tool.getToolCallId(), tool.getToolCallName(), null, tool.getDelta(), TOOL_RUNNING, false, false);
    }

    private void mapToolResultEnd(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof ToolResultEndEvent tool)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tool.getToolCallId()));
        String content = toolCallTracker.result(tool.getToolCallId());
        out.add(new AguiEvent.ToolCallResult(
                threadId, runId, tool.getToolCallId(), content, "tool", tool.getReplyId()));
        delta.upsertTool(tool.getToolCallId(), tool.getToolCallName(), null, null, TOOL_COMPLETE, false, false);
    }

    private void mapRequireUserConfirm(AgentEvent event, List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (!(event instanceof RequireUserConfirmEvent confirm)) {
            return;
        }
        closeActiveMessage(out, delta);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, buildInterruptOutcome(confirm.getToolCalls())));
        delta.awaiting(confirm.getToolCalls().stream()
                .map(tool -> new ChatRunSnapshot.ToolCall(tool.getId(), tool.getName(), "", "", TOOL_ASKING))
                .toList());
    }

    private AguiEvent.RunFinishedInterruptOutcome buildInterruptOutcome(List<ToolUseBlock> blocks) {
        List<AguiEvent.Interrupt> interrupts = new ArrayList<>();
        for (ToolUseBlock toolUse : blocks) {
            interrupts.add(new AguiEvent.Interrupt(
                    toolUse.getId(),
                    "human_confirmation_required",
                    "工具 '" + toolUse.getName() + "' 需要您确认后执行",
                    toolUse.getId(),
                    null,
                    null,
                    Map.of("toolName", toolUse.getName())));
        }
        return new AguiEvent.RunFinishedInterruptOutcome(interrupts);
    }

    /**
     * 关闭当前打开的文本和推理消息；除为已知内存消息生成 {@code TextMessageEnd}/{@code ReasoningMessageEnd} 外，
     * 无条件在快照增量上置 {@code closeActiveMessages=true}，使无内存消息 ID 的恢复实例也能闭合持久化快照中的打开状态，
     * 是关闭路径的唯一出口。
     *
     * @return 仅包含消息关闭事件与对应快照增量的解释结果
     */
    public AgentEventInterpretation closeOpenMessages() {
        List<AguiEvent> out = new ArrayList<>();
        SnapshotDeltaBuilder delta = new SnapshotDeltaBuilder();
        closeActiveMessage(out, delta);
        delta.closeActiveMessages();
        return new AgentEventInterpretation(List.copyOf(out), delta.build());
    }

    private void closeActiveMessage(List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        closeTextMessage(out, delta);
        closeReasoningMessage(out, delta);
    }

    private void closeTextMessage(List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (textMessageId != null) {
            out.add(new AguiEvent.TextMessageEnd(threadId, runId, textMessageId));
            delta.closeText();
            textMessageId = null;
        }
    }

    private void closeReasoningMessage(List<AguiEvent> out, SnapshotDeltaBuilder delta) {
        if (reasoningMessageId != null) {
            out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
            delta.closeReasoning();
            reasoningMessageId = null;
        }
        if (reasoningStarted) {
            out.add(new AguiEvent.ReasoningEnd(threadId, runId, reasoningGroupId));
            reasoningStarted = false;
            reasoningGroupId = null;
        }
    }

    private String resolveId(String replyId) {
        return StringUtils.isNotBlank(replyId) ? replyId : runId;
    }

    /** AG-UI 工具调用状态记录器。 */
    private static final class AguiToolCallTracker {

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

    /** 快照增量构建器。 */
    private static final class SnapshotDeltaBuilder {
        private String textMessageId;
        private String textDelta;
        private String reasoningMessageId;
        private String reasoningDelta;
        private boolean closeText;
        private boolean closeReasoning;
        private boolean closeActiveMessages;
        private final List<ChatRunSnapshotDelta.ToolDelta> tools = new ArrayList<>();
        private List<ChatRunSnapshot.ToolCall> awaitingTools = List.of();

        void appendText(String messageId, String delta) {
            textMessageId = messageId;
            textDelta = textDelta == null ? delta : textDelta + delta;
        }

        void appendReasoning(String messageId, String delta) {
            reasoningMessageId = messageId;
            reasoningDelta = reasoningDelta == null ? delta : reasoningDelta + delta;
        }

        void closeText() {
            closeText = true;
        }

        void closeReasoning() {
            closeReasoning = true;
        }

        void closeActiveMessages() {
            closeActiveMessages = true;
        }

        void upsertTool(
                String toolCallId,
                String toolCallName,
                String argsDelta,
                String resultDelta,
                String status,
                boolean replaceArgs,
                boolean replaceResult) {
            tools.add(new ChatRunSnapshotDelta.ToolDelta(
                    toolCallId, toolCallName, argsDelta, resultDelta, status, replaceArgs, replaceResult));
        }

        void awaiting(List<ChatRunSnapshot.ToolCall> toolsToAwait) {
            awaitingTools = List.copyOf(toolsToAwait);
            closeActiveMessages = true;
        }

        ChatRunSnapshotDelta build() {
            return new ChatRunSnapshotDelta(
                    textMessageId,
                    textDelta,
                    reasoningMessageId,
                    reasoningDelta,
                    closeText,
                    closeReasoning,
                    closeActiveMessages,
                    List.copyOf(tools),
                    awaitingTools);
        }
    }
}
