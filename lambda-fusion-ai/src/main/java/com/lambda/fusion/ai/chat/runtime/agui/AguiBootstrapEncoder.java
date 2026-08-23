package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiConstants.ChatRunToolStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** 用官方 AG-UI 事件重建一个运行中的助手气泡。引导事件本身不写入事件缓冲。 */
public final class AguiBootstrapEncoder {

    private static final String TOOL_COMPLETE = ChatRunToolStatus.COMPLETE.getCode();

    private AguiBootstrapEncoder() {}

    /** 构造"需要用户确认"的 AG-UI Interrupt 事件。 */
    static AguiEvent.Interrupt confirmationInterrupt(String toolCallId, String toolName) {
        return new AguiEvent.Interrupt(
                toolCallId,
                "human_confirmation_required",
                "工具 '" + toolName + "' 需要您确认后执行",
                toolCallId,
                null,
                null,
                Map.of("toolName", toolName));
    }

    /** 从待输入投影重建输入型 AG-UI Interrupt 事件（重连恢复交互卡片）。 */
    private static AguiEvent.Interrupt inputInterrupt(ChatRunSnapshot.PendingInput input) {
        Map<String, Object> responseSchema = null;
        if (!input.responseSchemaJson().isBlank()) {
            Map<?, ?> decoded = JsonUtils.getJsonCodec().fromJson(input.responseSchemaJson(), Map.class);
            Map<String, Object> schema = new LinkedHashMap<>();
            decoded.forEach((key, value) -> schema.put(String.valueOf(key), value));
            responseSchema = schema;
        }
        return new AguiEvent.Interrupt(
                input.toolCallId(),
                InterruptFactory.REASON_INPUT_REQUIRED,
                input.question(),
                input.toolCallId(),
                responseSchema,
                null,
                Map.of(
                        InterruptFactory.METADATA_TOOL_NAME,
                        input.toolCallName(),
                        InterruptFactory.METADATA_INPUT_KIND,
                        input.inputKind()));
    }

    /**
     * 根据运行实体与快照重建引导事件序列：RunStarted -> 推理 -> 工具调用 -> 文本 -> 终态/中断。
     * 事件顺序模拟真实流式过程，保证前端渲染出与断线前一致的助手气泡。
     *
     * @param run 运行实体（含状态与终态信息）
     * @param snapshot 已持久化的执行快照
     * @return 编码后的引导事件 JSON 列表
     */
    public static List<String> encode(ChatRunEntity run, ChatRunSnapshot snapshot) {
        EventCollector collector = new EventCollector(run);
        collector.add(new AguiEvent.RunStarted(run.getSessionId(), run.getAguiRunId(), null, null), run.getPhaseNo());
        boolean reopenReasoningAfterTools = appendReasoning(collector, snapshot);
        appendTools(collector, snapshot);
        if (reopenReasoningAfterTools) {
            reopenReasoning(collector, snapshot);
        }
        appendText(collector, snapshot);
        appendTerminal(collector, run, snapshot);
        return collector.events();
    }

    /**
     * 重建推理气泡：快照推理未闭合且已有工具调用时，先闭合本段、待工具调用输出后再重开
     *（工具事件必须落在两条消息之间），此时返回 {@code true} 由调用方重开。
     *
     * @param collector 事件收集器
     * @param snapshot 执行快照
     * @return 是否需要在工具调用后重开推理消息
     */
    private static boolean appendReasoning(EventCollector collector, ChatRunSnapshot snapshot) {
        if (snapshot.reasoning().isEmpty()) {
            return false;
        }
        String messageId = valueOrDefault(snapshot.reasoningMessageId(), "reasoning-" + collector.chatRunId());
        collector.add(new AguiEvent.ReasoningStart(collector.threadId(), collector.aguiRunId(), messageId, null));
        collector.add(new AguiEvent.ReasoningMessageStart(
                collector.threadId(), collector.aguiRunId(), messageId, "reasoning"));
        collector.add(new AguiEvent.ReasoningMessageContent(
                collector.threadId(), collector.aguiRunId(), messageId, snapshot.reasoning()));
        boolean reopenAfterTools = snapshot.reasoningOpen() && !snapshot.tools().isEmpty();
        if (!snapshot.reasoningOpen() || reopenAfterTools) {
            collector.add(new AguiEvent.ReasoningMessageEnd(collector.threadId(), collector.aguiRunId(), messageId));
            collector.add(new AguiEvent.ReasoningEnd(collector.threadId(), collector.aguiRunId(), messageId));
        }
        return reopenAfterTools;
    }

    /** 重建工具调用事件：按快照顺序输出 Start/Args，仅 COMPLETE 的工具补 End 与 Result。 */
    private static void appendTools(EventCollector collector, ChatRunSnapshot snapshot) {
        for (ChatRunSnapshot.ToolCall tool : snapshot.tools()) {
            collector.add(new AguiEvent.ToolCallStart(
                    collector.threadId(), collector.aguiRunId(), tool.toolCallId(), tool.toolCallName()));
            if (!tool.args().isEmpty()) {
                collector.add(new AguiEvent.ToolCallArgs(
                        collector.threadId(), collector.aguiRunId(), tool.toolCallId(), tool.args()));
            }
            if (!TOOL_COMPLETE.equals(tool.status())) {
                continue;
            }
            collector.add(new AguiEvent.ToolCallEnd(collector.threadId(), collector.aguiRunId(), tool.toolCallId()));
            if (!tool.result().isEmpty()) {
                collector.add(new AguiEvent.ToolCallResult(
                        collector.threadId(),
                        collector.aguiRunId(),
                        tool.toolCallId(),
                        tool.result(),
                        "tool",
                        "tool-result-" + tool.toolCallId()));
            }
        }
    }

    /** 在工具调用之后重开此前未闭合的推理消息，续用原消息标识。 */
    private static void reopenReasoning(EventCollector collector, ChatRunSnapshot snapshot) {
        String messageId = valueOrDefault(snapshot.reasoningMessageId(), "reasoning-" + collector.chatRunId());
        collector.add(new AguiEvent.ReasoningStart(collector.threadId(), collector.aguiRunId(), messageId, null));
        collector.add(new AguiEvent.ReasoningMessageStart(
                collector.threadId(), collector.aguiRunId(), messageId, "reasoning"));
    }

    /** 重建回复文本气泡：快照文本未闭合时不输出 End，等待实时流续接同一条消息。 */
    private static void appendText(EventCollector collector, ChatRunSnapshot snapshot) {
        if (snapshot.text().isEmpty()) {
            return;
        }
        String messageId = valueOrDefault(snapshot.textMessageId(), "message-" + collector.chatRunId());
        collector.add(
                new AguiEvent.TextMessageStart(collector.threadId(), collector.aguiRunId(), messageId, "assistant"));
        collector.add(new AguiEvent.TextMessageContent(
                collector.threadId(), collector.aguiRunId(), messageId, snapshot.text()));
        if (!snapshot.textOpen()) {
            collector.add(new AguiEvent.TextMessageEnd(collector.threadId(), collector.aguiRunId(), messageId));
        }
    }

    /**
     * 按运行终局补写收尾事件：待交互态（待确认/待输入）输出带 Interrupt 的 RunFinished；
     * COMPLETED/STOPPED 输出成功的 RunFinished；FAILED 输出 RunError（带失败码与消息）。
     * RUNNING 且无待交互投影时不输出收尾事件，由实时流续接。
     *
     * @param collector 事件收集器
     * @param run 运行实体
     * @param snapshot 执行快照
     */
    private static void appendTerminal(EventCollector collector, ChatRunEntity run, ChatRunSnapshot snapshot) {
        if (ChatRunStatus.RUNNING.name().equals(run.getStatus()) && snapshot.hasPendingInteraction()) {
            List<AguiEvent.Interrupt> interrupts = new ArrayList<>();
            snapshot.pendingTools().stream()
                    .map(tool -> confirmationInterrupt(tool.toolCallId(), tool.toolCallName()))
                    .forEach(interrupts::add);
            snapshot.pendingInputs().stream()
                    .map(AguiBootstrapEncoder::inputInterrupt)
                    .forEach(interrupts::add);
            collector.add(new AguiEvent.RunFinished(
                    collector.threadId(),
                    collector.aguiRunId(),
                    null,
                    new AguiEvent.RunFinishedInterruptOutcome(interrupts)));
            return;
        }
        if (ChatRunStatus.COMPLETED.name().equals(run.getStatus())
                || ChatRunStatus.STOPPED.name().equals(run.getStatus())) {
            collector.addTerminal(
                    new AguiEvent.RunFinished(
                            collector.threadId(),
                            collector.aguiRunId(),
                            null,
                            new AguiEvent.RunFinishedSuccessOutcome()),
                    run);
            return;
        }
        if (ChatRunStatus.FAILED.name().equals(run.getStatus())) {
            collector.addTerminal(
                    new AguiEvent.RunError(
                            collector.threadId(),
                            collector.aguiRunId(),
                            StringUtils.defaultIfBlank(run.getErrorMessage(), "对话运行失败"),
                            run.getErrorCode()),
                    run);
        }
    }

    /** 空白值回退到默认值，用于快照缺消息标识时合成稳定 ID。 */
    private static String valueOrDefault(String value, String fallback) {
        return StringUtils.defaultIfBlank(value, fallback);
    }

    /** 引导事件收集器：统一编码为 JSON 并补充 Run 元数据，终态事件额外附加终态信息。 */
    private static final class EventCollector {

        private final ChatRunEntity run;
        private final List<String> events = new ArrayList<>();

        private EventCollector(ChatRunEntity run) {
            this.run = run;
        }

        /** 会话即线程标识。 */
        private String threadId() {
            return run.getSessionId();
        }

        /** 业务运行标识。 */
        private String chatRunId() {
            return run.getId();
        }

        /** AG-UI 运行标识。 */
        private String aguiRunId() {
            return run.getAguiRunId();
        }

        /** 编码并追加事件（无阶段号元数据）。 */
        private void add(AguiEvent event) {
            add(event, null);
        }

        /** 编码普通引导事件；{@code phaseNo} 非空时附加到事件元数据。 */
        private void add(AguiEvent event, Integer phaseNo) {
            events.add(AguiEventJsonCodec.encodeBootstrapEvent(event, run.getId(), phaseNo));
        }

        /** 编码终态事件并附加运行状态与结束原因元数据。 */
        private void addTerminal(AguiEvent event, ChatRunEntity terminalRun) {
            events.add(AguiEventJsonCodec.withTerminalMetadata(
                    AguiEventJsonCodec.encodeBootstrapEvent(event, run.getId(), null),
                    terminalRun.getStatus(),
                    terminalRun.getFinishReason()));
        }

        /** 返回已收集事件的不可变副本。 */
        private List<String> events() {
            return List.copyOf(events);
        }
    }
}
