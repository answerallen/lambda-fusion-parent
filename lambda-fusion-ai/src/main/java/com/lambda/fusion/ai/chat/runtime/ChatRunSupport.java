package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotSanitizer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 对话运行共享的无状态小工具：承载 Factory/Coordinator/Instance 三方共用的纯函数，避免彼此中转或反向依赖。
 *
 * @author Jin
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ChatRunSupport {

    /**
     * 获取运行的快照事件序号。
     *
     * @param run 运行实体
     * @return 快照事件序号；未设置时返回 {@code 0}
     */
    static long sequenceFallback(ChatRunEntity run) {
        return run.getSnapshotSeq() == null ? 0L : run.getSnapshotSeq();
    }

    /**
     * 生成可持久化的错误信息。
     *
     * @param error 异常
     * @return 已清理并限制长度的错误信息
     */
    static String safeMessage(Throwable error) {
        String message =
                StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName());
        return StringUtils.left(ExecutionSnapshotSanitizer.redactText(message), 1000);
    }
}
