package com.lambda.fusion.ai.chat.execution.agui;

import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/** AG-UI 事件 JSON 的解析、扩展与编码边界。 */
public final class AguiEventJsonCodec {

    private AguiEventJsonCodec() {}

    @SuppressWarnings("unchecked")
    public static String withRunMetadata(String json, String chatRunId, String aguiRunId, long seq) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("seq", seq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    public static String encodeBootstrapEvent(
            Map<String, Object> event, String chatRunId, String aguiRunId, long bootstrapSeq) {
        Map<String, Object> enriched = new LinkedHashMap<>(event);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("bootstrap", true);
        enriched.put("bootstrapSeq", bootstrapSeq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    @SuppressWarnings("unchecked")
    public static String readEventType(String json) {
        Map<String, Object> event = JsonUtils.getJsonCodec().fromJson(json, Map.class);
        Object type = event == null ? null : event.get("type");
        return type == null ? null : String.valueOf(type);
    }

    @SuppressWarnings("unchecked")
    public static String withTerminalMetadata(String json, String status, String finishReason) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("chatRunStatus", status);
        enriched.put("finishReason", finishReason);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }
}
