package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.AiConstants.ChatRunStatus;
import com.lambda.fusion.ai.AiConstants.ChatRunToolStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshot;
import io.agentscope.core.agui.event.AguiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** 用官方 AG-UI 事件重建一个运行中的助手气泡。引导事件本身不写入事件缓冲。 */
public final class AguiBootstrapEncoder {

    private static final String TOOL_COMPLETE = ChatRunToolStatus.COMPLETE.getCode();

    private AguiBootstrapEncoder() {}

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

    private static void reopenReasoning(EventCollector collector, ChatRunSnapshot snapshot) {
        String messageId = valueOrDefault(snapshot.reasoningMessageId(), "reasoning-" + collector.chatRunId());
        collector.add(new AguiEvent.ReasoningStart(collector.threadId(), collector.aguiRunId(), messageId, null));
        collector.add(new AguiEvent.ReasoningMessageStart(
                collector.threadId(), collector.aguiRunId(), messageId, "reasoning"));
    }

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

    private static void appendTerminal(EventCollector collector, ChatRunEntity run, ChatRunSnapshot snapshot) {
        if (ChatRunStatus.RUNNING.name().equals(run.getStatus())
                && !snapshot.pendingTools().isEmpty()) {
            List<AguiEvent.Interrupt> interrupts = snapshot.pendingTools().stream()
                    .map(tool -> new AguiEvent.Interrupt(
                            tool.toolCallId(),
                            "human_confirmation_required",
                            "工具 '" + tool.toolCallName() + "' 需要您确认后执行",
                            tool.toolCallId(),
                            null,
                            null,
                            Map.of("toolName", tool.toolCallName())))
                    .toList();
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

    private static String valueOrDefault(String value, String fallback) {
        return StringUtils.defaultIfBlank(value, fallback);
    }

    private static final class EventCollector {

        private final ChatRunEntity run;
        private final List<String> events = new ArrayList<>();

        private EventCollector(ChatRunEntity run) {
            this.run = run;
        }

        private String threadId() {
            return run.getSessionId();
        }

        private String chatRunId() {
            return run.getId();
        }

        private String aguiRunId() {
            return run.getAguiRunId();
        }

        private void add(AguiEvent event) {
            add(event, null);
        }

        private void add(AguiEvent event, Integer phaseNo) {
            events.add(AguiEventJsonCodec.encodeBootstrapEvent(event, run.getId(), phaseNo));
        }

        private void addTerminal(AguiEvent event, ChatRunEntity terminalRun) {
            events.add(AguiEventJsonCodec.withTerminalMetadata(
                    AguiEventJsonCodec.encodeBootstrapEvent(event, run.getId(), null),
                    terminalRun.getStatus(),
                    terminalRun.getFinishReason()));
        }

        private List<String> events() {
            return List.copyOf(events);
        }
    }
}
