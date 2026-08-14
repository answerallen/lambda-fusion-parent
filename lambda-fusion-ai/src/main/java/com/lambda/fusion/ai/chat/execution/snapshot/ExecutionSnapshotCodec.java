package com.lambda.fusion.ai.chat.execution.snapshot;

import io.agentscope.core.util.JsonUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Execution 快照的持久化编解码边界。 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutionSnapshotCodec {

    public static String encode(ExecutionSnapshot snapshot) {
        return JsonUtils.getJsonCodec().toJson(snapshot);
    }

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
