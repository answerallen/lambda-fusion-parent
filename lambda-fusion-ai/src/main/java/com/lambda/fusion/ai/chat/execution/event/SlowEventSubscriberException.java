package com.lambda.fusion.ai.chat.execution.event;

import java.io.Serial;

/** 事件订阅者无法跟上生产速度、导致有界发送队列溢出。 */
final class SlowEventSubscriberException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    SlowEventSubscriberException(String runId) {
        super("Run订阅者消费过慢，已断开: " + runId);
    }
}
