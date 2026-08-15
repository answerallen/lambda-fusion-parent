package com.lambda.fusion.ai.chat.runtime.agui;

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
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * AgentScope 事件到 AG-UI 事件的映射器。
 *
 * <p>实例按运行阶段创建，负责维护文本、推理和工具调用事件的配对状态。
 *
 * @author Jin
 */
public class AguiEventMapper {

    private final String threadId;
    private final String runId;
    private final boolean enableReasoning;
    private final AguiEventEncoder encoder;

    private String textMessageId;
    private String reasoningMessageId;
    private boolean reasoningStarted;
    private String reasoningGroupId;

    private final AguiToolCallTracker toolCallTracker = new AguiToolCallTracker();

    /**
     * 创建事件映射器。
     *
     * @param threadId AG-UI 线程标识
     * @param runId AG-UI 运行标识
     * @param enableReasoning 是否输出推理事件
     */
    public AguiEventMapper(String threadId, String runId, boolean enableReasoning) {
        this.threadId = threadId;
        this.runId = runId;
        this.enableReasoning = enableReasoning;
        this.encoder = new AguiEventEncoder();
    }

    /**
     * 将单个 AgentScope 事件映射为 AG-UI 事件。
     *
     * @param event AgentScope 事件
     * @return AG-UI 事件列表；无对应协议事件时返回空列表
     */
    public List<AguiEvent> map(AgentEvent event) {
        List<AguiEvent> result = new ArrayList<>();
        AgentEventType type = event.getType();
        switch (type) {
            case AGENT_START -> result.add(new AguiEvent.RunStarted(threadId, runId, null, null));
            case TEXT_BLOCK_DELTA -> mapTextDelta(event, result);
            case THINKING_BLOCK_DELTA -> mapThinkingDelta(event, result);
            case TOOL_CALL_START -> mapToolCallStart(event, result);
            case TOOL_CALL_DELTA -> mapToolCallDelta(event, result);
            case TOOL_CALL_END -> mapToolCallEnd(event, result);
            case TOOL_RESULT_START -> mapToolResultStart(event, result);
            case TOOL_RESULT_TEXT_DELTA -> mapToolResultTextDelta(event);
            case TOOL_RESULT_END -> mapToolResultEnd(event, result);
            case REQUIRE_USER_CONFIRM -> mapRequireUserConfirm(event, result);
            case AGENT_END -> mapAgentEnd(result);
            default -> {}
        }
        return result;
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

    private void mapTextDelta(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof TextBlockDeltaEvent delta)) {
            return;
        }
        String msgId = resolveId(delta.getReplyId());
        if (!msgId.equals(textMessageId)) {
            closeReasoningMessage(out);
            closeTextMessage(out);
            textMessageId = msgId;
            out.add(new AguiEvent.TextMessageStart(threadId, runId, msgId, "assistant"));
        }
        out.add(new AguiEvent.TextMessageContent(threadId, runId, msgId, delta.getDelta()));
    }

    private void mapThinkingDelta(AgentEvent event, List<AguiEvent> out) {
        if (!enableReasoning || !(event instanceof ThinkingBlockDeltaEvent delta)) {
            return;
        }
        String msgId = resolveId(delta.getReplyId());
        if (!msgId.equals(reasoningMessageId)) {
            closeTextMessage(out);
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
        out.add(new AguiEvent.ReasoningMessageContent(threadId, runId, msgId, delta.getDelta()));
    }

    private void mapToolCallStart(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolCallStartEvent tc)) {
            return;
        }
        closeActiveMessage(out);
        if (toolCallTracker.markStarted(tc.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tc.getToolCallId(), tc.getToolCallName()));
        }
    }

    private void mapToolCallDelta(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolCallDeltaEvent tc)) {
            return;
        }
        out.add(new AguiEvent.ToolCallArgs(threadId, runId, tc.getToolCallId(), tc.getDelta()));
    }

    private void mapToolCallEnd(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolCallEndEvent tc)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tc.getToolCallId()));
    }

    private void mapToolResultStart(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultStartEvent tr)) {
            return;
        }
        closeActiveMessage(out);
        if (toolCallTracker.markStarted(tr.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tr.getToolCallId(), tr.getToolCallName()));
        }
    }

    private void mapToolResultTextDelta(AgentEvent event) {
        if (!(event instanceof ToolResultTextDeltaEvent tr)) {
            return;
        }
        toolCallTracker.appendResult(tr.getToolCallId(), tr.getDelta());
    }

    private void mapToolResultEnd(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultEndEvent tr)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tr.getToolCallId()));
        String content = toolCallTracker.result(tr.getToolCallId());
        out.add(new AguiEvent.ToolCallResult(threadId, runId, tr.getToolCallId(), content, "tool", tr.getReplyId()));
    }

    private void mapAgentEnd(List<AguiEvent> out) {
        closeActiveMessage(out);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, new AguiEvent.RunFinishedSuccessOutcome()));
    }

    private void mapRequireUserConfirm(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof RequireUserConfirmEvent ruc)) {
            return;
        }
        closeActiveMessage(out);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, buildInterruptOutcome(ruc.getToolCalls())));
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
     * 将执行异常映射为 AG-UI 错误事件。
     *
     * @param error 执行异常
     * @return 包含消息关闭事件和运行错误事件的列表
     */
    public List<AguiEvent> mapError(Throwable error) {
        List<AguiEvent> out = new ArrayList<>();
        closeActiveMessage(out);
        String message = error.getMessage();
        out.add(new AguiEvent.RunError(
                threadId, runId, message != null ? message : error.getClass().getSimpleName(), null));
        return out;
    }

    /**
     * 生成正常完成事件。
     *
     * @return 包含消息关闭事件和运行完成事件的列表
     */
    public List<AguiEvent> mapCompletion() {
        List<AguiEvent> out = new ArrayList<>();
        mapAgentEnd(out);
        return out;
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
        return StringUtils.isNotBlank(replyId) ? replyId : runId;
    }
}
