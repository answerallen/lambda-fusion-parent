package com.lambda.fusion.ai.chat.run;

import io.agentscope.core.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** 可持久化的 Run 展示快照。 */
@Slf4j
public record RunSnapshot(
        String runId,
        String aguiRunId,
        int phaseNo,
        String text,
        String reasoning,
        String textMessageId,
        String reasoningMessageId,
        boolean textOpen,
        boolean reasoningOpen,
        List<Tool> tools,
        List<Tool> pendingTools) {

    public RunSnapshot {
        text = text == null ? "" : text;
        reasoning = reasoning == null ? "" : reasoning;
        tools = tools == null ? List.of() : tools.stream().map(Tool::sanitized).toList();
        pendingTools = pendingTools == null
                ? List.of()
                : pendingTools.stream()
                        .map(tool -> new Tool(tool.toolCallId(), tool.toolCallName(), "", "", "asking"))
                        .toList();
    }

    public static RunSnapshot empty(String runId, String aguiRunId, int phaseNo) {
        return new RunSnapshot(runId, aguiRunId, phaseNo, "", "", null, null, false, false, List.of(), List.of());
    }

    public static RunSnapshot fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty(null, null, 1);
        }
        try {
            RunSnapshot snapshot = JsonUtils.getJsonCodec().fromJson(json, RunSnapshot.class);
            return snapshot == null ? empty(null, null, 1) : snapshot;
        } catch (RuntimeException invalid) {
            log.warn("对话Run快照解析失败，将按空快照恢复", invalid);
            return empty(null, null, 1);
        }
    }

    public record Tool(String toolCallId, String toolCallName, String args, String result, String status) {

        private static Tool sanitized(Tool tool) {
            if (tool == null) {
                return new Tool("", "", "", "", "unknown");
            }
            return new Tool(
                    safe(tool.toolCallId),
                    safe(tool.toolCallName),
                    sanitizeJson(tool.args),
                    sanitizeJson(tool.result),
                    safe(tool.status));
        }

        private static String sanitizeJson(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            try {
                Object decoded = JsonUtils.getJsonCodec().fromJson(value, Object.class);
                return JsonUtils.getJsonCodec().toJson(redact(decoded));
            } catch (RuntimeException notJson) {
                return redactText(value);
            }
        }

        private static Object redact(Object value) {
            if (value instanceof java.util.Map<?, ?> map) {
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                map.forEach((key, nested) -> {
                    String name = String.valueOf(key);
                    result.put(name, isSecret(name) ? "***" : redact(nested));
                });
                return result;
            }
            if (value instanceof List<?> list) {
                return list.stream().map(Tool::redact).toList();
            }
            return value;
        }

        private static boolean isSecret(String key) {
            String normalized =
                    key.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
            return normalized.contains("password")
                    || normalized.contains("secret")
                    || normalized.contains("token")
                    || normalized.contains("apikey")
                    || normalized.contains("authorization")
                    || normalized.contains("credential");
        }

        static String redactText(String value) {
            return value.replaceAll("(?i)(bearer\\s+)[a-z0-9._~+/=-]+", "$1***")
                    .replaceAll(
                            "(?i)(password|secret|token|api[_-]?key|authorization|credential)(\\s*[:=]\\s*)[^,;\\s]+",
                            "$1$2***");
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    /** 单个 Run 内仅由串行的 Agent Flux 回调修改。 */
    public static final class Accumulator {
        private final String runId;
        private String aguiRunId;
        private int phaseNo;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private String textMessageId;
        private String reasoningMessageId;
        private boolean textOpen;
        private boolean reasoningOpen;
        private final List<Tool> tools = new ArrayList<>();
        private List<Tool> pendingTools = List.of();

        public Accumulator(RunSnapshot snapshot) {
            this.runId = snapshot.runId();
            this.aguiRunId = snapshot.aguiRunId();
            this.phaseNo = snapshot.phaseNo();
            text.append(snapshot.text());
            reasoning.append(snapshot.reasoning());
            textMessageId = snapshot.textMessageId();
            reasoningMessageId = snapshot.reasoningMessageId();
            textOpen = snapshot.textOpen();
            reasoningOpen = snapshot.reasoningOpen();
            tools.addAll(snapshot.tools());
            pendingTools = snapshot.pendingTools();
        }

        public void beginPhase(String nextAguiRunId, int nextPhaseNo) {
            aguiRunId = nextAguiRunId;
            phaseNo = nextPhaseNo;
            pendingTools = List.of();
            closeActiveMessages();
        }

        public void appendText(String messageId, String delta) {
            closeReasoning();
            textMessageId = messageId;
            textOpen = true;
            text.append(delta);
        }

        public void appendReasoning(String messageId, String delta) {
            closeText();
            reasoningMessageId = messageId;
            reasoningOpen = true;
            reasoning.append(delta);
        }

        public void closeActiveMessages() {
            closeText();
            closeReasoning();
        }

        public void closeText() {
            textOpen = false;
        }

        public void closeReasoning() {
            reasoningOpen = false;
        }

        public void startTool(String toolCallId, String toolCallName) {
            closeActiveMessages();
            upsertTool(toolCallId, toolCallName, null, null, "running");
        }

        public void appendToolArgs(String toolCallId, String toolCallName, String delta) {
            Tool current = findTool(toolCallId);
            upsertTool(
                    toolCallId, toolCallName, (current == null ? "" : current.args()) + safe(delta), null, "running");
        }

        public void finishToolArgs(String toolCallId, String toolCallName) {
            upsertTool(toolCallId, toolCallName, null, null, "running");
        }

        public void appendToolResult(String toolCallId, String toolCallName, String delta) {
            Tool current = findTool(toolCallId);
            upsertTool(
                    toolCallId, toolCallName, null, (current == null ? "" : current.result()) + safe(delta), "running");
        }

        public void finishTool(String toolCallId, String toolCallName) {
            upsertTool(toolCallId, toolCallName, null, null, "complete");
        }

        public void updateTools(List<com.lambda.fusion.ai.chat.adapter.AguiEventMapper.ToolCallRecord> records) {
            for (com.lambda.fusion.ai.chat.adapter.AguiEventMapper.ToolCallRecord record : records) {
                upsertTool(record.toolCallId(), record.toolCallName(), record.args(), record.result(), "complete");
            }
        }

        public void awaiting(List<io.agentscope.core.message.ToolUseBlock> blocks) {
            pendingTools = blocks.stream()
                    .map(block -> new Tool(block.getId(), block.getName(), "", "", "asking"))
                    .toList();
        }

        public RunSnapshot snapshot() {
            return new RunSnapshot(
                    runId,
                    aguiRunId,
                    phaseNo,
                    text.toString(),
                    reasoning.toString(),
                    textMessageId,
                    reasoningMessageId,
                    textOpen,
                    reasoningOpen,
                    tools,
                    pendingTools);
        }

        private Tool findTool(String toolCallId) {
            return tools.stream()
                    .filter(tool -> java.util.Objects.equals(tool.toolCallId(), toolCallId))
                    .findFirst()
                    .orElse(null);
        }

        private void upsertTool(String toolCallId, String toolCallName, String args, String result, String status) {
            int index = -1;
            for (int i = 0; i < tools.size(); i++) {
                if (java.util.Objects.equals(tools.get(i).toolCallId(), toolCallId)) {
                    index = i;
                    break;
                }
            }
            Tool current = index < 0 ? null : tools.get(index);
            Tool updated = new Tool(
                    toolCallId,
                    toolCallName == null && current != null ? current.toolCallName() : safe(toolCallName),
                    args == null && current != null ? current.args() : safe(args),
                    result == null && current != null ? current.result() : safe(result),
                    status);
            if (index < 0) {
                tools.add(updated);
            } else {
                tools.set(index, updated);
            }
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
