package com.lambda.fusion.ai.chat.execution.agui;

import com.lambda.fusion.ai.chat.execution.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将规范快照编码成不会写回正式事件存储的 AG-UI 引导序列。 */
public final class AguiBootstrapEncoder {

    private static final String TOOL_COMPLETE = "complete";

    private AguiBootstrapEncoder() {}

    public static List<String> encode(ChatRunEntity run, ExecutionSnapshot snapshot, long highWatermark) {
        AguiBootstrapEventCollector collector = new AguiBootstrapEventCollector(run, highWatermark);
        collector.add(fields("type", "RUN_STARTED", "phaseNo", run.getPhaseNo()));
        appendReasoning(collector, snapshot);
        appendTools(collector, snapshot);
        appendText(collector, snapshot);
        appendTerminal(collector, run, snapshot);
        return collector.events();
    }

    private static void appendReasoning(AguiBootstrapEventCollector collector, ExecutionSnapshot snapshot) {
        if (snapshot.reasoning().isEmpty()) {
            return;
        }
        String messageId = valueOrDefault(snapshot.reasoningMessageId(), "reasoning-" + collector.chatRunId());
        collector.add(fields("type", "REASONING_START", "messageId", messageId));
        collector.add(fields("type", "REASONING_MESSAGE_START", "messageId", messageId, "role", "reasoning"));
        collector.add(
                fields("type", "REASONING_MESSAGE_CONTENT", "messageId", messageId, "delta", snapshot.reasoning()));
        if (!snapshot.reasoningOpen()) {
            collector.add(fields("type", "REASONING_MESSAGE_END", "messageId", messageId));
            collector.add(fields("type", "REASONING_END", "messageId", messageId));
        }
    }

    private static void appendTools(AguiBootstrapEventCollector collector, ExecutionSnapshot snapshot) {
        for (ExecutionSnapshot.Tool tool : snapshot.tools()) {
            collector.add(fields(
                    "type", "TOOL_CALL_START", "toolCallId", tool.toolCallId(), "toolCallName", tool.toolCallName()));
            if (!tool.args().isEmpty()) {
                collector.add(fields("type", "TOOL_CALL_ARGS", "toolCallId", tool.toolCallId(), "delta", tool.args()));
            }
            if (!TOOL_COMPLETE.equals(tool.status())) {
                continue;
            }
            collector.add(fields("type", "TOOL_CALL_END", "toolCallId", tool.toolCallId()));
            if (!tool.result().isEmpty()) {
                collector.add(fields(
                        "type",
                        "TOOL_CALL_RESULT",
                        "toolCallId",
                        tool.toolCallId(),
                        "content",
                        tool.result(),
                        "role",
                        "tool",
                        "messageId",
                        "tool-result-" + tool.toolCallId()));
            }
        }
    }

    private static void appendText(AguiBootstrapEventCollector collector, ExecutionSnapshot snapshot) {
        if (snapshot.text().isEmpty()) {
            return;
        }
        String messageId = valueOrDefault(snapshot.textMessageId(), "message-" + collector.chatRunId());
        collector.add(fields("type", "TEXT_MESSAGE_START", "messageId", messageId, "role", "assistant"));
        collector.add(fields("type", "TEXT_MESSAGE_CONTENT", "messageId", messageId, "delta", snapshot.text()));
        if (!snapshot.textOpen()) {
            collector.add(fields("type", "TEXT_MESSAGE_END", "messageId", messageId));
        }
    }

    private static void appendTerminal(
            AguiBootstrapEventCollector collector, ChatRunEntity run, ExecutionSnapshot snapshot) {
        if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            List<Map<String, Object>> interrupts = snapshot.pendingTools().stream()
                    .map(tool -> fields(
                            "id", tool.toolCallId(),
                            "value", "human_confirmation_required",
                            "message", "工具 '" + tool.toolCallName() + "' 需要您确认后执行",
                            "toolCallId", tool.toolCallId(),
                            "metadata", fields("toolName", tool.toolCallName())))
                    .toList();
            collector.add(
                    fields("type", "RUN_FINISHED", "outcome", fields("type", "interrupt", "interrupts", interrupts)));
            return;
        }
        if (ChatRunStatus.COMPLETED.name().equals(run.getStatus())
                || ChatRunStatus.STOPPED.name().equals(run.getStatus())) {
            collector.add(fields(
                    "type",
                    "RUN_FINISHED",
                    "chatRunStatus",
                    run.getStatus(),
                    "finishReason",
                    run.getFinishReason(),
                    "outcome",
                    fields("type", "success")));
            return;
        }
        if (ChatRunStatus.FAILED.name().equals(run.getStatus())) {
            collector.add(fields(
                    "type",
                    "RUN_ERROR",
                    "message",
                    run.getErrorMessage() == null ? "对话运行失败" : run.getErrorMessage(),
                    "code",
                    run.getErrorCode()));
        }
    }

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                values.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return values;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
