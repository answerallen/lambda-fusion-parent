package com.lambda.fusion.ai.chat.runtime.event;

import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对话事件控制指令：承载不占业务序号的带外控制事件（§6.6）。控制事件不进入事件 Stream、
 * 不占用业务 seq，仅作为 SSE 带内信号触发前端重置。
 *
 * @author Jin
 */
public final class ChatRunControlEvent {

    /** 强制重同步控制事件类型。 */
    public static final String TYPE_RESYNC_REQUIRED = "RESYNC_REQUIRED";

    private ChatRunControlEvent() {}

    /**
     * 构造 RESYNC_REQUIRED 控制事件：通知前端删除临时气泡并重新 bootstrap / 刷新历史。
     * 覆盖两类场景——缺边界事件，以及 INSTANCE_LOST 时页面持有未检查点尾部增量必须 reset。
     *
     * @param chatRunId 对话运行标识
     * @return 控制事件（seq 固定为 0，不占业务序号）
     */
    public static ChatRunEvent resyncRequired(String chatRunId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", TYPE_RESYNC_REQUIRED);
        payload.put("chatRunId", chatRunId);
        String json = AguiEventJsonCodec.withRunMetadata(
                io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(payload), chatRunId, "", 0L);
        return new ChatRunEvent(0L, chatRunId + ":resync", TYPE_RESYNC_REQUIRED, json);
    }
}
