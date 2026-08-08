package com.lambda.fusion.ai.chat.adapter;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * agentscope AgentEvent -> AG-UI AguiEvent 映射器。
 *
 * <p>消费 v2 {@code streamEvents} 的细粒度事件流，映射为 AG-UI 协议事件，
 * 供前端 TDesign AGUIAdapter 直接消费。每个对话流新建一个实例，内部维护
 * messageId / toolCallId 状态以正确配对 START/CONTENT/END。
 *
 * <p>覆盖：文本(TEXT_MESSAGE_*)、推理(REASONING_MESSAGE_*，开关)、工具调用
 * (TOOL_CALL_START/ARGS/END)、工具结果(TOOL_CALL_RESULT)。HITL
 * (REQUIRE_USER_CONFIRM)、Activity(CUSTOM) 暂未映射，留待后续。
 *
 * @author Jin
 */
public class AguiEventMapper {

    private final String threadId;
    private final String runId;
    private final boolean enableReasoning;
    private final AguiEventEncoder encoder;

    /** 当前文本消息 id（replyId）；切换时关闭上一条。 */
    private String textMessageId;

    /** 当前推理消息 id。 */
    private String reasoningMessageId;

    /** 已发 ToolCallStart 的 toolCallId（避免重复）。 */
    private final Set<String> startedToolCalls = new HashSet<>();

    /** 工具结果文本累积（toolCallId -> delta 拼接），TOOL_RESULT_END 时作为 content 发出。 */
    private final Map<String, StringBuilder> toolResultText = new HashMap<>();

    public AguiEventMapper(String threadId, String runId, boolean enableReasoning) {
        this.threadId = threadId;
        this.runId = runId;
        this.enableReasoning = enableReasoning;
        this.encoder = new AguiEventEncoder();
    }

    /**
     * 映射单个 AgentEvent 为 0..n 个 AguiEvent（一条事件可能产出 START+CONTENT 等）。
     *
     * @param event agentscope 原始事件
     * @return 映射后的 AG-UI 事件列表（可能为空，表示该事件不产出 AG-UI 事件）
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
            case AGENT_END -> mapAgentEnd(result);
            default -> {
                /* 未映射事件忽略；HITL/Activity 后续补 */
            }
        }
        return result;
    }

    /**
     * 编码为 SSE data 字段值（JSON 字符串，配合 SseEmitter.event().data() 使用）。
     *
     * <p>返回带前导空格的 JSON，SseEmitter 生成 "data: {json}"，符合 AG-UI SSE 格式。
     *
     * @param event AG-UI 事件
     * @return SSE data 字段值
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
            if (textMessageId != null) {
                out.add(new AguiEvent.TextMessageEnd(threadId, runId, textMessageId));
            }
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
            if (reasoningMessageId != null) {
                out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
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
        if (startedToolCalls.add(tc.getToolCallId())) {
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
        // 补 ToolCallStart（若上游未发，如直接进结果阶段）
        if (startedToolCalls.add(tr.getToolCallId())) {
            out.add(new AguiEvent.ToolCallStart(threadId, runId, tr.getToolCallId(), tr.getToolCallName()));
        }
        toolResultText.putIfAbsent(tr.getToolCallId(), new StringBuilder());
    }

    private void mapToolResultTextDelta(AgentEvent event) {
        if (!(event instanceof ToolResultTextDeltaEvent tr)) {
            return;
        }
        toolResultText
                .computeIfAbsent(tr.getToolCallId(), k -> new StringBuilder())
                .append(tr.getDelta());
    }

    private void mapToolResultEnd(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultEndEvent tr)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tr.getToolCallId()));
        String content = drainToolResult(tr.getToolCallId());
        out.add(new AguiEvent.ToolCallResult(threadId, runId, tr.getToolCallId(), content, "tool", tr.getReplyId()));
    }

    private void mapAgentEnd(List<AguiEvent> out) {
        closeActiveMessage(out);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, new AguiEvent.RunFinishedSuccessOutcome()));
    }

    /** 关闭活跃的文本/推理消息（工具调用开始或 run 结束前调用）。 */
    private void closeActiveMessage(List<AguiEvent> out) {
        if (textMessageId != null) {
            out.add(new AguiEvent.TextMessageEnd(threadId, runId, textMessageId));
            textMessageId = null;
        }
        if (reasoningMessageId != null) {
            out.add(new AguiEvent.ReasoningMessageEnd(threadId, runId, reasoningMessageId));
            reasoningMessageId = null;
        }
    }

    private String drainToolResult(String toolCallId) {
        StringBuilder sb = toolResultText.remove(toolCallId);
        return sb != null ? sb.toString() : "";
    }

    private String resolveId(String replyId) {
        return StringUtils.isNotBlank(replyId) ? replyId : runId;
    }
}
