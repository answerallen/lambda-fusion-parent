package com.lambda.fusion.ai.chat.runtime.agui;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** AG-UI 官方事件编码与最小业务元数据合并工具。 */
public final class AguiEventJsonCodec {

    private static final AguiEventEncoder ENCODER = new AguiEventEncoder();

    private AguiEventJsonCodec() {}

    /** 编码实时事件。进程内游标不会写入协议载荷。 */
    public static String encodeRunEvent(AguiEvent event, String chatRunId, String aguiRunId) {
        String json = ENCODER.encodeToJson(event);
        if (Objects.equals(aguiRunId, event.getRunId()) && json.endsWith("}")) {
            return json.substring(0, json.length() - 1) + ",\"chatRunId\":"
                    + JsonUtils.getJsonCodec().toJson(chatRunId) + "}";
        }
        return withRunMetadata(json, chatRunId, aguiRunId);
    }

    /** 为已经编码的事件补充业务 Run 与当前 AG-UI phase 标识。 */
    @SuppressWarnings("unchecked")
    public static String withRunMetadata(String json, String chatRunId, String aguiRunId) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    /** 编码浏览器恢复引导事件；仅 RUN_STARTED 需要附带业务 phase。 */
    @SuppressWarnings("unchecked")
    public static String encodeBootstrapEvent(AguiEvent event, String chatRunId, Integer phaseNo) {
        Map<String, Object> source = JsonUtils.getJsonCodec()
                .fromJson(encodeRunEvent(event, chatRunId, event.getRunId()).trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        if (phaseNo != null) {
            enriched.put("phaseNo", phaseNo);
        }
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    @SuppressWarnings("unchecked")
    public static String readEventType(String json) {
        Map<String, Object> event = JsonUtils.getJsonCodec().fromJson(json, Map.class);
        Object type = event == null ? null : event.get("type");
        return type == null ? null : String.valueOf(type);
    }

    /** 为终态事件补充业务状态和结束原因。 */
    @SuppressWarnings("unchecked")
    public static String withTerminalMetadata(String json, String status, String finishReason) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("chatRunStatus", status);
        enriched.put("finishReason", finishReason);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }
}
