package com.lambda.fusion.ai.chat.execution.event;

/** Run 事件订阅句柄。 */
public interface ExecutionEventSubscription extends AutoCloseable {

    /** 在历史回放和当前排队事件均发送完成后执行回调。 */
    void whenDrained(Runnable action);

    @Override
    void close();
}
