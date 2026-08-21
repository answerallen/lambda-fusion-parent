package com.lambda.fusion.ai.chat.runtime.snapshot;

import io.agentscope.core.util.JsonUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 对话执行快照编解码器。
 *
 * @author Jin
 */
@Slf4j
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
     * @return 执行快照；内容为空或无效时返回空快照
     */
    public static ChatRunSnapshot decode(String json) {
        if (json == null || json.isBlank()) {
            return ChatRunSnapshot.empty(null, null, 1);
        }
        try {
            ChatRunSnapshot snapshot = JsonUtils.getJsonCodec().fromJson(json, ChatRunSnapshot.class);
            return snapshot == null ? ChatRunSnapshot.empty(null, null, 1) : snapshot;
        } catch (RuntimeException invalid) {
            log.warn("对话Run快照解析失败，将按空快照恢复", invalid);
            return ChatRunSnapshot.empty(null, null, 1);
        }
    }
}
