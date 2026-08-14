package com.lambda.fusion.ai.chat.run;

import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将规范快照编码成不会写回正式事件存储的 AG-UI 引导序列。 */
public final class RunBootstrapEncoder {

    private RunBootstrapEncoder() {}

    public static List<String> encode(ChatRunEntity run, RunSnapshot snapshot, long highWatermark) {
        String threadId = run.getSessionId();
        String runId = run.getId();
        String aguiRunId = run.getAguiRunId();
        List<String> result = new ArrayList<>();
        result.add(event(
                runId,
                aguiRunId,
                highWatermark,
                fields("type", "RUN_STARTED", "threadId", threadId, "phaseNo", run.getPhaseNo())));

        if (!snapshot.reasoning().isEmpty()) {
            String messageId = valueOrDefault(snapshot.reasoningMessageId(), "reasoning-" + runId);
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields("type", "REASONING_START", "threadId", threadId, "messageId", messageId)));
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "REASONING_MESSAGE_START",
                            "threadId",
                            threadId,
                            "messageId",
                            messageId,
                            "role",
                            "reasoning")));
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "REASONING_MESSAGE_CONTENT",
                            "threadId",
                            threadId,
                            "messageId",
                            messageId,
                            "delta",
                            snapshot.reasoning())));
            if (!snapshot.reasoningOpen()) {
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields("type", "REASONING_MESSAGE_END", "threadId", threadId, "messageId", messageId)));
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields("type", "REASONING_END", "threadId", threadId, "messageId", messageId)));
            }
        }

        for (RunSnapshot.Tool tool : snapshot.tools()) {
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "TOOL_CALL_START",
                            "threadId",
                            threadId,
                            "toolCallId",
                            tool.toolCallId(),
                            "toolCallName",
                            tool.toolCallName())));
            if (!tool.args().isEmpty()) {
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields(
                                "type",
                                "TOOL_CALL_ARGS",
                                "threadId",
                                threadId,
                                "toolCallId",
                                tool.toolCallId(),
                                "delta",
                                tool.args())));
            }
            if ("complete".equals(tool.status())) {
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields("type", "TOOL_CALL_END", "threadId", threadId, "toolCallId", tool.toolCallId())));
            }
            if ("complete".equals(tool.status()) && !tool.result().isEmpty()) {
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields(
                                "type",
                                "TOOL_CALL_RESULT",
                                "threadId",
                                threadId,
                                "toolCallId",
                                tool.toolCallId(),
                                "content",
                                tool.result(),
                                "role",
                                "tool",
                                "messageId",
                                "tool-result-" + tool.toolCallId())));
            }
        }

        if (!snapshot.text().isEmpty()) {
            String messageId = valueOrDefault(snapshot.textMessageId(), "message-" + runId);
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "TEXT_MESSAGE_START",
                            "threadId",
                            threadId,
                            "messageId",
                            messageId,
                            "role",
                            "assistant")));
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "TEXT_MESSAGE_CONTENT",
                            "threadId",
                            threadId,
                            "messageId",
                            messageId,
                            "delta",
                            snapshot.text())));
            if (!snapshot.textOpen()) {
                result.add(event(
                        runId,
                        aguiRunId,
                        highWatermark,
                        fields("type", "TEXT_MESSAGE_END", "threadId", threadId, "messageId", messageId)));
            }
        }

        if (ChatRunStatus.AWAITING_CONFIRM.name().equals(run.getStatus())) {
            List<Map<String, Object>> interrupts = snapshot.pendingTools().stream()
                    .map(tool -> fields(
                            "id", tool.toolCallId(),
                            "value", "human_confirmation_required",
                            "message", "工具 '" + tool.toolCallName() + "' 需要您确认后执行",
                            "toolCallId", tool.toolCallId(),
                            "metadata", fields("toolName", tool.toolCallName())))
                    .toList();
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "RUN_FINISHED",
                            "threadId",
                            threadId,
                            "outcome",
                            fields("type", "interrupt", "interrupts", interrupts))));
        } else if (ChatRunStatus.COMPLETED.name().equals(run.getStatus())
                || ChatRunStatus.STOPPED.name().equals(run.getStatus())) {
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "RUN_FINISHED",
                            "threadId",
                            threadId,
                            "chatRunStatus",
                            run.getStatus(),
                            "finishReason",
                            run.getFinishReason(),
                            "outcome",
                            fields("type", "success"))));
        } else if (ChatRunStatus.FAILED.name().equals(run.getStatus())) {
            result.add(event(
                    runId,
                    aguiRunId,
                    highWatermark,
                    fields(
                            "type",
                            "RUN_ERROR",
                            "threadId",
                            threadId,
                            "message",
                            run.getErrorMessage() == null ? "对话运行失败" : run.getErrorMessage(),
                            "code",
                            run.getErrorCode())));
        }

        return result;
    }

    private static String event(String runId, String aguiRunId, long seq, Map<String, Object> fields) {
        return AguiJson.bootstrapEvent(fields, runId, aguiRunId, seq);
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
