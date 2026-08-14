package com.lambda.fusion.ai.chat.execution.event;

/**
 * 对话执行事件订阅。
 *
 * @author Jin
 */
public interface ExecutionEventSubscription extends AutoCloseable {

    /**
     * 注册队列排空回调。
     *
     * @param action 历史事件和实时队列发送完成后执行的回调
     */
    void whenDrained(Runnable action);

    /** 关闭订阅。 */
    @Override
    void close();
}
