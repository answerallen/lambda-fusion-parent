package com.lambda.fusion.ai.chat.run;

import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/** 对 AG-UI JSON 增加协议允许的顶层扩展字段。 */
public final class AguiJson {

    private AguiJson() {}

    @SuppressWarnings("unchecked")
    static String withRunMetadata(String json, String chatRunId, String aguiRunId, long seq) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("seq", seq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    static String bootstrapEvent(Map<String, Object> event, String chatRunId, String aguiRunId, long bootstrapSeq) {
        Map<String, Object> enriched = new LinkedHashMap<>(event);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("bootstrap", true);
        enriched.put("bootstrapSeq", bootstrapSeq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    @SuppressWarnings("unchecked")
    public static String eventType(String json) {
        Map<String, Object> event = JsonUtils.getJsonCodec().fromJson(json, Map.class);
        Object type = event == null ? null : event.get("type");
        return type == null ? null : String.valueOf(type);
    }

    @SuppressWarnings("unchecked")
    static String withTerminalMetadata(String json, String status, String finishReason) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("chatRunStatus", status);
        enriched.put("finishReason", finishReason);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }
}
