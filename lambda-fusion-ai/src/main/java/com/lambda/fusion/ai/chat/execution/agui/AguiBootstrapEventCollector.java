package com.lambda.fusion.ai.chat.execution.agui;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 收集单次 Bootstrap 编码产生的事件，并统一附加 Run 元数据。 */
final class AguiBootstrapEventCollector {

    private final String threadId;
    private final String chatRunId;
    private final String aguiRunId;
    private final long highWatermark;
    private final List<String> events = new ArrayList<>();

    AguiBootstrapEventCollector(ChatRunEntity run, long highWatermark) {
        this.threadId = run.getSessionId();
        this.chatRunId = run.getId();
        this.aguiRunId = run.getAguiRunId();
        this.highWatermark = highWatermark;
    }

    String chatRunId() {
        return chatRunId;
    }

    void add(Map<String, Object> fields) {
        Map<String, Object> event = new LinkedHashMap<>(fields);
        event.put("threadId", threadId);
        events.add(AguiEventJsonCodec.encodeBootstrapEvent(event, chatRunId, aguiRunId, highWatermark));
    }

    List<String> events() {
        return List.copyOf(events);
    }
}
