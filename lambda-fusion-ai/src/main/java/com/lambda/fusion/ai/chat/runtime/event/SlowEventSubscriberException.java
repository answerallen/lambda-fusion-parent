package com.lambda.fusion.ai.chat.runtime.event;

import java.io.Serial;

/**
 * 事件订阅队列容量不足异常。
 *
 * @author Jin
 */
final class SlowEventSubscriberException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建异常。
     *
     * @param runId 运行标识
     */
    SlowEventSubscriberException(String runId) {
        super("Run订阅者消费过慢，已断开: " + runId);
    }
}
