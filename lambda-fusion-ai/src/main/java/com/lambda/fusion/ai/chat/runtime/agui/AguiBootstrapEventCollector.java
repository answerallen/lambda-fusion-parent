package com.lambda.fusion.ai.chat.runtime.agui;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AG-UI 引导事件收集器。
 *
 * <p>收集单次编码产生的事件，并附加运行标识和事件序号上界。
 *
 * @author Jin
 */
final class AguiBootstrapEventCollector {

    private final String threadId;
    private final String chatRunId;
    private final String aguiRunId;
    private final long highWatermark;
    private final List<String> events = new ArrayList<>();

    /**
     * 创建引导事件收集器。
     *
     * @param run 运行实体
     * @param highWatermark 当前事件序号上界
     */
    AguiBootstrapEventCollector(ChatRunEntity run, long highWatermark) {
        this.threadId = run.getSessionId();
        this.chatRunId = run.getId();
        this.aguiRunId = run.getAguiRunId();
        this.highWatermark = highWatermark;
    }

    /**
     * 获取对话运行标识。
     *
     * @return 对话运行标识
     */
    String chatRunId() {
        return chatRunId;
    }

    /**
     * 添加并编码一个引导事件。
     *
     * @param fields 事件字段
     */
    void add(Map<String, Object> fields) {
        Map<String, Object> event = new LinkedHashMap<>(fields);
        event.put("threadId", threadId);
        events.add(AguiEventJsonCodec.encodeBootstrapEvent(event, chatRunId, aguiRunId, highWatermark));
    }

    /**
     * 获取已编码的事件。
     *
     * @return 不可变事件列表
     */
    List<String> events() {
        return List.copyOf(events);
    }
}
