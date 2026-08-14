package com.lambda.fusion.ai.chat.execution.snapshot;

import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 执行快照数据清理工具。
 *
 * <p>负责工具调用数据归一化，并在持久化前屏蔽常见凭据字段。
 *
 * @author Jin
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutionSnapshotSanitizer {

    /**
     * 清理工具调用快照。
     *
     * @param tools 工具调用快照
     * @return 清理后的工具调用快照
     */
    public static List<ExecutionSnapshot.Tool> sanitizeTools(List<ExecutionSnapshot.Tool> tools) {
        return tools == null
                ? List.of()
                : tools.stream().map(ExecutionSnapshotSanitizer::sanitizeTool).toList();
    }

    /**
     * 清理待确认工具调用快照。
     *
     * @param tools 待确认工具调用
     * @return 不包含参数和结果的待确认工具调用快照
     */
    public static List<ExecutionSnapshot.Tool> sanitizePendingTools(List<ExecutionSnapshot.Tool> tools) {
        return tools == null
                ? List.of()
                : tools.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(tool -> new ExecutionSnapshot.Tool(
                                safe(tool.toolCallId()), safe(tool.toolCallName()), "", "", "asking"))
                        .toList();
    }

    /**
     * 屏蔽文本中的常见凭据信息。
     *
     * @param value 原始文本
     * @return 清理后的文本
     */
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
