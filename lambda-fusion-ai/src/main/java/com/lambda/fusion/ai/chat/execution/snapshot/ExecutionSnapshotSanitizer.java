package com.lambda.fusion.ai.chat.execution.snapshot;

import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 工具调用持久化数据的归一化与敏感信息脱敏。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutionSnapshotSanitizer {

    public static List<ExecutionSnapshot.Tool> sanitizeTools(List<ExecutionSnapshot.Tool> tools) {
        return tools == null
                ? List.of()
                : tools.stream().map(ExecutionSnapshotSanitizer::sanitizeTool).toList();
    }

    public static List<ExecutionSnapshot.Tool> sanitizePendingTools(List<ExecutionSnapshot.Tool> tools) {
        return tools == null
                ? List.of()
                : tools.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(tool -> new ExecutionSnapshot.Tool(
                                safe(tool.toolCallId()), safe(tool.toolCallName()), "", "", "asking"))
                        .toList();
    }

    public static String redactText(String value) {
        return safe(value)
                .replaceAll("(?i)(bearer\\s+)[a-z0-9._~+/=-]+", "$1***")
                .replaceAll(
                        "(?i)(password|secret|token|api[_-]?key|authorization|credential)(\\s*[:=]\\s*)[^,;\\s]+",
                        "$1$2***");
    }

    private static ExecutionSnapshot.Tool sanitizeTool(ExecutionSnapshot.Tool tool) {
        if (tool == null) {
            return new ExecutionSnapshot.Tool("", "", "", "", "unknown");
        }
        return new ExecutionSnapshot.Tool(
                safe(tool.toolCallId()),
                safe(tool.toolCallName()),
                sanitizeJson(tool.args()),
                sanitizeJson(tool.result()),
                safe(tool.status()));
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
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                String name = String.valueOf(key);
                result.put(name, isSecret(name) ? "***" : redact(nested));
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ExecutionSnapshotSanitizer::redact).toList();
        }
        return value;
    }

    private static boolean isSecret(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("authorization")
                || normalized.contains("credential");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
