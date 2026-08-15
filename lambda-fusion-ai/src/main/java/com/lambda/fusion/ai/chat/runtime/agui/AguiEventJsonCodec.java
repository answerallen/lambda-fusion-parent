package com.lambda.fusion.ai.chat.runtime.agui;

import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AG-UI 事件 JSON 编解码工具。
 *
 * @author Jin
 */
public final class AguiEventJsonCodec {

    private AguiEventJsonCodec() {}

    /**
     * 单次编码运行事件并合并运行元数据。
     *
     * <p>事件自身的 runId 已与目标值一致且载荷为 JSON 对象时，直接在尾部拼接元数据，
     * 避免高频路径上「编码后再整体解析合并再编码」的往返；不满足前置条件时回退整体解析合并。
     *
     * @param event AG-UI 事件
     * @param chatRunId 对话运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param seq 事件序号
     * @return 添加元数据后的事件 JSON
     */
    public static String encodeRunEvent(AguiEvent event, String chatRunId, String aguiRunId, long seq) {
        String json = JsonUtils.getJsonCodec().toJson(event);
        if (aguiRunId.equals(event.getRunId()) && json.endsWith("}")) {
            return json.substring(0, json.length() - 1)
                    + ",\"chatRunId\":" + JsonUtils.getJsonCodec().toJson(chatRunId)
                    + ",\"seq\":" + seq + "}";
        }
        return withRunMetadata(json, chatRunId, aguiRunId, seq);
    }

    /**
     * 为事件 JSON 添加运行标识和事件序号。
     *
     * @param json 原始事件 JSON
     * @param chatRunId 对话运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param seq 事件序号
     * @return 添加元数据后的事件 JSON
     */
    @SuppressWarnings("unchecked")
    public static String withRunMetadata(String json, String chatRunId, String aguiRunId, long seq) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("seq", seq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    /**
     * 编码引导事件并添加引导元数据。
     *
     * @param event 事件字段
     * @param chatRunId 对话运行标识
     * @param aguiRunId AG-UI 运行标识
     * @param bootstrapSeq 引导事件对应的事件序号上界
     * @return 引导事件 JSON
     */
    public static String encodeBootstrapEvent(
            Map<String, Object> event, String chatRunId, String aguiRunId, long bootstrapSeq) {
        Map<String, Object> enriched = new LinkedHashMap<>(event);
        enriched.put("runId", aguiRunId);
        enriched.put("chatRunId", chatRunId);
        enriched.put("bootstrap", true);
        enriched.put("bootstrapSeq", bootstrapSeq);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }

    /**
     * 读取事件类型。
     *
     * @param json 事件 JSON
     * @return 事件类型；不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static String readEventType(String json) {
        Map<String, Object> event = JsonUtils.getJsonCodec().fromJson(json, Map.class);
        Object type = event == null ? null : event.get("type");
        return type == null ? null : String.valueOf(type);
    }

    /**
     * 为终态事件添加业务状态和结束原因。
     *
     * @param json 终态事件 JSON
     * @param status 业务状态
     * @param finishReason 结束原因
     * @return 添加终态元数据后的事件 JSON
     */
    @SuppressWarnings("unchecked")
    public static String withTerminalMetadata(String json, String status, String finishReason) {
        Map<String, Object> source = JsonUtils.getJsonCodec().fromJson(json.trim(), Map.class);
        Map<String, Object> enriched = new LinkedHashMap<>(source);
        enriched.put("chatRunStatus", status);
        enriched.put("finishReason", finishReason);
        return JsonUtils.getJsonCodec().toJson(enriched);
    }
}
