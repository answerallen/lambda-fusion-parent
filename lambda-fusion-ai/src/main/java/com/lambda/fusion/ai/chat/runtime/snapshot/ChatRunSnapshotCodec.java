package com.lambda.fusion.ai.chat.runtime.snapshot;

import io.agentscope.core.util.JsonUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 对话执行快照编解码器。
 *
 * @author Jin
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChatRunSnapshotCodec {

    /**
     * 将执行快照编码为 JSON。
     *
     * @param snapshot 执行快照
     * @return 快照 JSON
     */
    public static String encode(ChatRunSnapshot snapshot) {
        return JsonUtils.getJsonCodec().toJson(snapshot);
    }

    /**
     * 从 JSON 解析执行快照。
     *
     * @param json 快照 JSON
     * @return 执行快照；内容为空时返回空快照
     * @throws IllegalStateException 非空内容无法解析时抛出，避免用空快照覆盖已持久化的恢复事实
     */
    public static ChatRunSnapshot decode(String json) {
        if (json == null || json.isBlank()) {
            return ChatRunSnapshot.empty(null, null, 1);
        }
        try {
            ChatRunSnapshot snapshot = JsonUtils.getJsonCodec().fromJson(json, ChatRunSnapshot.class);
            if (snapshot == null) {
                throw new IllegalArgumentException("快照内容为 null");
            }
            return snapshot;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("对话Run快照解析失败", invalid);
        }
    }
}
