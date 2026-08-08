package com.lambda.fusion.ai.chat.adapter;

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
 * <p>工具调用累积：流结束后通过 {@link #getToolCalls()} 获取工具调用快照，
 * 供持久化为历史回放数据，结构与前端 {@code ToolCallRenderer} 的 toolCall prop 对齐。
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

    /** 工具调用累积器（toolCallId -> args/result），流结束后供持久化。 */
    private final Map<String, ToolCallAccumulator> toolCallAccumulators = new HashMap<>();

    /** 已完成的工具调用快照（供历史回放持久化）。 */
    private final List<ToolCallRecord> completedToolCalls = new ArrayList<>();

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
            case REQUIRE_USER_CONFIRM -> mapRequireUserConfirm(event, result);
            case AGENT_END -> mapAgentEnd(result);
            default -> {
                /* 未映射事件忽略；Activity 后续补 */
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

    /**
     * 已完成工具调用快照（流结束后调用），供持久化为历史回放数据。
     *
     * <p>结构与前端 {@code ToolCallRenderer} 的 toolCall prop 对齐
     * （toolCallId / toolCallName / args / result），前端 mapHistory 据此构造
     * toolcall 内容块回放。
     *
     * @return 不可变工具调用快照列表
     */
    public List<ToolCallRecord> getToolCalls() {
        return List.copyOf(completedToolCalls);
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
        toolCallAccumulators.computeIfAbsent(
                tc.getToolCallId(), k -> new ToolCallAccumulator(tc.getToolCallId(), tc.getToolCallName()));
    }

    private void mapToolCallDelta(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolCallDeltaEvent tc)) {
            return;
        }
        out.add(new AguiEvent.ToolCallArgs(threadId, runId, tc.getToolCallId(), tc.getDelta()));
        ToolCallAccumulator acc = toolCallAccumulators.get(tc.getToolCallId());
        if (acc != null) {
            acc.args.append(tc.getDelta());
        }
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
        toolCallAccumulators.computeIfAbsent(
                tr.getToolCallId(), k -> new ToolCallAccumulator(tr.getToolCallId(), tr.getToolCallName()));
    }

    private void mapToolResultTextDelta(AgentEvent event) {
        if (!(event instanceof ToolResultTextDeltaEvent tr)) {
            return;
        }
        ToolCallAccumulator acc = toolCallAccumulators.get(tr.getToolCallId());
        if (acc != null) {
            acc.result.append(tr.getDelta());
        }
    }

    private void mapToolResultEnd(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof ToolResultEndEvent tr)) {
            return;
        }
        out.add(new AguiEvent.ToolCallEnd(threadId, runId, tr.getToolCallId()));
        ToolCallAccumulator acc = toolCallAccumulators.get(tr.getToolCallId());
        String content = acc != null ? acc.result.toString() : "";
        out.add(new AguiEvent.ToolCallResult(threadId, runId, tr.getToolCallId(), content, "tool", tr.getReplyId()));
        completedToolCalls.add(new ToolCallRecord(
                tr.getToolCallId(),
                acc != null ? acc.toolCallName : tr.getToolCallName(),
                acc != null ? acc.args.toString() : "",
                content));
    }

    private void mapAgentEnd(List<AguiEvent> out) {
        closeActiveMessage(out);
        out.add(new AguiEvent.RunFinished(threadId, runId, null, new AguiEvent.RunFinishedSuccessOutcome()));
    }

    /**
     * 映射 HITL 确认请求为 RunFinished(interrupt)：agent 暂停，前端展示确认 UI。
     *
     * <p>每个待确认 ToolUseBlock 产出一个 {@link AguiEvent.Interrupt}（toolCallId 锚定），
     * 前端用户确认后调回传端点 {@code POST /sessions/{id}/confirm} 恢复。这是 AG-UI 协议
     * 的标准 interrupt 机制（RunFinished + RunFinishedInterruptOutcome），对应 agentscope
     * 的 {@link RequireUserConfirmEvent}。
     *
     * @param event agentscope 确认请求事件
     * @param out 输出列表
     */
    private void mapRequireUserConfirm(AgentEvent event, List<AguiEvent> out) {
        if (!(event instanceof RequireUserConfirmEvent ruc)) {
            return;
        }
        closeActiveMessage(out);
        List<AguiEvent.Interrupt> interrupts = new ArrayList<>();
        for (ToolUseBlock toolUse : ruc.getToolCalls()) {
            interrupts.add(new AguiEvent.Interrupt(
                    toolUse.getId(),
                    "human_confirmation_required",
                    "工具 '" + toolUse.getName() + "' 需要您确认后执行",
                    toolUse.getId(),
                    null,
                    null,
                    Map.of("toolName", toolUse.getName())));
        }
        out.add(new AguiEvent.RunFinished(
                threadId, runId, null, new AguiEvent.RunFinishedInterruptOutcome(interrupts)));
    }

    /**
     * 映射流异常为 RunError 事件（先关闭活跃消息，再发出错误）。
     *
     * <p>message 取异常 message，为空时退回异常类名；code 暂留 null。
     *
     * @param error 异常
     * @return 含 RunError 的事件列表
     */
    public List<AguiEvent> mapError(Throwable error) {
        List<AguiEvent> out = new ArrayList<>();
        closeActiveMessage(out);
        String message = error.getMessage();
        out.add(new AguiEvent.RunError(
                threadId, runId, message != null ? message : error.getClass().getSimpleName(), null));
        return out;
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

    private String resolveId(String replyId) {
        return StringUtils.isNotBlank(replyId) ? replyId : runId;
    }

    /** 工具调用累积状态（args/result 增量拼接）。 */
    private static final class ToolCallAccumulator {
        final String toolCallId;
        final String toolCallName;
        final StringBuilder args = new StringBuilder();
        final StringBuilder result = new StringBuilder();

        ToolCallAccumulator(String toolCallId, String toolCallName) {
            this.toolCallId = toolCallId;
            this.toolCallName = toolCallName;
        }
    }

    /**
     * 工具调用快照（供持久化与历史回放）。
     *
     * <p>字段与前端 {@code ToolCall} 结构对齐：toolCallId / toolCallName / args / result。
     * Jackson 序列化为 {@code {"toolCallId":...,"toolCallName":...,"args":...,"result":...}}，
     * 前端直接作为 toolcall 内容块的 data 传给 ToolCallRenderer。
     */
    public record ToolCallRecord(String toolCallId, String toolCallName, String args, String result) {}
}
