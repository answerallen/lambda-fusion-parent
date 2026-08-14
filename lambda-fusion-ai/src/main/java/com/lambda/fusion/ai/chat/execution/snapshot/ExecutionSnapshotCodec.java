package com.lambda.fusion.ai.chat.execution.snapshot;

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
public final class ExecutionSnapshotCodec {

    /**
     * 将执行快照编码为 JSON。
     *
     * @param snapshot 执行快照
     * @return 快照 JSON
     */
    public static String encode(ExecutionSnapshot snapshot) {
        return JsonUtils.getJsonCodec().toJson(snapshot);
    }

    /**
     * 从 JSON 解析执行快照。
     *
     * @param json 快照 JSON
     * @return 执行快照；内容为空或无效时返回空快照
     */
    public static ExecutionSnapshot decode(String json) {
        if (json == null || json.isBlank()) {
            return ExecutionSnapshot.empty(null, null, 1);
        }
        try {
            ExecutionSnapshot snapshot = JsonUtils.getJsonCodec().fromJson(json, ExecutionSnapshot.class);
            return snapshot == null ? ExecutionSnapshot.empty(null, null, 1) : snapshot;
        } catch (RuntimeException invalid) {
            log.warn("对话Run快照解析失败，将按空快照恢复", invalid);
            return ExecutionSnapshot.empty(null, null, 1);
        }
    }
}
