package com.lambda.fusion.ai.chat.runtime.agui;

import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
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
 * AgentScope 细粒度事件到官方 AG-UI 事件的薄映射器。
 *
 * <p>本类只处理协议事件配对与工具结果缓冲，不解释业务 Run 状态，也不生成另一套快照增量。运行中快照直接投影本类输出的
 * {@link AguiEvent}。
 *
 * @author Jin
 */
public final class AgentEventMapper {

    private final String threadId;
    private final String runId;
    private final boolean enableReasoning;
    private final ToolCallTracker toolCallTracker = new ToolCallTracker();

    private String textMessageId;
    private String reasoningMessageId;
    private boolean reasoningStarted;
    private String reasoningGroupId;

    public AgentEventMapper(String threadId, String runId, boolean enableReasoning) {
        this.threadId = threadId;
        this.runId = runId;
        this.enableReasoning = enableReasoning;
    }

    /** 将一个 AgentScope 事件映射为零到多个标准 AG-UI 事件。 */
    public List<AguiEvent> map(AgentEvent event) {
        List<AguiEvent> events = new ArrayList<>();
        AgentEventType type = event.getType();
        switch (type) {
            case AGENT_START -> mapAgentStart(event, events);
            case TEXT_BLOCK_DELTA -> mapTextDelta(event, events);
            case THINKING_BLOCK_DELTA -> mapThinkingDelta(event, events);
            case TOOL_CALL_START -> mapToolCallStart(event, events);
            case TOOL_CALL_DELTA -> mapToolCallDelta(event, events);
            case TOOL_RESULT_START -> mapToolResultStart(event, events);
            case TOOL_RESULT_TEXT_DELTA -> mapToolResultTextDelta(event);
            case TOOL_RESULT_END -> mapToolResultEnd(event, events);
            case REQUIRE_USER_CONFIRM -> mapRequireUserConfirm(event, events);
            default -> {
                // AGENT_END 由运行实例决定业务完成语义；其余事件当前没有对应的 UI 投影。
            }
        }
        return List.copyOf(events);
    }

    /** 关闭本阶段已打开的文本/推理事件。 */
    public List<AguiEvent> closeOpenMessages() {
        List<AguiEvent> events = new ArrayList<>();
        closeActiveMessage(events);
        return List.copyOf(events);
    }

    private void mapAgentStart(AgentEvent event, List<AguiEvent> out) {
        if (event.getSource() == null) {
            out.add(new AguiEvent.RunStarted(threadId, runId, null, null));
        }
    }

    private void mapTextDelta(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof TextBlockDeltaEvent textDelta)) {
            return;
        }
        String messageId = resolveId(textDelta.getReplyId());
        if (!messageId.equals(textMessageId)) {
            closeReasoningMessage(out);
            closeTextMessage(out);
            textMessageId = messageId;
            out.add(new AguiEvent.TextMessageStart(threadId, runId, messageId, "assistant"));
        }
        out.add(new AguiEvent.TextMessageContent(threadId, runId, messageId, textDelta.getDelta()));
    }

    private void mapThinkingDelta(AgentEvent event, List<AguiEvent> out) {
        if (!enableReasoning || !(event instanceof ThinkingBlockDeltaEvent thinkingDelta)) {
            return;
        }
        String messageId = resolveId(thinkingDelta.getReplyId());
        if (!messageId.equals(reasoningMessageId)) {
            closeTextMessage(out);
            if (reasoningMessageId != null) {
                out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
            }
            if (!reasoningStarted) {
                out.add(new AguiEvent.ReasoningStart(threadId, runId, messageId, null));
                reasoningStarted = true;
                reasoningGroupId = messageId;
            }
            reasoningMessageId = messageId;
            out.add(new AguiEvent.ReasoningMessageStart(threadId, runId, messageId, "reasoning"));
        }
        out.add(new AguiEvent.ReasoningMessageContent(threadId, runId, messageId, thinkingDelta.getDelta()));
    }

    private void mapToolCallStart(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolCallStartEvent tool)) {
            return;
        }
        closeActiveMessage(out);
        if (toolCallTracker.markStarted(tool.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tool.getToolCallId(), tool.getToolCallName()));
        }
    }

    private void mapToolCallDelta(AgentEvent event, List<AguiEvent> out) {
        if (event instanceof ToolCallDeltaEvent tool) {
            out.add(new AguiEvent.ToolCallArgs(threadId, runId, tool.getToolCallId(), tool.getDelta()));
        }
    }

    private void mapToolResultStart(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultStartEvent tool)) {
            return;
        }
        closeActiveMessage(out);
        if (toolCallTracker.markStarted(tool.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tool.getToolCallId(), tool.getToolCallName()));
        }
    }

    private void mapToolResultTextDelta(AgentEvent event) {
        if (event instanceof ToolResultTextDeltaEvent tool) {
            toolCallTracker.appendResult(tool.getToolCallId(), tool.getDelta());
        }
    }

    private void mapToolResultEnd(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultEndEvent tool)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tool.getToolCallId()));
        out.add(new AguiEvent.ToolCallResult(
                threadId,
                runId,
                tool.getToolCallId(),
                toolCallTracker.result(tool.getToolCallId()),
                "tool",
                tool.getReplyId()));
    }

    private void mapRequireUserConfirm(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof RequireUserConfirmEvent confirm)) {
            return;
        }
        closeActiveMessage(out);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, buildInterruptOutcome(confirm.getToolCalls())));
    }

    private static AguiEvent.RunFinishedInterruptOutcome buildInterruptOutcome(List<ToolUseBlock> blocks) {
        List<AguiEvent.Interrupt> interrupts = blocks.stream()
                .map(tool -> new AguiEvent.Interrupt(
                        tool.getId(),
                        "human_confirmation_required",
                        "工具 '" + tool.getName() + "' 需要您确认后执行",
                        tool.getId(),
                        null,
                        null,
                        Map.of("toolName", tool.getName())))
                .toList();
        return new AguiEvent.RunFinishedInterruptOutcome(interrupts);
    }

    private void closeActiveMessage(List<AguiEvent> out) {
        closeTextMessage(out);
        closeReasoningMessage(out);
    }

    private void closeTextMessage(List<AguiEvent> out) {
        if (textMessageId != null) {
            out.add(new AguiEvent.TextMessageEnd(threadId, runId, textMessageId));
            textMessageId = null;
        }
    }

    private void closeReasoningMessage(List<AguiEvent> out) {
        if (reasoningMessageId != null) {
            out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
            reasoningMessageId = null;
        }
        if (reasoningStarted) {
            out.add(new AguiEvent.ReasoningEnd(threadId, runId, reasoningGroupId));
            reasoningStarted = false;
            reasoningGroupId = null;
        }
    }

    private String resolveId(String replyId) {
        return StringUtils.defaultIfBlank(replyId, runId);
    }

    private static final class ToolCallTracker {

        private final Set<String> startedToolCalls = new HashSet<>();
        private final Map<String, StringBuilder> resultBuffers = new HashMap<>();

        boolean markStarted(String toolCallId) {
            resultBuffers.computeIfAbsent(toolCallId, ignored -> new StringBuilder());
            return startedToolCalls.add(toolCallId);
        }

        void appendResult(String toolCallId, String delta) {
            resultBuffers
                    .computeIfAbsent(toolCallId, ignored -> new StringBuilder())
                    .append(delta);
        }

        String result(String toolCallId) {
            StringBuilder result = resultBuffers.get(toolCallId);
            return result == null ? "" : result.toString();
        }
    }
}
